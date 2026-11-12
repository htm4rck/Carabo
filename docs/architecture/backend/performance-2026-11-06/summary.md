| Escenario | Clientes | N por versión | Mediana anterior → actual | P95 anterior → actual | Cambio mediana | SQL/petición anterior → actual |
|---|---:|---:|---:|---:|---:|---:|
| integrations | 1 | 16 / 16 | 101.3 → 10.6 ms | 203.7 → 19.6 ms | -89.6% | 42 → 2 |
| integrations | 4 | 64 / 64 | 83.7 → 10.1 ms | 139.2 → 13.5 ms | -88.0% | 42 → 2 |
| sales | 1 | 16 / 16 | 8.0 → 8.5 ms | 9.6 → 13.9 ms | +5.7% | 2 → 2 |
| sales | 4 | 64 / 64 | 7.6 → 9.4 ms | 9.1 → 15.6 ms | +22.8% | 2 → 2 |
| documents | 1 | 16 / 16 | 16.6 → 19.3 ms | 20.7 → 26.5 ms | +16.2% | 5 → 4 |
| documents | 4 | 64 / 64 | 15.9 → 16.5 ms | 18.5 → 20.3 ms | +4.1% | 5 → 4 |
| excel_200 | 1 | 8 / 8 | 146.2 → 151.5 ms | 240.3 → 227.8 ms | +3.6% | 2 → 2 |
| excel_3000 | 1 | 8 / 8 | 1684.4 → 1075.3 ms | 2298.0 → 1531.6 ms | -36.2% | 2 → 2 |

P95 calculado sobre las latencias originales agrupadas de ambas rondas. Excel tiene pocas observaciones.
baseline: 256 peticiones medidas, 0 errores, pico RSS 397.7 MiB
current: 256 peticiones medidas, 0 errores, pico RSS 324.9 MiB
