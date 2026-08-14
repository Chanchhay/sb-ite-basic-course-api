# 🚀 ITE Spring Boot API (`ite-sb-api`)

![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Ready-blue.svg)
![Keycloak](https://img.shields.io/badge/Keycloak-26.0.10-cyan.svg)
![Gradle](https://img.shields.io/badge/Gradle-8.x-02303A.svg)

A high-performance backend REST API service. Powered by **Spring Boot 4.1.0** and **Java 25**, this system provides robust user management, real-time communication, S3-compatible object storage, and native KHQR payment processing integrations.

---

## 🛠️ Technology Stack & Dependencies

### Core Engine & Security
* **Java 25** — Primary programming language runtime (`toolchain: 25`)
* **Spring Boot 4.1.0** — Framework foundation (`webmvc`, `data-jpa`, `validation`)
* **Spring Security OAuth2 Resource Server** — Token validation & API route protection
* **Keycloak Admin Client (`26.0.10`)** — Identity & Access Management (IAM) administration
* **PostgreSQL** — Primary relational database (`org.postgresql:postgresql`)

### Integrations & Services
* **NBC Bakong KHQR SDK (`1.0.0.17`)** — Native Cambodian KHQR payment generation & verification
* **MinIO Java SDK (`9.0.3`)** — S3-compatible object storage for file/media handling
* **ZXing (`3.5.3`)** — QR code and barcode rendering engine (`core` & `javase`)
* **Spring WebSocket** — Bi-directional, real-time messaging
* **Springdoc OpenAPI Scalar (`3.0.3`)** — Interactive API documentation

### Developer Tooling & Build
* **Lombok & MapStruct (`1.6.3`)** — DTO-to-Entity mapping and boilerplate code reduction
* **Gradle** — Build automation tool

---

## 📚 API Documentation & Testing

This repository provides two ways to inspect, test, and debug API endpoints:

### 1. Interactive Scalar Web UI
When the Spring Boot application is running locally, access the interactive Scalar UI directly in your browser to inspect request bodies and test endpoints live with authentication:

* **Scalar Interactive UI:** `http://localhost:8080/scalar/docs` *(or your custom Springdoc Scalar path)*
* **OpenAPI Spec (JSON):** `http://localhost:8080/v3/api-docs`

### 2. Postman Collection
To test endpoints using Postman:
1. Locate or download the collection file at [`./public/postman_collection.json`](./public/postman_collection.json) (or `./postman_collection.json`).
2. Open Postman $\rightarrow$ **Import** $\rightarrow$ **Upload Files**.
3. Select `postman_collection.json`.
4. Configure your environment variables:
   * `baseUrl` = `http://localhost:8080`
   * `bearerToken` = `<your_keycloak_jwt_token>`

---

## ⚙️ Environment Configuration

Copy `.env.example` (or set these system environment variables) before running the application:

| Variable Name | Required | Default / Example | Description |
| :--- | :---: | :--- | :--- |
| `SPRING_DATASOURCE_URL` | **Yes** | `jdbc:postgresql://localhost:5432/ite_db` | PostgreSQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | **Yes** | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | `secret` | Database password |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | **Yes** | `http://localhost:8080/realms/ite-realm` | Keycloak Issuer URI |
| `MINIO_URL` | **Yes** | `http://localhost:9000` | MinIO Server endpoint |
| `MINIO_ACCESS_KEY` | **Yes** | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | **Yes** | `minioadmin` | MinIO secret key |
| `MINIO_BUCKET` | No | `ite-media` | Object storage bucket name |
| `BAKONG_TOKEN` | No | `eyJhbGci...` | NBC Bakong KHQR token |
