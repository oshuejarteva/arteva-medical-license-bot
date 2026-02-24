# Medical License Bot

Telegram-бот с RAG (Retrieval-Augmented Generation) для ответов на вопросы по медицинским документам (DOC/DOCX).

## Архитектура

```
┌─────────────┐     ┌──────────────────┐     ┌─────────┐
│  Telegram    │────▶│  Spring Boot App │────▶│  Qdrant │
│  (вопрос)   │◀────│                  │     │ (vector │
└─────────────┘     │  ┌────────────┐  │     │  store) │
                    │  │ RAG Service│  │     └─────────┘
┌─────────────┐     │  └─────┬──────┘  │
│  REST API   │────▶│        │         │     ┌──────────┐
│  /ask       │◀────│        ▼         │────▶│OpenRouter│
│  /reindex   │     │  ┌────────────┐  │◀────│  (LLM)   │
└─────────────┘     │  │  LLM Call  │  │     └──────────┘
                    │  └────────────┘  │
                    └──────────────────┘
```

## Стек технологий

| Компонент      | Технология                        |
|----------------|-----------------------------------|
| Язык           | Java 21                           |
| Фреймворк      | Spring Boot 3.3                   |
| Сборка         | Maven                             |
| RAG Framework  | LangChain4j 0.35                  |
| Vector Store   | Qdrant                            |
| LLM            | OpenRouter (OpenAI-compatible)    |
| Документы      | Apache POI (DOC/DOCX)             |
| Telegram       | TelegramBots (long polling)       |
| Контейнеры     | Docker + docker-compose           |

## Быстрый старт

### 1. Клонируйте репозиторий

```bash
git clone https://github.com/oshuejarteva/arteva-medical-license-bot.git
cd arteva-medical-license-bot
```

### 2. Создайте файл окружения

```bash
cp .env.example .env
```

Отредактируйте `.env`:

```env
OPENROUTER_API_KEY=sk-or-v1-ваш-ключ
TELEGRAM_TOKEN=ваш-токен-бота
TELEGRAM_USERNAME=ИмяВашегоБота
```

### 3. Добавьте документы

Поместите DOC/DOCX файлы в папку `./docs/`:

```bash
cp ~/documents/*.docx ./docs/
```

### 4. Запустите через Docker Compose

```bash
docker compose up -d --build
```

Сервис будет доступен:
- **REST API**: http://localhost:8080
- **Qdrant Dashboard**: http://localhost:6333/dashboard

### 5. Проиндексируйте документы

```bash
curl -X POST http://localhost:8080/reindex
```

Или отправьте `/reindex` боту в Telegram.

## API

### POST /ask

Задать вопрос по документам.

**Request:**
```json
{
  "question": "Какие документы нужны для получения медицинской лицензии?"
}
```

**Response:**
```json
{
  "answer": "Для получения медицинской лицензии необходимы следующие документы: ...",
  "sources": ["license-requirements.docx", "regulations-2024.docx"]
}
```

### POST /reindex

Переиндексация всех документов из папки `./docs`.

**Response:**
```json
{
  "status": "completed",
  "documentsIndexed": 5
}
```

## Telegram-команды

| Команда     | Описание                          |
|-------------|-----------------------------------|
| `/start`    | Приветствие и справка             |
| `/help`     | Краткая справка                   |
| `/reindex`  | Переиндексация документов         |
| *любой текст* | Вопрос по документам            |

## Как добавить новые документы

1. Поместите файлы `.doc` или `.docx` в папку `./docs/`
2. Вызовите переиндексацию:
   ```bash
   curl -X POST http://localhost:8080/reindex
   ```
   или отправьте `/reindex` в Telegram-бот.

## Конфигурация

Все параметры задаются через переменные окружения или `application.yml`:

| Параметр                     | Описание                                    | По умолчанию                         |
|------------------------------|---------------------------------------------|--------------------------------------|
| `OPENROUTER_API_KEY`         | API-ключ OpenRouter                         | —                                    |
| `OPENROUTER_MODEL`           | Модель LLM                                  | `google/gemini-2.0-flash-001`        |
| `OPENROUTER_EMBEDDING_MODEL` | Модель эмбеддингов                          | `openai/text-embedding-3-small`      |
| `OPENROUTER_BASE_URL`        | Base URL для LLM API                        | `https://openrouter.ai/api/v1`       |
| `OPENROUTER_TEMPERATURE`     | Температура генерации                       | `0.1`                                |
| `QDRANT_HOST`                | Хост Qdrant                                 | `localhost`                          |
| `QDRANT_PORT`                | gRPC-порт Qdrant                            | `6334`                               |
| `QDRANT_COLLECTION`          | Название коллекции                          | `medical-documents`                  |
| `QDRANT_DIMENSION`           | Размерность векторов                        | `1536`                               |
| `RAG_TOP_K`                  | Кол-во возвращаемых фрагментов             | `6`                                  |
| `RAG_SIMILARITY_THRESHOLD`   | Порог релевантности                         | `0.75`                               |
| `TELEGRAM_TOKEN`             | Токен Telegram-бота                         | —                                    |
| `TELEGRAM_USERNAME`          | Username бота                               | `MedLicenseBot`                      |
| `TELEGRAM_ENABLED`           | Включить/выключить Telegram                 | `true`                               |
| `DOCS_PATH`                  | Путь к папке с документами                  | `./docs`                             |

## Замена OpenRouter на Ollama (локальный LLM)

Для использования **Ollama** вместо OpenRouter:

### 1. Установите Ollama

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull llama3.1
ollama pull nomic-embed-text
```

### 2. Измените переменные окружения

```env
OPENROUTER_API_KEY=ollama
OPENROUTER_BASE_URL=http://host.docker.internal:11434/v1
OPENROUTER_MODEL=llama3.1
OPENROUTER_EMBEDDING_MODEL=nomic-embed-text
QDRANT_DIMENSION=768
```

> **Примечание:** размерность (`QDRANT_DIMENSION`) зависит от модели эмбеддингов:
> - `nomic-embed-text` → 768
> - `openai/text-embedding-3-small` → 1536
> - `openai/text-embedding-3-large` → 3072

### 3. Добавьте extra_hosts в docker-compose.yml

```yaml
services:
  app:
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### 4. Пересоберите и запустите

```bash
docker compose up -d --build
curl -X POST http://localhost:8080/reindex
```

## Локальная разработка (без Docker)

### Предварительные требования

- Java 21+
- Maven 3.9+
- Qdrant (запустите отдельно):
  ```bash
  docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.12.1
  ```

### Запуск

```bash
export OPENROUTER_API_KEY=sk-or-v1-ваш-ключ
export TELEGRAM_TOKEN=ваш-токен
mvn spring-boot:run
```

## Структура проекта

```
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── docs/                              # Документы для RAG
│   └── .gitkeep
└── src/main/java/com/arteva/medbot/
    ├── MedicalLicenseBotApplication.java
    ├── config/
    │   ├── LlmConfig.java            # OpenRouter LLM + Embedding beans
    │   ├── QdrantConfig.java          # Qdrant EmbeddingStore + collection mgmt
    │   └── TelegramBotConfig.java     # Telegram bot registration
    ├── controller/
    │   └── AskController.java         # REST: POST /ask, POST /reindex
    ├── model/
    │   ├── AskRequest.java            # Request DTO
    │   └── AskResponse.java           # Response DTO
    ├── rag/
    │   ├── DocumentParser.java        # DOC/DOCX → text (Apache POI)
    │   ├── DocumentIngestionService.java  # Ingestion pipeline
    │   └── RagService.java            # Retrieval + LLM generation
    └── service/
        └── TelegramBotService.java    # Telegram long polling bot
```

## Лицензия

MIT