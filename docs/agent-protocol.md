# ABK Agent Protocol v1

Android ABK exposes a localhost-only HTTP API intended to be reached through `adb forward`.

Default host and port:

- Host: `127.0.0.1`
- Port: `48765`

Service start example:

```bash
adb forward tcp:48765 tcp:48765
adb shell am start-foreground-service \
  -a com.abk.kernel.agent.START \
  -n com.abk.kernel/.agent.AbkAgentService \
  --es host 127.0.0.1 \
  --ei port 48765
```

## Read endpoints

- `GET /api/v1/health`
- `GET /api/v1/session`
- `GET /api/v1/runtime`
- `GET /api/v1/root-grants`
- `GET /api/v1/root-grants/{packageName}/icon`
- `GET /api/v1/susfs`
- `GET /api/v1/runtime/modules/{moduleId}/webui/files`
- `GET /api/v1/runtime/modules/{moduleId}/webui/files/{relativePath...}`
- `GET /api/v1/runtime/modules/{moduleId}/webui/module-info`
- `GET /api/v1/tasks/{taskId}`
- `GET /api/v1/tasks/{taskId}/download`

## Write endpoints

- `POST /api/v1/root-grants/{packageName}/allow`
  - Body: `{ "allowed": true|false }`
- `POST /api/v1/susfs/apply`
  - Body: `SusfsConfig` JSON
- `POST /api/v1/runtime/modules/{moduleId}/enable`
  - Body: `{ "enabled": true|false }`
- `POST /api/v1/runtime/modules/{moduleId}/pending-uninstall`
  - Body: `{ "pending": true|false }`
- `POST /api/v1/runtime/modules/{moduleId}/action`
- `POST /api/v1/runtime/modules/{moduleId}/webui/exec`
  - Body: `{ "command": "sh line", "options": { "cwd": "...", "env": { "KEY": "VALUE" } } }`
- `POST /api/v1/runtime/modules/{moduleId}/webui/spawn`
  - Body: `{ "command": "binary", "args": ["--flag"], "options": { "cwd": "...", "env": { "KEY": "VALUE" } } }`
- `POST /api/v1/install/module`
  - Body: `{ "zipPath": "/path/on/device" }`
- `POST /api/v1/install/apk`
  - Body: `{ "apkPath": "/path/on/device" }`
- `POST /api/v1/flash/image`
  - Body: `{ "imagePath": "/path/on/device", "partition": "boot" }`
- `POST /api/v1/diagnostics/export`

## Task model

Long-running writes return HTTP `202 Accepted` with a task snapshot:

```json
{
  "id": "uuid",
  "kind": "diagnostics.export",
  "state": "pending|running|succeeded|failed",
  "message": "optional summary",
  "output": ["streamed log lines"],
  "result": {},
  "downloadName": "optional-file-name.zip",
  "downloadContentType": "application/zip"
}
```

Clients poll `GET /api/v1/tasks/{taskId}` until `state` becomes `succeeded` or `failed`.

If a task exposes a downloadable artifact, the client fetches it from `GET /api/v1/tasks/{taskId}/download`.
