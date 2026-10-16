# Custodia documental de Carabo

## Controles implementados

- Segmentación física por `businessCode/serie/correlativo` y rechazo de segmentos que intenten escapar del volumen.
- Escrituras atómicas para XML firmado, CDR y PDF.
- Cifrado AES-256-GCM para artefactos nuevos cuando `CARABO_STORAGE_ENCRYPTION_KEY` apunta a un secreto válido.
- Un IV aleatorio y autenticación GCM por escritura; el mismo contenido no genera el mismo ciphertext.
- Sidecar SHA-256 (`.sha256`) verificado antes de descifrar o entregar el archivo.
- Lectura temporal de archivos históricos sin cabecera cifrada para permitir una migración controlada.

La clave debe contener 32 bytes, representados como 64 caracteres hexadecimales o Base64. En DEV se monta como
`secret://storage-encryption-key`; no se guarda en PostgreSQL, imágenes, backups ni repositorio.

## Backup

```bash
./scripts/backup-carabo-dev.sh /ruta/segura/backups
```

El paquete incluye el schema PostgreSQL `carabo`, el volumen de documentos, el volumen de certificados y un
manifiesto `SHA256SUMS`. El secret store se respalda por un canal separado: restaurar los artefactos sin la misma
clave de cifrado los vuelve deliberadamente ilegibles.

## Restore ensayable

```bash
./scripts/restore-carabo-dev.sh /ruta/segura/backups/carabo-AAAAMMDDTHHMMSSZ --confirm
```

El comando valida todos los hashes antes de detener Carabo. `--confirm` es obligatorio porque sustituye únicamente
el schema `carabo` y los volúmenes exactos `tordo_dev_carabo_storage` y `tordo_dev_carabo_certs`. Después se debe
comprobar `/health`, el preflight fiscal y la descarga de un XML/CDR conocido.

## Retención

No existe borrado automático. XML, CDR, representaciones, auditoría y backups permanecen bajo retención indefinida
hasta que Legal/Fiscal apruebe una política por país y ambiente. La futura automatización deberá incluir legal hold,
doble aprobación, inventario previo, registro de destrucción y restauración de muestra; no se admite un `find -mtime -delete`
como política tributaria.
