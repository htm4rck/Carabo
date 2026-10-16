# Simulador contractual OSE

Implementa exclusivamente el contrato interno **Carabo OSE Gateway v1**; no simula la API propietaria de un proveedor.

```bash
docker build -t carabo-ose-simulator tools/ose-simulator
docker run --rm -p 18080:8080 carabo-ose-simulator
```

Token: `carabo-ose-simulator`. Permite enviar, consultar estado y descargar CDR. El header opcional
`X-OSE-Simulator-Mode` acepta `ACCEPTED`, `OBSERVED`, `REJECTED`, `PENDING` o `TIMEOUT`.
