# 🦉 Carabo

> Ave fiscalizadora — Servicio de facturación electrónica SUNAT multiempresa.

## Estructura

```
Carabo/
├── apps/
│   ├── api/                          # Backend Quarkus (Java 21 + Gradle)
│   │   ├── carabo-bom/               # BOM de dependencias
│   │   ├── carabo-core/              # Dominio: entidades, enums, UBL builder
│   │   ├── carabo-signer/            # Firma digital XML (certificado .pfx/.p12)
│   │   ├── carabo-ose-client/        # Cliente REST hacia OSE
│   │   ├── carabo-sunat-client/      # Cliente REST/SOAP hacia SUNAT directa
│   │   ├── carabo-storage/           # Almacenamiento de XML, CDR, PDF
│   │   ├── carabo-scheduler/         # Jobs: resumen diario, reintentos
│   │   └── carabo-starter-quarkus/   # App Quarkus (REST + RabbitMQ consumer)
│   └── ui/                           # Frontend Angular
├── infra/
│   └── docker-compose.yml
└── docs/
```

## Requisitos

- Java 21+
- Node.js 22+
- Docker & Docker Compose
- [Tordo](../tordo) levantado (postgres, rabbitmq, kong)

## Desarrollo Local

### Backend
```bash
cd apps/api
./gradlew :carabo-starter-quarkus:quarkusDev
```

### Frontend
```bash
cd apps/ui
npm install && npm start
```

## Arquitectura

- Tordo publica documentos aprobados → RabbitMQ → Carabo consume
- Carabo genera XML UBL 2.1, firma con certificado digital, envía a OSE/SUNAT
- Carabo recibe CDR y notifica resultado a Tordo vía RabbitMQ
- Panel Angular para configurar empresas, certificados, conexiones OSE/SUNAT

## Documentos soportados

| Código | Tipo | Prioridad |
|--------|------|-----------|
| 01 | Factura electrónica | P0 |
| 03 | Boleta electrónica | P0 |
| 07 | Nota de crédito electrónica | P0 |
| 08 | Nota de débito electrónica | P1 |
| 09 | Guía de remisión electrónica | P1 |
| RA | Comunicación de baja | P1 |
| RC | Resumen diario de boletas | P1 |
