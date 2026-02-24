# 🏗 Архитектура Medical License Bot

> Подробное описание архитектуры, компонентов и потоков данных.

---

## Общая схема

```
                    ┌──────────────────────────────────────────────────────┐
                    │              Spring Boot Application                  │
                    │                                                      │
 ┌──────────┐      │  ┌────────────────┐    ┌──────────────────────────┐  │      ┌──────────┐
 │ Telegram  │─────▶│  │ TelegramBot    │───▶│      RagService          │  │─────▶│ OpenRouter│
 │ Клиент    │◀─────│  │ Service        │    │                          │  │◀─────│ (LLM +    │
 └──────────┘      │  └────────────────┘    │  1. Embed вопрос          │  │      │ Embedding)│
                    │                        │  2. Поиск в Qdrant        │  │      └──────────┘
 ┌──────────┐      │  ┌────────────────┐    │  3. Формировать промпт     │  │
 │ REST     │─────▶│  │ AskController  │───▶│  4. Вызвать LLM           │  │      ┌──────────┐
 │ Клиент   │◀─────│  │                │    │  5. Вернуть ответ          │  │─────▶│  Qdrant  │
 └──────────┘      │  └────────────────┘    └──────────────────────────┘  │◀─────│ (вектор. │
                    │                                                      │      │   БД)    │
                    │  ┌────────────────┐    ┌──────────────────────────┐  │      └──────────┘
                    │  │ SecurityConfig │    │ DocumentIngestionService │  │
                    │  │ (Basic Auth)   │    │ (индексация документов)   │  │      ┌──────────┐
                    │  └────────────────┘    └──────────────────────────┘  │◀─────│ Файловая │
                    │                                                      │      │ система  │
                    │  ┌────────────────┐    ┌──────────────────────────┐  │      │ (docs/)  │
                    │  │ RateLimitFilter│    │ QdrantCollectionManager  │  │      └──────────┘
                    │  │ (per-IP)       │    │ (жизненный цикл коллекции)│  │
                    │  └────────────────┘    └──────────────────────────┘  │
                    └──────────────────────────────────────────────────────┘
```

---

## Компоненты

### 1. Точки входа (Entry Points)

#### TelegramBotService
- **Тип:** Long Polling бот
- **Назначение:** Принимает сообщения от пользователей Telegram
- **Особенности:**
  - Асинхронная обработка через `TaskExecutor` (4-8 потоков)
  - Rate limiting: 10 сообщений/мин на пользователя
  - Белый список администраторов для `/reindex`
  - Автоматическое разбиение длинных ответов (>4096 символов)

#### AskController (REST API)
- **Тип:** Spring `@RestController`
- **Эндпоинты:**
  - `POST /ask` — публичный, обрабатывает вопросы
  - `POST /reindex` — защищённый (ADMIN), переиндексация
- **Валидация:** Jakarta Validation (`@NotBlank`, `@Size(max=4000)`)

### 2. RAG-пайплайн

#### RagService
- **Назначение:** Основной сервис обработки вопросов
- **Алгоритм:**
  1. Генерация embedding вопроса через `EmbeddingModel`
  2. Семантический поиск в Qdrant (`topK=6`, `similarityThreshold=0.75`)
  3. Фильтрация результатов с null-сегментами
  4. Формирование промпта с контекстом из документов
  5. Вызов чат-модели через `ChatLanguageModel`
  6. Возврат ответа со списком источников
- **Системный промпт:** Инструктирует LLM отвечать строго по документам, на русском языке

#### DocumentParser
- **Назначение:** Парсинг DOC/DOCX файлов
- **Библиотека:** Apache POI 5.2.5
- **Поддержка:**
  - DOCX: параграфы + таблицы (ячейки через ` | `)
  - DOC: полный текст через `WordExtractor`
- **Метаданные:** Сохраняет имя файла в `source`

#### DocumentIngestionService
- **Назначение:** Пайплайн индексации документов
- **Чанкинг:** 1000 токенов, перекрытие 200 токенов (`OpenAiTokenizer`)
- **`ingest()`:** Добавляет к существующей коллекции
- **`reindex()`:** Полная переиндексация (parse → recreate → ingest)
  - `ReentrantLock.tryLock()` — защита от конкурентных вызовов
  - Документы парсятся ДО удаления коллекции (parse-first)

#### QdrantCollectionManager
- **Назначение:** Управление жизненным циклом коллекции Qdrant
- **Операции:**
  - `ensureCollectionExists()` — при старте (fail-fast)
  - `recreateCollection()` — при переиндексации
- **Метрика:** Cosine distance, размерность из конфигурации

### 3. Безопасность и инфраструктура

#### SecurityConfig
- **Тип:** Spring Security конфигурация
- **Стратегия:** STATELESS (без сессий, без CSRF)
- **Аутентификация:** Basic Auth → `InMemoryUserDetailsManager`
- **Хэширование:** BCrypt

#### RateLimitFilter
- **Тип:** Servlet Filter (`@Order(1)`)
- **Алгоритм:** Fixed-window Token Bucket (per-IP)
- **Очистка:** `@Scheduled` каждые 5 минут (удаление неактивных бакетов)
- **Исключения:** `/actuator/**` не ограничивается

#### GlobalExceptionHandler
- **Тип:** `@RestControllerAdvice`
- **Обработчики:** 400 (валидация, JSON), 405, 500
- **Безопасность:** Не раскрывает стектрейс в 500-ответах

---

## Потоки данных

### Поток ответа на вопрос

```
1. Пользователь → [Telegram / REST]
2. → Валидация запроса
3. → RagService.ask(question)
4.   → EmbeddingModel.embed(question)       → OpenRouter API
5.   → QdrantEmbeddingStore.findRelevant()  → Qdrant gRPC
6.   → Фильтрация (null, threshold)
7.   → buildContext() + buildPrompt()
8.   → ChatLanguageModel.generate()          → OpenRouter API
9.   → AskResponse(answer, sources)
10. ← Пользователю
```

### Поток переиндексации

```
1. Администратор → [Telegram /reindex / REST POST /reindex]
2. → Проверка прав (admin whitelist / Basic Auth)
3. → ReentrantLock.tryLock()
4.   → DocumentParser.parseAll(docsPath)     → Файловая система
5.   → [Если 0 документов — отмена, данные сохранены]
6.   → QdrantCollectionManager.recreateCollection()  → Qdrant gRPC
7.   → EmbeddingStoreIngestor.ingest()       → OpenRouter + Qdrant
8. ← Результат (количество документов)
```

---

## Модель потоков (Thread Model)

| Компонент              | Потоки                                    | Описание                                      |
|------------------------|-------------------------------------------|-----------------------------------------------|
| Telegram polling       | 1 (библиотечный)                          | Получает обновления от Telegram API            |
| Обработка сообщений    | 4-8 (`tg-handler-*`)                      | Выделенный пул для обработки Telegram-сообщений|
| HTTP-запросы           | Tomcat thread pool (200)                  | Стандартный пул Spring Boot                    |
| Rate limit cleanup     | 1 (`@Scheduled`)                          | Очистка устаревших бакетов каждые 5 мин        |
| Reindex lock           | `ReentrantLock` (1 одновременно)          | Только один reindex в любой момент времени     |

---

## Конфигурационная модель

```
application.yml
├── server.*              → Порт, graceful shutdown
├── spring.lifecycle.*    → Таймаут shutdown
├── api.admin.*           → Basic Auth credentials
├── rate-limit.*          → Rate limiting параметры
├── openrouter.*          → LLM/Embedding конфигурация  → AppProperties.OpenRouterProperties
├── qdrant.*              → Векторная БД                → AppProperties.QdrantProperties
├── rag.*                 → RAG параметры (topK, threshold)
├── telegram.*            → Telegram бот
├── app.*                 → Общие (docs-path, tokenizer)
├── management.*          → Actuator / Prometheus
└── logging.*             → Уровни логирования
```

---

## Зависимости между компонентами

```
MedicalLicenseBotApplication
 ├── SecurityConfig
 ├── RateLimitFilter → TokenBucket
 ├── LlmConfig → ChatLanguageModel, EmbeddingModel
 ├── QdrantConfig → QdrantCollectionManager → QdrantEmbeddingStore
 ├── TelegramBotConfig → TelegramBotService
 │                        ├── RagService
 │                        │   ├── ChatLanguageModel
 │                        │   ├── EmbeddingModel
 │                        │   └── QdrantEmbeddingStore
 │                        └── DocumentIngestionService
 │                            ├── DocumentParser
 │                            ├── EmbeddingModel
 │                            ├── QdrantEmbeddingStore
 │                            └── QdrantCollectionManager
 └── AskController
      ├── RagService
      └── DocumentIngestionService
```
