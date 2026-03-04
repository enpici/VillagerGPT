# Despliegue de VillagerGPT en servidor Paper

Guía operativa para instalar, configurar, respaldar y recuperar VillagerGPT en producción.

## 1) Instalación en Paper

### Prerrequisitos
- Java 21 instalado en el host.
- Servidor Paper funcionando (directorio raíz con `plugins/`, `logs/`, `paper.jar`, etc.).
- JAR de VillagerGPT compilado o descargado (por ejemplo `VillagerGPT-<version>.jar`).

### Pasos
1. **Detén Paper** para evitar corrupción de archivos durante copia:
   ```bash
   systemctl stop paper
   ```
2. **Copia el plugin** al directorio de plugins:
   ```bash
   cp VillagerGPT-<version>.jar /opt/minecraft/paper/plugins/
   ```
3. **Inicia Paper** para que el plugin genere su estructura inicial:
   ```bash
   systemctl start paper
   ```
4. Verifica en logs que cargó correctamente:
   ```bash
   journalctl -u paper -n 200 --no-pager | rg -i "VillagerGPT|enabled|error|exception"
   ```

> Ajusta rutas y nombre del servicio (`paper`) a tu entorno real.

---

## 2) Configuración: `config.yml`, secretos y validación post-deploy

### Ubicación de `config.yml`
En un servidor Paper estándar:

```text
<PAPER_ROOT>/plugins/VillagerGPT/config.yml
```

Ejemplo típico:

```text
/opt/minecraft/paper/plugins/VillagerGPT/config.yml
```

### Gestión de secretos
`config.yml` contiene datos sensibles (por ejemplo `openai-key`). Recomendado:

1. Mantener un template sin secretos en Git (`config.example.yml`).
2. Inyectar secretos en despliegue con variables de entorno o un secret manager.
3. Limitar permisos de archivo:
   ```bash
   chown minecraft:minecraft /opt/minecraft/paper/plugins/VillagerGPT/config.yml
   chmod 600 /opt/minecraft/paper/plugins/VillagerGPT/config.yml
   ```
4. Si usas backup remoto, cifra backups en reposo y tránsito.

### Validación post-deploy
Checklist mínima después de desplegar o cambiar config:

1. Plugin cargado sin stacktraces en logs.
2. El proveedor configurado (`provider=openai|local`) coincide con el entorno.
3. Si `provider=openai`, la clave existe y no está vacía.
4. Si `provider=local`, `local-model-url` responde HTTP 200.
5. Ejecutar prueba funcional en juego:
   - `/ttv` inicia conversación.
   - `/ttvend` finaliza conversación.
   - Se genera memoria en `memory.db` tras interacción.

---

## 3) Backup y restore de `memory.db` (periodicidad y retención)

### Ubicación esperada
Por defecto, la base SQLite del plugin se encuentra dentro del directorio del plugin (o en la ruta definida en `config.yml`, sección `memory`).

Ruta típica:

```text
<PAPER_ROOT>/plugins/VillagerGPT/memory.db
```

### Política recomendada
- **Periodicidad**: backup cada 6 horas.
- **Retención**:
  - 7 días de copias diarias.
  - 4 semanas de copias semanales.

### Script de backup (ejemplo)
Archivo: `/opt/minecraft/scripts/backup-villagergpt-memory.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

SRC="/opt/minecraft/paper/plugins/VillagerGPT/memory.db"
DST_DIR="/opt/minecraft/backups/villagergpt"
TS="$(date +%F_%H%M%S)"
OUT="${DST_DIR}/memory_${TS}.db.gz"

mkdir -p "$DST_DIR"

# Copia consistente para SQLite sin detener Paper
sqlite3 "$SRC" ".backup '/tmp/memory_${TS}.db'"
gzip -c "/tmp/memory_${TS}.db" > "$OUT"
rm -f "/tmp/memory_${TS}.db"

# Retención: borrar backups de más de 28 días
find "$DST_DIR" -type f -name "memory_*.db.gz" -mtime +28 -delete
```

Programar con `cron`:

```cron
0 */6 * * * /opt/minecraft/scripts/backup-villagergpt-memory.sh >> /var/log/villagergpt-backup.log 2>&1
```

### Procedimiento de restore
1. Detener Paper:
   ```bash
   systemctl stop paper
   ```
2. Respaldar estado actual por seguridad:
   ```bash
   cp /opt/minecraft/paper/plugins/VillagerGPT/memory.db /opt/minecraft/paper/plugins/VillagerGPT/memory.db.pre-restore
   ```
3. Restaurar desde backup:
   ```bash
   gunzip -c /opt/minecraft/backups/villagergpt/memory_<timestamp>.db.gz > /opt/minecraft/paper/plugins/VillagerGPT/memory.db
   ```
4. Corregir permisos:
   ```bash
   chown minecraft:minecraft /opt/minecraft/paper/plugins/VillagerGPT/memory.db
   chmod 600 /opt/minecraft/paper/plugins/VillagerGPT/memory.db
   ```
5. Iniciar Paper y validar conversaciones:
   ```bash
   systemctl start paper
   ```

---

## 4) Rollback a versión anterior del JAR

Mantén siempre al menos **2 versiones previas** del plugin en un repositorio de artefactos o en disco (`/opt/minecraft/artifacts/villagergpt/`).

### Procedimiento
1. Detener Paper:
   ```bash
   systemctl stop paper
   ```
2. Identificar versión actual y objetivo.
3. Retirar JAR actual de `plugins/`:
   ```bash
   mv /opt/minecraft/paper/plugins/VillagerGPT-<actual>.jar /opt/minecraft/paper/plugins/VillagerGPT-<actual>.jar.rollback
   ```
4. Copiar JAR anterior estable:
   ```bash
   cp /opt/minecraft/artifacts/villagergpt/VillagerGPT-<anterior-estable>.jar /opt/minecraft/paper/plugins/
   ```
5. Iniciar Paper:
   ```bash
   systemctl start paper
   ```
6. Validar comandos y logs (`/ttv`, `/ttvend`, errores de carga).

> Si la versión nueva cambió estructura de datos, restaura también `memory.db` desde un backup compatible de la misma ventana temporal.

---

## 5) Checklist de salud tras reinicio del servidor

Ejecuta esta lista después de cada restart (manual o automático):

- [ ] `systemctl status paper` en estado `active (running)`.
- [ ] Sin errores críticos en logs recientes:
  ```bash
  journalctl -u paper -n 300 --no-pager | rg -i "error|exception|villagergpt"
  ```
- [ ] Plugin VillagerGPT cargado correctamente.
- [ ] `config.yml` presente y con permisos correctos (`600`).
- [ ] `memory.db` accesible, con tamaño > 0 y permisos correctos.
- [ ] Comandos funcionales en juego: `/ttv`, `/ttvend`, `/ttvclear`.
- [ ] Prueba de conversación completa (inicio, respuesta, cierre).
- [ ] Verificación de backup reciente (< 6 horas).
- [ ] Si aplica, endpoint de modelo local responde dentro del SLA.

Sugerencia operativa: automatiza esta checklist con un script de smoke test y publícalo en tu canal de alertas (Slack/Discord/Email) tras cada reinicio.
