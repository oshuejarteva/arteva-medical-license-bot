# 🏥 Medical License Bot

> Telegram-бот и REST API для ответов на вопросы по медицинскому лицензированию  
> на основе технологии **RAG** (Retrieval-Augmented Generation).

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.35.0-blue)](https://github.com/langchain4j/langchain4j)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 📋 Содержание

- [Обзор](#-обзор)
- [Архитектура](#-архитектура)
- [Технологический стек](#-технологический-стек)
- [Быстрый старт](#-быстрый-старт)
- [Конфигурация](#-конфигурация)
- [REST API](#-rest-api)
- [Telegram-бот](#-telegram-бот)
- [Безопасность](#-безопасность)
- [Мониторинг и метрики](#-мониторинг-и-метрики)
- [Документация](#-документация)
- [Разработка](#-разработка)

---

## 🔍 Обзор

**Medical License Bot** — это интеллектуальный помощник, который отвечает на вопросы
пользователей по медицинскому лицензированию, опираясь **исключительно** на загруженные
документы (`.doc`/`.docx`).

### Как это работает

```
Пользователь → Вопрос → Embedding → Семантический поиск (Qdrant) → Релевантные фрагменты → LLM (OpenRouter) → Ответ
```

1. Пользователь задаёт вопрос (через Telegram или REST API)
2. Вопрос преобразуется в вектор (embedding)
3. В Qdrant ищутся наиболее похожие фрагменты документов
4. Найденные фрагменты вместе с вопросом отправляются в LLM
5. LLM формирует ответ **только** на основе найденного контекста
6. Ответ возвращается пользователю с указанием источников

---

## 🏗 Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                    Medical License Bot                    │
├─────────────────┬───────────────────────────────────────┤
│   Telegram Bot  │           REST API                     │
│  (Long Polling) │  POST /ask     POST /reindex           │
├─────────────────┴───────────────────────────────────────┤
│                    RAG Pipeline                           │
│  ┌──────────┐  ┌───────────┐  ┌─────────┐  ┌─────────┐ │
│  │ Document │→ │ Embedding │→ │  Qdrant │→ │   LLM   │ │
│  │  Parser  │  │   Model   │  │(вектор.)│  │(OpenAI) │ │
│  └──────────┘  └───────────┘  └─────────┘  └─────────┘ │
├─────────────────────────────────────────────────────────┤
│  Security │ Rate Limiting │ Monitoring │ Graceful Stop   │
└─────────────────────────────────────────────────────────┘
```

### Структура пакетов

```
com.arteva.medbot
├── config/               — Конфигурация (Security, LLM, Qdrant, Telegram, Rate Limit)
├── controller/           — REST-контроллеры (AskController, GlobalExceptionHandler)
├── model/                — DTO (AskRequest, AskResponse)
├── rag/                  — RAG-пайплайн (RagService, DocumentParser, Ingestion, Qdrant)
├── service/              — Telegram-бот (TelegramBotService)
└── util/                 — Утилиты (TokenBucket)
```

---

## 🛠 Технологический стек

| Компонент             | Технология                          | Версия   |
|-----------------------|-------------------------------------|----------|
| Язык                  | Java                                | 21       |
| Фреймворк             | Spring Boot                         | 3.3.5    |
| RAG Framework         | LangChain4j                         | 0.35.0   |
| LLM / Embeddings      | OpenRouter (OpenAI-совместимый API) | —        |
| Векторная БД          | Qdrant                              | 1.12.1   |
| Парсинг документов    | Apache POI                          | 5.2.5    |
| Telegram              | TelegramBots                        | 6.9.7.1  |
| Безопасность          | Spring Security (Basic Auth)        | —        |
| Метрики               | Micrometer + Prometheus             | —        |
| Логирование           | Logback + Logstash Encoder (JSON)   | 7.4      |
| Контейнеризация       | Docker (multi-stage build)          | —        |

---

## 🚀 Быстрый старт

### Предварительные требования

- Docker и Docker Compose
- API-ключ [OpenRouter](https://openrouter.ai/keys)
- Токен Telegram-бота от [@BotFather](https://t.me/BotFather)

### Шаги запуска

**1. Клонируйте репозиторий:**

```bash
git clone https://github.com/arteva/medical-license-bot.git
cd medical-license-bot
```

**2. Создайте `.env` файл:**

```bash
cp .env.example .env
```

**3. Заполните обязательные переменные в `.env`:**

```env
OPENROUTER_API_KEY=sk-or-v1-ваш-ключ
TELEGRAM_TOKEN=123456789:ваш-токен
TELEGRAM_USERNAME=ИмяВашегоБота
API_ADMIN_PASSWORD=надёжный-пароль
```

**4. Положите документы в папку `docs/`:**

```bash
cp /путь/к/документам/*.docx ./docs/
```

**5. Запустите:**

```bash
docker compose up -d
```

**6. Проверьте работоспособность:**

```bash
# Healthcheck
curl http://localhost:8080/actuator/health

# Переиндексация документов
curl -X POST http://localhost:8080/reindex \
  -u admin:ваш-пароль

# Задать вопрос
curl -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Какие требования к лицензии?"}'
```

---

## ⚙ Конфигурация

Конфигурация задаётся через переменные окружения. Полный список с описаниями — в [.env.example](.env.example).

| Переменная                   | Описание                                      | По умолчанию                          | Обязательна |
|------------------------------|-----------------------------------------------|---------------------------------------|-------------|
| `OPENROUTER_API_KEY`         | API-ключ OpenRouter                            | —                                     | ✅          |
| `OPENROUTER_MODEL`           | Модель генерации ответов                       | `google/gemini-2.0-flash-001`         | ❌          |
| `OPENROUTER_EMBEDDING_MODEL` | Модель эмбеддингов                             | `openai/text-embedding-3-small`       | ❌          |
| `TELEGRAM_TOKEN`             | Токен Telegram-бота                            | —                                     | ✅          |
| `TELEGRAM_USERNAME`          | Username бота                                  | `MedLicenseBot`                       | ❌          |
| `TELEGRAM_ADMIN_CHAT_IDS`    | ID администраторов (через запятую)             | —                                     | ❌          |
| `API_ADMIN_USERNAME`         | Логин для Basic Auth                           | `admin`                               | ❌          |
| `API_ADMIN_PASSWORD`         | Пароль для Basic Auth                          | —                                     | ✅          |
| `RATE_LIMIT_RPM`             | Макс. запросов/мин с одного IP                 | `30`                                  | ❌          |
| `QDRANT_HOST`                | Хост Qdrant                                    | `localhost` (Docker: `qdrant`)        | ❌          |
| `QDRANT_PORT`                | gRPC-порт Qdrant                               | `6334`                                | ❌          |
| `RAG_TOP_K`                  | Кол-во извлекаемых фрагментов                  | `6`                                   | ❌          |
| `RAG_SIMILARITY_THRESHOLD`   | Минимальный порог сходства                     | `0.75`                                | ❌          |

Подробные комментарии — в [application.yml](src/main/resources/application.yml).

---

## 📡 REST API

### POST /ask — Задать вопрос (публичный)

**Запрос:**
```json
{
  "question": "Какие документы нужны для получения медицинской лицензии?"
}
```

**Ответ (200 OK):**
```json
{
  "answer": "Для получения медицинской лицензии необходимы следующие документы: ...",
  "sources": ["license-requirements.docx", "order-123.doc"]
}
```

**Ошибки:**
| Код | Описание                             |
|-----|--------------------------------------|
| 400 | Пустой вопрос или > 4000 символов    |
| 429 | Превышен лимит запросов              |

### POST /reindex — Переиндексация (требует ADMIN)

```bash
curl -X POST http://localhost:8080/reindex -u admin:пароль
```

**Ответ (200 OK):**
```json
{
  "status": "completed",
  "documentsIndexed": 5
}
```

**Ошибки:**
| Код | Описание                                |
|-----|-----------------------------------------|
| 401 | Не авторизован                           |
| 403 | Недостаточно прав                        |
| 409 | Переиндексация уже выполняется           |

---

## 🤖 Telegram-бот

### Команды

| Команда        | Описание                                | Доступ          |
|---------------|------------------------------------------|-----------------|
| `/start`      | Приветствие и описание бота              | Все             |
| `/help`       | Справка по использованию                  | Все             |
| `/reindex`    | Запуск переиндексации документов          | Администраторы  |
| *Любой текст* | Вопрос к RAG-пайплайну                   | Все             |

### Ограничения

- **Rate limit:** 10 сообщений в минуту на пользователя
- **Длина ответа:** автоматическое разбиение на части по 4096 символов

### Настройка администраторов

Укажите свой chat ID в `TELEGRAM_ADMIN_CHAT_IDS`:
```env
TELEGRAM_ADMIN_CHAT_IDS=123456789,987654321
```

Узнать свой chat ID: [@userinfobot](https://t.me/userinfobot)

---

## 🔒 Безопасность

| Механизм                 | Описание                                                       |
|-------------------------|----------------------------------------------------------------|
| **Basic Auth**           | Защита `/reindex` и `/actuator/**` (кроме health/info/prometheus) |
| **STATELESS сессии**     | Без cookie, без CSRF — чистый API                              |
| **Admin whitelist (TG)** | Команда `/reindex` доступна только перечисленным chat ID       |
| **Rate Limiting (HTTP)** | Per-IP ограничение запросов (TokenBucket)                      |
| **Rate Limiting (TG)**   | Per-user ограничение сообщений (10/мин)                        |
| **Нет утечки деталей**   | Ошибки 500 не раскрывают стектрейс или внутренние сообщения    |
| **BCrypt**               | Пароль администратора хэшируется BCrypt                        |
| **Non-root контейнер**   | Docker-образ запускается от пользователя `appuser` (UID 1001)  |

---

## 📊 Мониторинг и метрики

### Actuator-эндпоинты

| Эндпоинт                      | Доступ     | Описание               |
|-------------------------------|------------|------------------------|
| `/actuator/health`            | Публичный  | Статус приложения       |
| `/actuator/info`              | Публичный  | Информация о приложении |
| `/actuator/prometheus`        | Публичный  | Метрики для Prometheus  |
| `/actuator/**` (остальные)    | ADMIN      | Детальная информация    |

### Prometheus

Метрики доступны по адресу `http://localhost:8080/actuator/prometheus`.

Тег для фильтрации в Grafana: `application="medical-license-bot"`.

### JSON-логирование

Для production рекомендуется активировать профиль `json-logs`:
```yaml
SPRING_PROFILES_ACTIVE=json-logs
```

Формат: структурированный JSON (Logstash-совместимый) — удобно для ELK/Loki.

### Graceful Shutdown

Приложение корректно дожидается завершения текущих запросов (до 30 секунд).

---

## 📚 Документация

Дополнительная документация расположена в папке [`docs/`](docs/):

| Файл                                           | Описание                          |
|------------------------------------------------|-----------------------------------|
| [docs/architecture.md](docs/architecture.md)   | Архитектура и компоненты           |
| [docs/deployment.md](docs/deployment.md)       | Руководство по развёртыванию       |
| [docs/api.md](docs/api.md)                     | Полная документация REST API       |

Все Java-классы содержат русскоязычный **JavaDoc** с описанием классов, методов и полей.

---

## 🔧 Разработка

### Сборка и запуск

```bash
# Сборка
./mvnw clean package -DskipTests

# Запуск тестов (59 тестов)
./mvnw test

# Локальный запуск (нужен Qdrant на localhost:6334)
./mvnw spring-boot:run
```

### Docker

```bash
# Сборка образа
docker build -t medical-license-bot .

# Запуск всего стека
docker compose up -d

# Просмотр логов
docker compose logs -f app
```

### Замена OpenRouter на Ollama (локальный LLM)

Для использования **Ollama** вместо OpenRouter:

```bash
# 1. Установите и запустите Ollama
curl -fsSL https://ollama.com/install.sh | sh
ollama pull llama3.1
ollama pull nomic-embed-text

# 2. Измените переменные в .env
OPENROUTER_API_KEY=ollama
OPENROUTER_BASE_URL=http://host.docker.internal:11434/v1
OPENROUTER_MODEL=llama3.1
OPENROUTER_EMBEDDING_MODEL=nomic-embed-text
QDRANT_DIMENSION=768
```

> **Примечание:** размерность (`QDRANT_DIMENSION`) зависит от модели эмбеддингов:
> `nomic-embed-text` → 768, `text-embedding-3-small` → 1536, `text-embedding-3-large` → 3072

### Тестирование

- **59 тестов** (unit + integration)
- Покрытие: контроллеры, сервисы, конфигурации, RAG-пайплайн
- Spring Security тесты с `@WithMockUser`

```bash
./mvnw test
```

### Добавление документов

1. Положите `.doc`/`.docx` файлы в папку `docs/`
2. Вызовите переиндексацию:
   - **Telegram:** `/reindex` (от администратора)
   - **REST:** `curl -X POST http://localhost:8080/reindex -u admin:пароль`

### Структура проекта

```
├── pom.xml                              # Maven-конфигурация
├── Dockerfile                           # Multi-stage сборка
├── docker-compose.yml                   # Qdrant + приложение
├── .env.example                         # Шаблон переменных окружения
├── docs/                                # Документы для RAG + документация
│   ├── architecture.md                  # Архитектура
│   ├── deployment.md                    # Развёртывание
│   └── api.md                           # REST API
└── src/main/java/com/arteva/medbot/
    ├── MedicalLicenseBotApplication.java    # Точка входа
    ├── config/
    │   ├── SecurityConfig.java              # Spring Security (Basic Auth)
    │   ├── RateLimitFilter.java             # Rate limiting по IP
    │   ├── AppProperties.java               # Типобезопасные свойства
    │   ├── LlmConfig.java                   # LLM + Embedding бины
    │   ├── QdrantConfig.java                # Qdrant EmbeddingStore
    │   └── TelegramBotConfig.java           # Регистрация Telegram-бота
    ├── controller/
    │   ├── AskController.java               # POST /ask, POST /reindex
    │   └── GlobalExceptionHandler.java      # Обработка ошибок
    ├── model/
    │   ├── AskRequest.java                  # Запрос (вопрос)
    │   └── AskResponse.java                 # Ответ (текст + источники)
    ├── rag/
    │   ├── DocumentParser.java              # DOC/DOCX → текст (Apache POI)
    │   ├── DocumentIngestionService.java    # Пайплайн индексации
    │   ├── QdrantCollectionManager.java     # Управление коллекцией Qdrant
    │   └── RagService.java                  # RAG: поиск + генерация
    ├── service/
    │   └── TelegramBotService.java          # Telegram Long Polling бот
    └── util/
        └── TokenBucket.java                 # Rate limiting (token bucket)
```

---

## 📄 Лицензия

MIT License. Подробности — в файле [LICENSE](LICENSE).