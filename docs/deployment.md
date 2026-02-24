# 🚀 Руководство по развёртыванию

> Инструкции по запуску Medical License Bot в различных средах.

---

## Содержание

- [Docker Compose (рекомендуется)](#docker-compose-рекомендуется)
- [Локальная разработка](#локальная-разработка)
- [Production-развёртывание](#production-развёртывание)
- [Мониторинг](#мониторинг)
- [Устранение неполадок](#устранение-неполадок)

---

## Docker Compose (рекомендуется)

### Предварительные требования

- Docker Engine 20.10+
- Docker Compose v2+
- API-ключ [OpenRouter](https://openrouter.ai/keys)
- Токен Telegram-бота от [@BotFather](https://t.me/BotFather)

### Шаг 1: Подготовка окружения

```bash
# Клонируйте репозиторий
git clone https://github.com/arteva/medical-license-bot.git
cd medical-license-bot

# Создайте .env из шаблона
cp .env.example .env
```

### Шаг 2: Заполните `.env`

Обязательные переменные:

```env
# API-ключ для OpenRouter (обязательно)
OPENROUTER_API_KEY=sk-or-v1-ваш-ключ

# Токен Telegram-бота (обязательно)
TELEGRAM_TOKEN=123456789:ABCDefGhIjKlMnOpQrStUvWxYz
TELEGRAM_USERNAME=ИмяВашегоБота

# Пароль администратора (обязательно, сменить на надёжный!)
API_ADMIN_PASSWORD=ваш-надёжный-пароль

# Администраторы Telegram (через запятую)
TELEGRAM_ADMIN_CHAT_IDS=123456789
```

### Шаг 3: Добавьте документы

```bash
# Скопируйте DOC/DOCX файлы в папку docs/
cp /путь/к/документам/*.docx ./docs/
cp /путь/к/документам/*.doc ./docs/
```

### Шаг 4: Запуск

```bash
# Запустите все сервисы
docker compose up -d

# Проверьте статус
docker compose ps

# Посмотрите логи
docker compose logs -f app
```

### Шаг 5: Проиндексируйте документы

```bash
# Через REST API (с авторизацией)
curl -X POST http://localhost:8080/reindex \
  -u admin:ваш-надёжный-пароль

# Или через Telegram — отправьте /reindex боту (если вы в списке админов)
```

### Шаг 6: Проверьте работу

```bash
# Health check
curl http://localhost:8080/actuator/health

# Задать вопрос
curl -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Какие требования к медицинской лицензии?"}'
```

---

## Локальная разработка

### Предварительные требования

- Java 21+ (JDK)
- Maven 3.9+
- Docker (только для Qdrant)

### Шаг 1: Запустите Qdrant

```bash
docker run -d \
  --name qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  qdrant/qdrant:v1.12.1
```

### Шаг 2: Задайте переменные окружения

```bash
export OPENROUTER_API_KEY=sk-or-v1-ваш-ключ
export TELEGRAM_TOKEN=ваш-токен
export TELEGRAM_USERNAME=ИмяВашегоБота
export API_ADMIN_PASSWORD=test-password
export TELEGRAM_ADMIN_CHAT_IDS=ваш-chat-id
```

### Шаг 3: Соберите и запустите

```bash
# Сборка
mvn clean package -DskipTests

# Запуск
mvn spring-boot:run

# Или напрямую
java -jar target/medical-license-bot-1.0.0.jar
```

### Шаг 4: Запуск тестов

```bash
# Все 59 тестов
mvn test

# С подробным выводом
mvn test -Dsurefire.useFile=false

# Только конкретный тест-класс
mvn test -Dtest=RagServiceTest
```

---

## Production-развёртывание

### Рекомендации по безопасности

1. **Смените пароль администратора** на надёжный (минимум 16 символов)
2. **Ограничьте доступ** к порту 8080 — только через reverse proxy
3. **Не публикуйте** порт Qdrant (6333/6334) наружу
4. **Используйте HTTPS** через Nginx/Traefik перед приложением
5. **Включите JSON-логирование** для сбора в ELK/Loki

### Рекомендуемый docker-compose для production

```yaml
services:
  app:
    build: .
    restart: always
    ports:
      - "127.0.0.1:8080:8080"  # Только localhost!
    environment:
      - SPRING_PROFILES_ACTIVE=json-logs
      - QDRANT_HOST=qdrant
      - QDRANT_PORT=6334
    env_file:
      - .env
    depends_on:
      qdrant:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M

  qdrant:
    image: qdrant/qdrant:v1.12.1
    restart: always
    volumes:
      - qdrant_data:/qdrant/storage
    # НЕ публикуем порт наружу!
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:6333/healthz || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  qdrant_data:
```

### Nginx reverse proxy (пример)

```nginx
server {
    listen 443 ssl http2;
    server_name bot.example.com;

    ssl_certificate     /etc/ssl/certs/bot.example.com.crt;
    ssl_certificate_key /etc/ssl/private/bot.example.com.key;

    # Публичный эндпоинт
    location /ask {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Host $host;
    }

    # Мониторинг
    location /actuator/health {
        proxy_pass http://127.0.0.1:8080;
    }

    location /actuator/prometheus {
        proxy_pass http://127.0.0.1:8080;
        # Ограничить доступ по IP
        allow 10.0.0.0/8;
        deny all;
    }

    # Админ-эндпоинт
    location /reindex {
        proxy_pass http://127.0.0.1:8080;
        # Ограничить доступ по IP
        allow 10.0.0.0/8;
        deny all;
    }
}
```

### Настройка ресурсов

| Параметр              | Рекомендация            | Описание                                    |
|----------------------|-------------------------|---------------------------------------------|
| Память (app)          | 384–512 MB              | Зависит от объёма документов                 |
| Память (Qdrant)       | 256–512 MB              | Зависит от количества эмбеддингов            |
| CPU (app)             | 1–2 vCPU               | LLM-вызовы — в основном I/O-bound            |
| Диск (Qdrant)         | 1+ GB                  | SSD рекомендуется                            |

---

## Мониторинг

### Prometheus

Метрики доступны по адресу:
```
http://localhost:8080/actuator/prometheus
```

Основные метрики:
- `http_server_requests_seconds_*` — время HTTP-запросов
- `jvm_memory_*` — использование памяти JVM
- `process_cpu_usage` — загрузка CPU
- `logback_events_total` — количество логов по уровням

### Grafana (пример scrape config для Prometheus)

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'medical-license-bot'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']
    scrape_interval: 15s
```

### Health Check

```bash
# Статус приложения
curl http://localhost:8080/actuator/health

# Ожидаемый ответ
{"status": "UP"}
```

### JSON-логирование

Для сбора логов в ELK/Loki активируйте профиль:
```env
SPRING_PROFILES_ACTIVE=json-logs
```

Формат вывода: структурированный JSON (Logstash Encoder).

---

## Устранение неполадок

### Приложение не запускается

| Проблема                              | Решение                                      |
|---------------------------------------|----------------------------------------------|
| `openrouter.api-key is required`       | Задайте `OPENROUTER_API_KEY` в `.env`        |
| `api.admin.password is required`       | Задайте `API_ADMIN_PASSWORD` в `.env`        |
| `Cannot initialize Qdrant collection`  | Проверьте, что Qdrant запущен и доступен     |
| `Port 8080 already in use`             | Остановите конфликтующий процесс             |

### Qdrant не отвечает

```bash
# Проверьте статус контейнера
docker compose ps qdrant

# Проверьте логи
docker compose logs qdrant

# Перезапустите
docker compose restart qdrant
```

### Telegram-бот не отвечает

1. Проверьте `TELEGRAM_TOKEN` — корректный ли токен?
2. Проверьте `TELEGRAM_ENABLED=true`
3. Посмотрите логи: `docker compose logs app | grep -i telegram`
4. Убедитесь, что бот не запущен в другом месте (Long Polling — только один экземпляр)

### Переиндексация выдаёт 409

Переиндексация уже выполняется. Дождитесь завершения или перезапустите приложение.

### Нет ответов по документам

1. Проверьте, что в `docs/` есть `.doc`/`.docx` файлы
2. Запустите переиндексацию
3. Снизьте `RAG_SIMILARITY_THRESHOLD` (например, до `0.6`)
4. Увеличьте `RAG_TOP_K` (например, до `10`)

### Обновление документов

```bash
# 1. Добавьте/обновите файлы в docs/
cp new-document.docx ./docs/

# 2. Запустите переиндексацию
curl -X POST http://localhost:8080/reindex -u admin:пароль
```
