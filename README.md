# Notification Service

A Spring Boot–based notification service built to learn and implement **event-driven architecture, message queues, asynchronous processing, retry mechanisms, dead-letter queues, rate limiting, and multi-channel notifications**.

The project is being built incrementally, with each feature introducing a system-design concept and applying it to a practical backend system.

---

## Why This Project?

Notification systems are a good example of distributed backend architecture.

A simple requirement such as:

> "Send an email when a notification is created"

quickly introduces several real-world engineering problems:

* What happens if the email provider is unavailable?
* Should the API wait until the email is sent?
* How do we retry failed notifications?
* How many times should we retry?
* What happens when a message keeps failing?
* How do we prevent a failing notification from blocking other notifications?
* How do we support Email, SMS, and Push notifications?
* How do we control the number of requests sent to external providers?
* How do we inspect messages that could not be delivered?

This project explores those problems by building a notification system around **Spring Boot + RabbitMQ + PostgreSQL**.

---

# Goals

The main goals of this project are:

* Build a notification service using Spring Boot
* Learn event-driven architecture
* Learn asynchronous processing
* Understand RabbitMQ and message queues
* Implement multiple notification channels
* Implement retry mechanisms
* Implement Dead Letter Queues (DLQ)
* Understand transient vs permanent failures
* Implement rate limiting
* Understand message acknowledgement
* Understand message requeueing
* Persist notification state in PostgreSQL
* Containerize infrastructure using Docker
* Gradually evolve the system toward a production-style architecture

---

# Tech Stack

| Technology  | Purpose                         |
| ----------- | ------------------------------- |
| Java        | Programming language            |
| Spring Boot | Backend framework               |
| Spring AMQP | RabbitMQ integration            |
| RabbitMQ    | Message broker                  |
| PostgreSQL  | Persistent database             |
| Docker      | Infrastructure/containerization |
| Maven       | Dependency management           |
| Git         | Version control                 |

---

# High-Level Architecture

The current system follows an asynchronous/event-driven approach.

```text
                    Client
                      |
                      | HTTP
                      v
              +---------------+
              | Spring Boot   |
              | REST API      |
              +-------+-------+
                      |
                      | Create notification
                      v
              +---------------+
              | PostgreSQL    |
              | notifications |
              +---------------+

                      |
                      | Publish event
                      v
              +---------------+
              |   RabbitMQ    |
              |   Exchange    |
              +-------+-------+
                      |
                      v
              +---------------+
              | Notification  |
              |    Queue       |
              +-------+-------+
                      |
                      v
              +---------------+
              | Notification  |
              |   Consumer    |
              +-------+-------+
                      |
               +------+------+
               |             |
            Success        Failure
               |             |
               v             v
              ACK          Retry
                             |
                             v
                            DLQ
```

---

# Notification Channels

The system is designed to support:

```text
EMAIL
SMS
PUSH
```

The notification message contains information such as:

```text
notificationId
recipient
channel
template
```

The consumer can determine which notification channel should be used based on the message.

---

# PostgreSQL

PostgreSQL is used as the persistent data store for notification information.

## Notification Entity

The current notification model contains:

```text
id
recipient
channel
template
status
createdAt
updatedAt
```

The entity follows the structure:

```java
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

The database gives us a **source of truth** for notification state instead of relying only on messages in RabbitMQ.

---

# RabbitMQ

RabbitMQ is used as the message broker between notification creation and notification processing.

The main reason for introducing RabbitMQ is to make notification delivery **asynchronous**.

Instead of:

```text
Client
  |
  v
API
  |
  v
Send Email
  |
  v
Response
```

we move toward:

```text
Client
  |
  v
API
  |
  v
Publish Event
  |
  v
RabbitMQ
  |
  v
Response
```

The consumer processes the notification independently.

This provides better separation between:

* Notification creation
* Notification processing
* External notification providers

---

# RabbitMQ Components

The current RabbitMQ architecture contains:

```text
notification.exchange
        |
        v
notification.queue
        |
        v
Notification Consumer
```

For failed messages:

```text
notification.queue
        |
        | failure
        v
notification.dlx
        |
        v
notification.dlq
```

Where:

### Main Exchange

```text
notification.exchange
```

Receives notification events.

### Main Queue

```text
notification.queue
```

Contains notifications waiting to be processed.

### Dead Letter Exchange

```text
notification.dlx
```

Receives messages that have been rejected/dead-lettered.

### Dead Letter Queue

```text
notification.dlq
```

Stores messages that could not be successfully processed.

---

# Notification Consumer

The consumer listens to:

```text
notification.queue
```

Current consumer:

```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
public void consume(NotificationMessage message) {

    System.out.println(
        "Processing notification " +
        message.getNotificationId()
    );

    System.out.println(
        "Sending " +
        message.getChannel() +
        " to " +
        message.getRecipient()
    );

    throw new RuntimeException(
        "Deliberate notification failure"
    );
}
```

The deliberate exception is currently used for testing the retry and DLQ behaviour.

The exception is intentionally **not caught inside the consumer**.

This allows Spring AMQP's listener infrastructure to handle the failure.

---

# Message Acknowledgement

RabbitMQ uses acknowledgements to determine whether a consumer successfully processed a message.

Conceptually:

```text
Message
   |
   v
Consumer
   |
   +---- Success ----> ACK
   |
   +---- Failure ----> Retry / Reject
```

Previously, the consumer manually acknowledged messages:

```java
channel.basicAck(deliveryTag, false);
```

and manually requeued failed messages:

```java
channel.basicNack(deliveryTag, false, true);
```

However, manually requeueing indefinitely can result in:

```text
Failure
  ↓
Requeue
  ↓
Failure
  ↓
Requeue
  ↓
Failure
  ↓
...
```

This can create an infinite retry loop.

The project therefore moves retry responsibility to Spring AMQP.

---

# Retry Mechanism

The goal of the retry mechanism is:

```text
Initial Attempt
      |
      v
   Failure
      |
      v
   Retry #1
      |
      v
   Failure
      |
      v
   Retry #2
      |
      v
   Failure
      |
      v
     DLQ
```

The retry configuration is:

```properties
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-retries=2
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10000
```

This means:

```text
Initial attempt
      ↓
wait ~1 second
      ↓
Retry #1
      ↓
wait ~2 seconds
      ↓
Retry #2
      ↓
failure
      ↓
DLQ
```

The exact timing can vary slightly depending on the listener and application execution.

---

# Why Retry?

External services are not always permanently unavailable.

For example:

```text
Email provider
     |
     X
Temporary network failure
```

Retrying may allow the notification to succeed later.

Typical transient failures include:

* Network timeout
* Temporary provider outage
* Connection failure
* Temporary HTTP 5xx response

However, not every failure should be retried.

For example:

```text
Invalid email address
Malformed notification
Invalid recipient
```

These are potentially permanent failures.

This leads to the next planned improvement:

```text
Transient Failure
       |
       v
     Retry

Permanent Failure
       |
       v
      DLQ
```

---

# Dead Letter Queue

A Dead Letter Queue is used for messages that could not be successfully processed.

The main queue is configured with:

```java
QueueBuilder
    .durable(QUEUE_NAME)
    .deadLetterExchange(DLX_NAME)
    .deadLetterRoutingKey(DLQ_ROUTING_KEY)
    .build();
```

This tells RabbitMQ that when a message is rejected without requeueing, it should be routed to:

```text
notification.dlx
```

which then routes it to:

```text
notification.dlq
```

The resulting architecture is:

```text
                    notification.queue
                            |
                         failure
                            |
                       Spring Retry
                            |
                    +-------+-------+
                    |       |       |
                  Try 1   Try 2   Try 3
                            |
                         failure
                            |
                            v
                  notification.dlx
                            |
                            v
                  notification.dlq
```

---

# Why DLQ?

Without a DLQ, a permanently failing message could continuously block the system or be repeatedly reprocessed.

The DLQ gives us a place to:

* Inspect failed notifications
* Debug failures
* Monitor problematic messages
* Investigate external provider issues
* Potentially reprocess messages later

For example:

```text
notification.dlq

ID       Channel     Recipient
-----------------------------------------
7        EMAIL       user@example.com
8        SMS         +919876543210
9        PUSH        device-token
```

---

# Docker

PostgreSQL and RabbitMQ are currently run using Docker Compose.

This keeps infrastructure dependencies isolated from the local machine.

## Docker Compose

```yaml
services:

  postgres:
    image: postgres:17
    container_name: notification-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: notification_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:4-management
    container_name: notification-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq

volumes:
  postgres_data:
  rabbitmq_data:
```

---

# Starting Infrastructure

Start PostgreSQL and RabbitMQ:

```bash
docker compose up -d
```

Check running containers:

```bash
docker compose ps
```

Expected containers:

```text
notification-postgres
notification-rabbitmq
```

Stop containers:

```bash
docker compose down
```

Stop containers and remove persistent volumes:

```bash
docker compose down -v
```

---

# PostgreSQL Configuration

When Spring Boot runs directly on the host machine:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/notification_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

PostgreSQL can also be accessed directly through the container:

```bash
docker exec -it notification-postgres \
psql -U postgres -d notification_db
```

To inspect tables:

```sql
\dt
```

---

# RabbitMQ Configuration

When Spring Boot runs directly on the host machine:

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

RabbitMQ Management UI:

```text
http://localhost:15672
```

Default credentials:

```text
Username: guest
Password: guest
```

The management interface can be used to inspect:

* Exchanges
* Queues
* Bindings
* Consumers
* Message counts
* Message rates
* Dead-letter queues

---

# Running the Application

## 1. Start infrastructure

```bash
docker compose up -d
```

## 2. Start Spring Boot

Using Maven:

```bash
mvn spring-boot:run
```

Or run:

```text
NotificationServiceApplication
```

from IntelliJ IDEA.

## 3. Create a notification

Use the notification API to create a notification.

Example payload:

```json
{
  "recipient": "user@example.com",
  "channel": "EMAIL",
  "template": "WELCOME"
}
```

The application should:

```text
Create Notification
        |
        v
PostgreSQL
        |
        v
Publish RabbitMQ message
        |
        v
notification.queue
        |
        v
Notification Consumer
```

---

# Current Failure Testing

The consumer currently deliberately throws:

```java
throw new RuntimeException(
    "Deliberate notification failure"
);
```

This allows the retry and DLQ pipeline to be tested without depending on an actual email/SMS provider.

Expected behaviour:

```text
Processing notification 7
Sending EMAIL to user@example.com

Processing notification 7
Sending EMAIL to user@example.com

Processing notification 7
Sending EMAIL to user@example.com

                 ↓

        notification.dlq
```

This test verifies that:

1. RabbitMQ delivers the message.
2. The consumer processes it.
3. The consumer throws an exception.
4. Spring detects the failure.
5. The configured retry mechanism is invoked.
6. Retries are eventually exhausted.
7. The failed message is dead-lettered.

---

# Project Concepts Learned So Far

This project has introduced several important backend/system-design concepts.

### 1. Asynchronous Processing

The API does not need to perform the notification delivery synchronously.

```text
API → Queue → Consumer
```

### 2. Event-Driven Architecture

Notification events are passed through a message broker.

```text
Producer → RabbitMQ → Consumer
```

### 3. Message Queues

RabbitMQ decouples notification creation from notification processing.

### 4. Message Acknowledgement

Consumers acknowledge successful processing.

### 5. Message Requeueing

Failed messages can potentially be returned to the queue.

### 6. Retry

Transient failures can be retried before permanently failing.

### 7. Dead Letter Queue

Messages that cannot be processed successfully can be isolated for investigation.

### 8. Separation of Concerns

The system separates:

```text
API
 |
 +-- Persistence
 |
 +-- Messaging
 |
 +-- Notification Processing
 |
 +-- External Providers
```

---

# Project Roadmap

The project is intentionally being built incrementally.

## Completed

* [x] Spring Boot application
* [x] PostgreSQL integration
* [x] Notification entity
* [x] Notification persistence
* [x] RabbitMQ integration
* [x] Notification exchange
* [x] Notification queue
* [x] RabbitMQ consumer
* [x] Message acknowledgement concepts
* [x] Failure handling
* [x] Retry configuration
* [x] Dead Letter Exchange
* [x] Dead Letter Queue
* [x] Docker Compose for PostgreSQL
* [x] Docker Compose for RabbitMQ
* [x] Deliberate failure testing

## Planned

* [ ] Transient vs permanent exception handling
* [ ] Channel-specific notification handlers
* [ ] Email notification implementation
* [ ] SMS notification implementation
* [ ] Push notification implementation
* [ ] Notification status management
* [ ] Retry metadata / attempt tracking
* [ ] Rate limiting
* [ ] Provider abstraction
* [ ] Idempotency
* [ ] Duplicate message handling
* [ ] Observability
* [ ] Metrics with Prometheus
* [ ] Grafana dashboards
* [ ] Structured logging
* [ ] Notification delivery history
* [ ] Authentication and authorization
* [ ] Production-ready Docker setup

---

# Future Architecture

The eventual system is expected to evolve toward:

```text
                         Client
                           |
                           v
                    +-------------+
                    | REST API    |
                    +------+------+
                           |
                           v
                    +-------------+
                    | PostgreSQL  |
                    +-------------+
                           |
                           | Event
                           v
                    +-------------+
                    | RabbitMQ    |
                    +------+------+
                           |
                    Notification Queue
                           |
                           v
                 +---------------------+
                 | Notification       |
                 | Consumer            |
                 +----------+----------+
                            |
              +-------------+-------------+
              |             |             |
              v             v             v
           Email           SMS          Push
           Provider      Provider       Provider
              |             |             |
              +-------------+-------------+
                            |
                            v
                    Notification Status
```

With failure handling:

```text
                       Consumer
                           |
                       Processing
                           |
                 +---------+---------+
                 |                   |
              Success              Failure
                 |                   |
                ACK                Retry
                                     |
                              +------+------+
                              |             |
                           Transient     Permanent
                              |             |
                            Retry          DLQ
                              |
                         Max retries
                              |
                              v
                             DLQ
```

---

# Learning Objective

This is not intended to be just another CRUD application.

The primary goal is to understand **why distributed systems use these components and what problems each component solves**.

The project therefore evolves feature-by-feature:

```text
CRUD
 ↓
Async Processing
 ↓
Message Queue
 ↓
Event-Driven Architecture
 ↓
Retry
 ↓
DLQ
 ↓
Failure Classification
 ↓
Rate Limiting
 ↓
Idempotency
 ↓
Observability
 ↓
Production Architecture
```

Each stage builds on the previous one and introduces a new system-design concept.

---

# Author

Built as a hands-on learning project to explore:

**Java • Spring Boot • RabbitMQ • PostgreSQL • Docker • Distributed Systems • System Design**
