# Banking Microservices Application

A full-stack banking application built with React and Java 21 Spring Boot microservices.

## Architecture

```
Frontend (React + Vite)  →  API Gateway (Spring Cloud Gateway :8085)
                                    ↓
        ┌───────────────────────────┼───────────────────────┐
        ▼               ▼           ▼                       ▼
Auth Service      Account Service  Transaction Service  Notification Service
   :8081              :8082             :8083               :8084
        └───────────────────────────┼───────────────────────┘
                                 MySQL :3306
```

## Services & Ports

| Service              | Port |
|----------------------|------|
| Frontend             | 5173 |
| API Gateway          | 8085 |
| Auth Service         | 8081 |
| Account Service      | 8082 |
| Transaction Service  | 8083 |
| Notification Service | 8084 |
| MySQL                | 3306 |

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- Node.js 18+
- Docker & Docker Compose (for MySQL)

### Start MySQL (Docker)
```bash
docker run --name banking-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=banking_db \
  -p 3306:3306 \
  -d mysql:8.0
```

### Run Services
```bash
# Auth Service
cd auth-service && mvn spring-boot:run

# Account Service
cd account-service && mvn spring-boot:run

# Transaction Service
cd transaction-service && mvn spring-boot:run

# Notification Service
cd notification-service && mvn spring-boot:run

# API Gateway
cd api-gateway && mvn spring-boot:run

# Frontend
cd frontend && npm install && npm run dev
```
