# 📡 Документация REST API

> Полное описание HTTP-эндпоинтов Medical License Bot.

---

## Базовая информация

| Параметр          | Значение                           |
|-------------------|-------------------------------------|
| Базовый URL       | `http://localhost:8080`             |
| Формат данных     | JSON (`application/json`)          |
| Аутентификация    | Basic Auth (для защищённых эндпоинтов) |
| Rate Limiting     | 30 запросов/мин с одного IP         |

---

## Эндпоинты

### POST /ask — Задать вопрос

Обрабатывает вопрос пользователя через RAG-пайплайн и возвращает ответ
на основе проиндексированных документов.

**Доступ:** Публичный (без аутентификации)

#### Запрос

```http
POST /ask HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "question": "Какие документы нужны для получения медицинской лицензии?"
}
```

| Поле       | Тип    | Обязательно | Ограничения           | Описание                  |
|------------|--------|-------------|-----------------------|---------------------------|
| `question` | string | ✅          | 1–4000 символов       | Текст вопроса пользователя |

#### Успешный ответ (200 OK)

```json
{
  "answer": "Для получения медицинской лицензии необходимы следующие документы: ...",
  "sources": ["license-requirements.docx", "order-123.doc"]
}
```

| Поле      | Тип      | Описание                                                    |
|-----------|----------|-------------------------------------------------------------|
| `answer`  | string   | Текст ответа, сгенерированный LLM на основе документов      |
| `sources` | string[] | Список имён файлов-источников. Пустой, если информация не найдена |

#### Ответ при отсутствии информации (200 OK)

```json
{
  "answer": "В документах нет информации по данному вопросу.",
  "sources": []
}
```

#### Ошибки

##### 400 Bad Request — Ошибка валидации

```json
{
  "error": "Validation failed",
  "details": "question: Question must not be blank"
}
```

##### 400 Bad Request — Невалидный JSON

```json
{
  "error": "Malformed request body",
  "message": "Request body is missing or not valid JSON"
}
```

##### 429 Too Many Requests — Превышен лимит запросов

```json
{
  "error": "Too many requests",
  "message": "Rate limit exceeded. Try again later."
}
```

#### Примеры (cURL)

```bash
# Простой вопрос
curl -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Какие требования к помещениям для лицензии?"}'

# С jq для форматирования
curl -s -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Сроки получения лицензии?"}' | jq .
```

---

### POST /reindex — Переиндексация документов

Перечитывает все DOC/DOCX-файлы из папки `docs/`, пересоздаёт коллекцию
в Qdrant и загружает новые эмбеддинги.

**Доступ:** Требует роль ADMIN (Basic Auth)

#### Запрос

```http
POST /reindex HTTP/1.1
Host: localhost:8080
Authorization: Basic YWRtaW46cGFzc3dvcmQ=
```

Тело запроса не требуется.

#### Успешный ответ (200 OK)

```json
{
  "status": "completed",
  "documentsIndexed": 5
}
```

| Поле               | Тип    | Описание                         |
|--------------------|--------|----------------------------------|
| `status`           | string | Статус операции: `completed`     |
| `documentsIndexed` | number | Количество проиндексированных документов |

#### Ошибки

##### 401 Unauthorized — Не авторизован

Возвращается, если не передан заголовок `Authorization` или учётные данные неверны.

##### 403 Forbidden — Недостаточно прав

Возвращается, если у пользователя нет роли ADMIN.

##### 409 Conflict — Переиндексация уже выполняется

```json
{
  "status": "rejected",
  "message": "Reindex is already in progress"
}
```

#### Примеры (cURL)

```bash
# Запуск переиндексации
curl -X POST http://localhost:8080/reindex \
  -u admin:ваш-пароль

# С выводом HTTP-кода
curl -X POST http://localhost:8080/reindex \
  -u admin:ваш-пароль \
  -w "\nHTTP Code: %{http_code}\n"
```

---

## Actuator-эндпоинты

### GET /actuator/health — Статус здоровья

**Доступ:** Публичный

```bash
curl http://localhost:8080/actuator/health
```

**Ответ:**
```json
{
  "status": "UP"
}
```

### GET /actuator/info — Информация о приложении

**Доступ:** Публичный

```bash
curl http://localhost:8080/actuator/info
```

### GET /actuator/prometheus — Метрики

**Доступ:** Публичный

```bash
curl http://localhost:8080/actuator/prometheus
```

Возвращает метрики в формате Prometheus (text/plain).

---

## Аутентификация

### Basic Auth

Защищённые эндпоинты (`/reindex`, `/actuator/**`) используют HTTP Basic Authentication.

**Формат заголовка:**
```
Authorization: Basic base64(username:password)
```

**Пример с cURL:**
```bash
curl -u admin:пароль http://localhost:8080/reindex
```

Логин и пароль задаются через переменные окружения:
- `API_ADMIN_USERNAME` (по умолчанию: `admin`)
- `API_ADMIN_PASSWORD` (обязательно)

---

## Rate Limiting

Все бизнес-эндпоинты защищены ограничением частоты запросов.

| Параметр              | Значение по умолчанию | Описание                              |
|----------------------|----------------------|---------------------------------------|
| Лимит                 | 30 запросов/мин      | На один IP-адрес                      |
| Окно                 | 1 минута             | Фиксированное окно (fixed window)    |
| Исключения           | `/actuator/**`       | Эндпоинты мониторинга не ограничиваются |
| Заголовок            | `X-Forwarded-For`    | Учитывается для определения клиентского IP |

При превышении лимита возвращается **429 Too Many Requests**.

Настройка:
```env
RATE_LIMIT_RPM=30
```

---

## Модель ошибок

Все ошибки возвращаются в едином формате JSON:

```json
{
  "error": "Краткое описание ошибки",
  "message": "Детальное сообщение"
}
```

Для ошибок валидации формат расширенный:
```json
{
  "error": "Validation failed",
  "details": "field1: message1; field2: message2"
}
```

### Таблица кодов ошибок

| HTTP-код | Причина                                  | Эндпоинт        |
|----------|------------------------------------------|-----------------|
| 400      | Невалидный запрос (пустой вопрос, не JSON) | `/ask`          |
| 401      | Отсутствует или неверная авторизация      | `/reindex`      |
| 403      | Недостаточно прав (нет роли ADMIN)        | `/reindex`      |
| 405      | Неподдерживаемый HTTP-метод               | Все             |
| 409      | Переиндексация уже выполняется            | `/reindex`      |
| 429      | Превышен лимит запросов                   | `/ask`,`/reindex`|
| 500      | Внутренняя ошибка сервера                 | Все             |

> **Примечание:** Ответ 500 никогда не раскрывает стектрейс или внутренние сообщения об ошибках —
> это сделано для безопасности.
