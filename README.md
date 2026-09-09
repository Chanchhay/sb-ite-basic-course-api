# iPOS Backend API

<p align="center">
  <strong>Scalable, Secure & Production-Ready Backend Service for the FluxiBiz / iPOS Platform</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 4.1.0" />
  <img src="https://img.shields.io/badge/PostgreSQL-18-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 18" />
  <img src="https://img.shields.io/badge/Redis-8.10-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Keycloak-OAuth2%20%2F%20OIDC-4D4D4D?style=for-the-badge&logo=keycloak&logoColor=white" alt="Keycloak" />
  <img src="https://img.shields.io/badge/MinIO-Object_Storage-C72E49?style=for-the-badge&logo=minio&logoColor=white" alt="MinIO" />
  <img src="https://img.shields.io/badge/Docker-Containerized-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/Jenkins-CI%2FCD-D24939?style=for-the-badge&logo=jenkins&logoColor=white" alt="Jenkins" />
</p>

---

## Overview

**iPOS Backend API** is the core backend service powering the **FluxiBiz / iPOS ecosystem**.

Built with **Java 25** and **Spring Boot 4.1**, the application provides secure REST APIs for business operations, product catalog management, inventory, customers, carts, orders, payments, data migration, notifications, social integrations, authentication, and platform administration.

The system follows a **feature-oriented modular architecture** and integrates with PostgreSQL, Redis, Keycloak, MinIO, WebSocket, NBC Bakong KHQR, Telegram, Facebook, and other supporting services.

The backend is designed to serve multiple clients including:

- Customer-facing storefront
- Business dashboard
- Administrative dashboard
- POS applications
- Customer display systems
- External integrations

---

## Core Features

### Business Management

- Business registration and management
- Business configuration
- Business owner management
- Multi-business platform support
- Administrative business operations

### Authentication & Users

- User registration
- User management
- OAuth2 / OpenID Connect authentication
- JWT-based API security
- Keycloak integration
- Keycloak administrative operations
- Role and permission-based authorization

### Catalog Management

- Product management
- Categories
- Product variants
- Pricing
- Product media
- QR and barcode generation
- Storefront catalog APIs

### Inventory Management

- Inventory tracking
- Stock management
- Inventory-related business operations
- Product availability management

### Cart & Orders

- Shopping cart management
- Cart item operations
- Order creation
- Order processing
- Order lifecycle management
- Customer order workflows

### Customer Management

- Customer profiles
- Customer-related operations
- Customer display integration

### Payments

- Payment processing
- NBC Bakong KHQR integration
- KHQR generation
- Payment verification
- ABA PayWay integration support

### Data Import & Migration

- CSV import
- Excel / XLSX import
- Data validation
- Legacy data migration
- Import workflow processing
- Migration file storage

### Notifications

- Real-time notifications
- WebSocket communication
- Telegram integration
- Payment polling workflows
- Application notification services

### Social Integration

- Facebook OAuth integration
- Facebook webhook support
- External social service integration

### Object Storage

- MinIO S3-compatible object storage
- Business asset storage
- Product/media storage
- Import-file storage

---

## Tech Stack

| Category | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1.0 |
| Build Tool | Gradle |
| Web Framework | Spring Web MVC |
| Persistence | Spring Data JPA |
| Database | PostgreSQL 18 |
| Cache | Redis 8 |
| Security | Spring Security |
| Authentication | OAuth2 Resource Server |
| IAM | Keycloak |
| Object Storage | MinIO |
| Validation | Jakarta Bean Validation |
| Mapping | MapStruct 1.6.3 |
| Boilerplate Reduction | Lombok |
| Real-Time | Spring WebSocket |
| CSV Processing | Apache Commons CSV |
| Excel Processing | Apache POI |
| QR / Barcode | ZXing |
| Payment | NBC Bakong KHQR SDK |
| API Documentation | Springdoc OpenAPI + Scalar |
| Monitoring | Spring Boot Actuator |
| Containerization | Docker |
| Orchestration | Docker Compose |
| CI/CD | Jenkins |
| Container Registry | Google Artifact Registry |
| Reverse Proxy | Traefik |

---

## System Architecture

<p align="center">
  <img
    src="./src/main/resources/system-architecture.png"
    alt="iPOS Backend System Architecture"
    width="900"
  />
</p>

<p align="center">
  <em>High-Level System Architecture of the FluxiBiz / iPOS Backend Platform</em>
</p>

---

### Module Responsibilities

| Module | Responsibility |
| --- | --- |
| `admin` | Administrative platform operations |
| `auth` | Authentication-related functionality |
| `business` | Business and organization management |
| `cart` | Shopping cart operations |
| `catalog` | Product and catalog management |
| `channel` | Business / sales channel management |
| `customer` | Customer management |
| `customerdisplay` | Customer-facing display functionality |
| `dataimport` | Data import workflows |
| `discount` | Discount configuration and processing |
| `inventory` | Inventory and stock management |
| `migration` | Legacy/business data migration |
| `minio` | Object storage integration |
| `notification` | Notification and real-time communication |
| `order` | Order processing |
| `payment` | Payment and KHQR operations |
| `register` | Registration workflows |
| `social` | Social-platform integrations |
| `user` | User management |

---

## Project Structure

```text
sb-ite-basic-course-api/
│
├── api-docs/
│   ├── endpoints-authorizations.json
│   ├── ipos_api_endpoints_updated.md
│   ├── keycloak-client-role.json
│   └── storefront-item-api.md
│
├── database/
│   └── *.sql
│
├── gradle/
│   └── wrapper/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── kh/edu/istad/ite/
│   │   │       ├── config/
│   │   │       ├── features/
│   │   │       ├── shared/
│   │   │       └── IteSbApiApplication.java
│   │   │
│   │   └── resources/
│   │       ├── db/
│   │       ├── application.yaml
│   │       ├── application-dev.yaml
│   │       └── application-prod.yaml
│   │
│   └── test/
│
├── .dockerignore
├── .env.example
├── .gitattributes
├── .gitignore
├── Dockerfile
├── Jenkinsfile
├── compose.yml
├── deploy-local.ps1
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
└── README.md
```

---

## Getting Started

### Prerequisites

Before running the project locally, make sure you have:

| Requirement | Version |
| --- | --- |
| Java | 25+ |
| Git | Latest |
| Docker | Latest |
| Docker Compose | V2 |
| PostgreSQL | 18 recommended |
| Redis | 8 recommended |
| Keycloak | Configured realm/client |
| MinIO | Accessible instance |

> Gradle does not need to be installed globally because this repository includes the **Gradle Wrapper**.

Verify Java:

```bash
java --version
```

Expected major version:

```text
25
```

---

### 1️Clone the Repository

```bash
git clone https://github.com/Chanchhay/sb-ite-basic-course-api.git
cd sb-ite-basic-course-api
```

---

### 2️Configure Environment Variables

Use `.env.example` as the reference configuration.

```bash
cp .env.example .env
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Example:

```env
# ==========================================================
# DATABASE
# ==========================================================

PGDATABASE=fluxibix
PGHOST=localhost
PGPORT=1681

DB_USER=postgres
DB_PASS=your_password


# ==========================================================
# REDIS
# ==========================================================

SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=

REDIS_PASSWORD=


# ==========================================================
# KEYCLOAK
# ==========================================================

ISSUER_URI=https://your-keycloak-host/realms/your-realm

KEYCLOAK_URL=https://your-keycloak-host
KEYCLOAK_CLIENT_ID=
KEYCLOAK_CLIENT_SECRET=
TARGET_REALM=


# ==========================================================
# MINIO
# ==========================================================

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=


# ==========================================================
# APPLICATION
# ==========================================================

SERVER_URL=http://localhost:8080

BASE_STOREFRONT_DOMAIN=localhost

CREDENTIAL_ENCRYPTION_KEY=


# ==========================================================
# TELEGRAM
# ==========================================================

TELEGRAM_WEBHOOK_BASE_URL=


# ==========================================================
# FACEBOOK
# ==========================================================

FACEBOOK_APP_ID=
FACEBOOK_APP_SECRET=
FACEBOOK_OAUTH_REDIRECT_URI=
FACEBOOK_FRONTEND_RESULT_URL=
FACEBOOK_WEBHOOK_VERIFY_TOKEN=


# ==========================================================
# OPTIONAL PAYMENT INTEGRATION
# ==========================================================

ABA_MERCHANT_ID=
ABA_API_KEY=


# ==========================================================
# DEPLOYMENT
# ==========================================================

IMAGE_TAG=latest
```

> `.env` is consumed directly by Docker Compose. When using `./gradlew bootRun`, export these variables through your shell or configure them in your IDE run configuration.

> Never commit real passwords, private keys, access tokens, client secrets, or production credentials.

---

### 3️Start Required Dependencies

For local development you need access to:

```text
PostgreSQL
Redis
Keycloak
MinIO
```

Default development addresses are typically:

| Service | Default |
| --- | --- |
| Backend API | `localhost:8080` |
| PostgreSQL | `localhost:1681` |
| Redis | `localhost:6379` |
| MinIO | `localhost:9000` |
| Keycloak | Depends on your environment |

---

### 4️Run the Application

Linux / macOS:

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

The default Spring profile is:

```text
dev
```

The application runs at:

```text
http://localhost:8080
```

---

## API Documentation

The project uses **Springdoc OpenAPI** with **Scalar** for interactive API documentation.

### Scalar UI

```text
http://localhost:8080/docs
```

### OpenAPI JSON

```text
http://localhost:8080/v3/api-docs
```

Additional API documentation is available inside:

```text
api-docs/
```

Including:

```text
api-docs/
├── endpoints-authorizations.json
├── ipos_api_endpoints_updated.md
├── keycloak-client-role.json
└── storefront-item-api.md
```

---

## Authentication & Authorization

The backend acts as an **OAuth2 Resource Server** and validates JWT access tokens issued by Keycloak.

Authentication flow:

```text
User
  │
  ▼
Frontend Application
  │
  ▼
Keycloak
  │
  │ JWT Access Token
  ▼
Frontend
  │
  │ Authorization: Bearer <ACCESS_TOKEN>
  ▼
iPOS Backend API
  │
  ├── JWT Signature Validation
  ├── Issuer Validation
  ├── Expiration Validation
  ├── Role / Authority Extraction
  └── Endpoint Authorization
```

Example authenticated request:

```bash
curl \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8080/api/v1/...
```

Authorization-related documentation can be found in:

```text
api-docs/endpoints-authorizations.json
api-docs/keycloak-client-role.json
```

### Security Principle

Frontend permission checks are useful for controlling the user interface, but **backend authorization remains the final security boundary**.

Every protected operation must be validated by Spring Security.

---

## Database

The application uses **PostgreSQL** as its primary relational database.

Development configuration:

```text
Database : fluxibix
Host     : localhost
Port     : 1681
```

Configuration:

```env
PGDATABASE=fluxibix
PGHOST=localhost
PGPORT=1681

DB_USER=
DB_PASS=
```

The project also contains SQL resources under:

```text
database/
```

During the Gradle resource-processing phase, SQL files from this directory are copied into:

```text
db/migration/
```

inside the generated application artifact.

---

## Redis

Redis is used for application caching and shared runtime data.

Configuration:

```env
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
```

Default local address:

```text
localhost:6379
```

Test Redis connectivity:

```bash
redis-cli ping
```

Expected response:

```text
PONG
```

---

## MinIO Object Storage

The backend integrates with **MinIO** through its S3-compatible Java SDK.

Configuration:

```env
MINIO_ENDPOINT=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
```

The application separates platform assets and import files.

Configured buckets include:

```text
fluxibix
fluxibiz-imports
```

Typical stored objects include:

- Product images
- Business assets
- Uploaded documents
- Import files
- Migration files

MinIO access keys must remain backend-only.

---

## Data Import & Migration

The application contains dedicated modules for importing and migrating business data.

Supported file-processing libraries include:

- Apache Commons CSV
- Apache POI

Supported formats include:

```text
CSV
XLSX
```

The migration pipeline can be used to process data exported from existing or legacy POS systems before importing it into FluxiBiz.

---

## Payment Integration

### NBC Bakong KHQR

The backend integrates with the official **NBC Bakong KHQR SDK**.

Supported payment capabilities include:

- KHQR generation
- QR payment workflows
- Payment verification
- Payment-status processing

### ABA PayWay

The backend also contains configuration for ABA PayWay integration.

```env
ABA_MERCHANT_ID=
ABA_API_KEY=
```

Payment credentials must never be committed to source control.

---

## Real-Time Communication

The application uses **Spring WebSocket** for real-time communication.

Potential real-time workflows include:

- Order updates
- Notifications
- Customer display updates
- Payment events
- Operational events

WebSocket-related configuration is maintained inside the application's configuration packages.

---

## External Integrations

### Telegram

Configuration:

```env
TELEGRAM_WEBHOOK_BASE_URL=
```

Telegram integration can be used by backend notification workflows.

### Facebook

Configuration:

```env
FACEBOOK_APP_ID=
FACEBOOK_APP_SECRET=
FACEBOOK_OAUTH_REDIRECT_URI=
FACEBOOK_FRONTEND_RESULT_URL=
FACEBOOK_WEBHOOK_VERIFY_TOKEN=
```

The backend supports Facebook OAuth and webhook integration through the `social` feature module.

---

## Testing

Run all tests:

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

Clean and test:

```bash
./gradlew clean test
```

Full build:

```bash
./gradlew clean build
```

The project uses the **JUnit Platform** through Spring Boot's testing support.

---

## Build

Build the executable Spring Boot JAR:

```bash
./gradlew bootJar
```

Generated artifacts are stored in:

```text
build/libs/
```

Run the generated JAR:

```bash
java -jar build/libs/*.jar
```

---

## Docker

The production Docker image uses:

```text
eclipse-temurin:25-jre-alpine
```

The container runs the application using a dedicated non-root `spring` user.

### Build Spring Boot JAR

```bash
./gradlew clean bootJar
```

### Prepare Docker Artifact

The current Dockerfile expects:

```text
app.jar
```

Prepare it:

```bash
cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" app.jar
```

### Build Docker Image

```bash
docker build -t ipos-api:local .
```

### Run Docker Container

```bash
docker run \
  --env-file .env \
  -p 8080:8080 \
  ipos-api:local
```

Remove the temporary JAR afterward if necessary:

```bash
rm -f app.jar
```

---

## Docker Compose

The repository contains:

```text
compose.yml
```

The production Compose stack includes:

- PostgreSQL 18
- Redis 8
- iPOS Backend API

The API also connects to externally configured services such as Keycloak and MinIO.

### Production-Oriented Services

```text
compose.yml
├── db
├── redis
└── api
```

Start:

```bash
docker compose up -d
```

Check containers:

```bash
docker compose ps
```

View API logs:

```bash
docker compose logs -f api
```

View PostgreSQL logs:

```bash
docker compose logs -f db
```

View Redis logs:

```bash
docker compose logs -f redis
```

Stop:

```bash
docker compose down
```

> The repository's Compose configuration is production-oriented and expects the external `proxy` Docker network and the configured container registry image.

---

## Application Health

The project includes **Spring Boot Actuator** for application observability and operational health integration.

Actuator can support:

- Application health checks
- Container monitoring
- Infrastructure probes
- Deployment verification
- Runtime observability

The exact exposed endpoints depend on the active Spring configuration.

---

## CI/CD Pipeline

The project uses **Jenkins** for automated testing, building, containerization, registry publishing, and deployment.

```text
Git Push
   │
   ▼
Jenkins
   │
   ├── Validate Java / Gradle
   │
   ├── Run Tests
   │
   ├── Build Spring Boot JAR
   │
   ├── Prepare app.jar
   │
   ├── Generate Git SHA Tag
   │
   ├── Build Docker Image
   │
   ├── Push Image
   │
   └── Deploy to Server
```

### Pipeline Flow

```text
Source Code
    │
    ▼
Jenkins Pipeline
    │
    ▼
./gradlew test bootJar
    │
    ▼
Docker Image
    │
    ▼
Google Artifact Registry
    │
    ▼
SSH Deployment
    │
    ▼
Docker Compose
    │
    ▼
Traefik
    │
    ▼
Production API
```

Images are tagged using the Git commit SHA:

```text
ipos-api:<git-sha>
```

and also published as:

```text
ipos-api:latest
```

The immutable Git SHA tag is used to identify the deployed application version.

---

## Deployment

Production traffic is routed through **Traefik**.

Production API:

```text
https://api.fluxibiz.store
```

The backend container listens internally on:

```text
8080
```

Traefik handles:

- HTTPS routing
- TLS termination
- Let's Encrypt certificates
- Reverse proxying
- Service discovery through Docker labels

Production flow:

```text
Internet
   │
   ▼
HTTPS
   │
   ▼
Traefik
   │
   ▼
iPOS Backend API
   │
   ├── PostgreSQL
   ├── Redis
   ├── Keycloak
   ├── MinIO
   └── External Integrations
```

---

## Security Guidelines

Because this backend processes platform identities, business information, integration credentials, payments, and administrative operations, security must be treated as a primary requirement.

### Required Practices

- Never commit `.env`
- Never commit passwords or API secrets
- Never hardcode production credentials
- Use OAuth2 / OIDC for authentication
- Validate JWT signatures
- Validate token issuer and expiration
- Apply backend authorization to protected endpoints
- Restrict CORS to trusted origins
- Keep Keycloak client secrets server-side
- Keep MinIO credentials server-side
- Keep PostgreSQL private
- Keep Redis private
- Use HTTPS in production
- Encrypt sensitive integration credentials
- Avoid logging access tokens
- Avoid logging passwords or secrets
- Rotate compromised credentials immediately

Never store real secrets inside:

```text
README.md
application.yaml
application-dev.yaml
application-prod.yaml
Dockerfile
compose.yml
Jenkinsfile
```

Use environment variables or a secure secret-management mechanism instead.

---

## Common Commands

| Command | Description |
| --- | --- |
| `./gradlew bootRun` | Run backend locally |
| `./gradlew test` | Run automated tests |
| `./gradlew clean test` | Clean and run tests |
| `./gradlew build` | Build and test application |
| `./gradlew bootJar` | Create executable Spring Boot JAR |
| `docker build -t ipos-api:local .` | Build Docker image |
| `docker compose up -d` | Start Compose services |
| `docker compose ps` | Show container status |
| `docker compose logs -f api` | Follow API logs |
| `docker compose down` | Stop Compose services |

---

## Development Guidelines

When implementing backend functionality:

1. Keep new functionality inside the appropriate feature module.
2. Keep controllers focused on HTTP request/response handling.
3. Place business logic inside service classes.
4. Use DTOs instead of exposing JPA entities directly.
5. Use Jakarta Bean Validation for request validation.
6. Reuse MapStruct for DTO/entity mapping where appropriate.
7. Use centralized exception handling.
8. Use Spring Data repositories for persistence.
9. Apply pagination for potentially large datasets.
10. Apply authorization to sensitive endpoints.
11. Avoid hardcoding environment-specific configuration.
12. Add automated tests for important business rules.
13. Update OpenAPI documentation when contracts change.
14. Update authorization documentation when permissions change.

Recommended module pattern:

```text
feature/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── entity/
├── mapper/
├── repository/
├── service/
└── specification/
```

Follow the existing package convention of the target feature when it differs.

---

## Troubleshooting

### PostgreSQL Connection Failed

Verify:

```env
PGHOST=
PGPORT=
PGDATABASE=
DB_USER=
DB_PASS=
```

Check PostgreSQL:

```bash
pg_isready
```

---

### Redis Connection Failed

Verify:

```env
SPRING_DATA_REDIS_HOST=
SPRING_DATA_REDIS_PORT=
SPRING_DATA_REDIS_PASSWORD=
```

Test Redis:

```bash
redis-cli ping
```

Expected:

```text
PONG
```

---

### `401 Unauthorized`

Verify:

- Keycloak is reachable
- Access token is present
- Token has not expired
- `ISSUER_URI` matches the JWT issuer
- Correct Keycloak realm is being used

Example header:

```http
Authorization: Bearer <ACCESS_TOKEN>
```

---

### `403 Forbidden`

The user is authenticated but does not have the required permission.

Check:

```text
api-docs/endpoints-authorizations.json
api-docs/keycloak-client-role.json
```

---

### MinIO Upload Failed

Verify:

```env
MINIO_ENDPOINT=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
```

Also verify:

- MinIO is reachable
- Required buckets exist
- Credentials have sufficient permissions

---

### Backend Does Not Start

Run:

```bash
./gradlew bootRun --stacktrace
```

If using Docker:

```bash
docker compose logs -f api
```

Check dependency status:

```bash
docker compose ps
```

---

### Port `8080` Already in Use

Linux / macOS:

```bash
lsof -i :8080
```

Windows:

```powershell
netstat -ano | findstr :8080
```

Stop the conflicting application or configure a different server port.

---

## Contributing

1. Create a feature or bug-fix branch.
2. Implement your changes.
3. Run the tests.
4. Verify the application builds.
5. Commit using a clear message.
6. Push your branch.
7. Open a Pull Request.

### Branch Naming

```text
feature/catalog-management
feature/payment-integration
feature/data-migration

fix/order-validation
fix/keycloak-authorization
fix/inventory-calculation

refactor/payment-service
refactor/catalog-mapper

chore/dependency-update
```

### Commit Convention

```text
feat: add inventory adjustment endpoint

fix: correct order status validation

refactor: simplify payment service

test: add catalog service tests

docs: update API documentation

chore: update dependencies
```

---

## Related Applications

The API is part of the larger **FluxiBiz / iPOS ecosystem** and serves multiple client applications:

- iPOS Frontend
- Business Dashboard
- Admin Dashboard
- Storefront
- POS Client
- Customer Display

---


<p align="center">
  <strong>iPOS Backend API</strong>
</p>

<p align="center">
  Built with Java 25, Spring Boot, PostgreSQL, Redis, Keycloak, MinIO and Docker.
</p>
