mod agent;
mod commands;
mod local_build;
mod local_build_paths;
mod proxy;

use crate::agent::RemoteAgentClient;
use crate::commands::{
    build_adb_detect_command, build_adb_forward_command, build_adb_push_command,
    build_adb_remove_forward_command, build_adb_shell_command, build_adb_start_agent_command,
    build_adb_stop_agent_command, build_cli_command, repo_root, run_command,
};
use crate::local_build::{
    BuildLocalBuildProfileRequest, CreateLocalBuildSourceInstanceRequest,
    LocalBuildArtifactsResponse, LocalBuildBackendsResponse, LocalBuildCatalogResponse,
    LocalBuildLogsResponse, LocalBuildManager, LocalBuildProfile, LocalBuildProfileBuildPlan,
    LocalBuildProfilesResponse, LocalBuildSettings, LocalBuildSourceInstance,
    LocalBuildSourceInstancesResponse, LocalBuildSourceSyncPlan, SaveLocalBuildProfileRequest,
    SyncLocalBuildSourceInstanceRequest, UpdateLocalBuildSettingsRequest,
    InstallLocalBuildBackendRequest, LocalBuildBackendInstallAction,
    LocalBuildBackendInstallPlan, LocalBuildBackendKind,
};
use crate::local_build_paths::{
    load_local_build_path_settings, resolve_local_build_profile_store_dir,
    resolve_local_build_root, resolve_local_build_workspace_dir,
};
use crate::proxy::{normalize_proxy_value, ProxySettings};
use anyhow::{anyhow, Context, Result};
use axum::body::{Body, Bytes};
use axum::extract::{Path, Query, State};
use axum::http::header::{CACHE_CONTROL, CONTENT_TYPE};
use axum::http::{HeaderMap, HeaderValue, Method, Response, StatusCode, Uri};
use axum::response::IntoResponse;
use axum::routing::{get, post};
use axum::{Json, Router};
use futures_util::TryStreamExt;
use mime_guess::MimeGuess;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet, VecDeque};
use std::env;
use std::fs;
use std::net::SocketAddr;
use std::path::{Path as FsPath, PathBuf};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpListener;
use tokio::process::Command as TokioCommand;
use tokio::sync::{mpsc, watch};
use tokio::time::sleep;
use tower_http::cors::{Any, CorsLayer};
use uuid::Uuid;

const DEFAULT_SIDECAR_HOST: &str = "127.0.0.1";
const DEFAULT_SIDECAR_PORT: u16 = 38765;
const DEFAULT_AGENT_HOST: &str = "127.0.0.1";
const DEFAULT_AGENT_PORT: u16 = 48765;
const SIDELOAD_DIR: &str = "/data/local/tmp/abk-desktop";
const CLI_CONFIG_PATH_SUFFIX: &str = ".config/abk/config.json";
const GITHUB_OAUTH_DEVICE_URL: &str = "https://github.com/login/device/code";
const GITHUB_OAUTH_TOKEN_URL: &str = "https://github.com/login/oauth/access_token";
const GITHUB_CLIENT_ID_FALLBACK: &str = "Ov23li8skGo6AFPBeSTh";
const MAX_LOG_LINES: usize = 500;
const MAX_TASKS: usize = 64;
const BUILD_TRACK_RUN_LIMIT: usize = 50;
const BUILD_DISCOVERY_POLL_INTERVAL: Duration = Duration::from_secs(3);
const BUILD_COMPLETION_POLL_INTERVAL: Duration = Duration::from_secs(10);
const BUILD_DISCOVERY_TIMEOUT: Duration = Duration::from_secs(120);
const MAX_TASK_OUTPUT_LINES: usize = 4000;

#[derive(Clone)]
struct AppState {
    inner: Arc<InnerState>,
}

struct InnerState {
    agent: RemoteAgentClient,
    connection: RwLock<ConnectionState>,
    logs: Mutex<VecDeque<LogEntry>>,
    tasks: Mutex<HashMap<String, LocalTask>>,
    task_order: Mutex<VecDeque<String>>,
    task_cancellers: Mutex<HashMap<String, watch::Sender<bool>>>,
    local_build: Mutex<LocalBuildManager>,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
struct ConnectionState {
    serial: Option<String>,
    agent_host: String,
    agent_port: u16,
    connected: bool,
    mode: ConnectionMode,
    last_error: Option<String>,
    last_detected: Vec<DetectedDevice>,
    last_detect_raw: String,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase")]
enum ConnectionMode {
    #[default]
    Disconnected,
    Abk,
    AdbFallback,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DetectedDevice {
    serial: String,
    status: String,
    detail: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct LogEntry {
    id: String,
    timestamp_ms: u64,
    scope: String,
    level: String,
    message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TaskSnapshot {
    id: String,
    kind: String,
    state: String,
    message: Option<String>,
    #[serde(default)]
    output: Vec<String>,
    #[serde(default)]
    result: Value,
    download_name: Option<String>,
    download_content_type: Option<String>,
}

#[derive(Debug, Clone)]
struct LocalTask {
    snapshot: TaskSnapshot,
    download_path: Option<PathBuf>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConnectRequest {
    serial: Option<String>,
    port: Option<u16>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstallModuleRequest {
    zip_path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct KernelFeatureRequest {
    enabled: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstallApkRequest {
    apk_path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FlashImageRequest {
    image_path: String,
    partition: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CliRunRequest {
    args: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GitHubLoginPollRequest {
    device_code: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DownloadDirRequest {
    path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProxySettingsRequest {
    http_proxy: Option<String>,
    https_proxy: Option<String>,
    all_proxy: Option<String>,
    no_proxy: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct BuildGkiRequest {
    target: String,
    ksu_variant: Option<String>,
    ksu_branch: Option<String>,
    version: Option<String>,
    revision: Option<String>,
    custom_ref: Option<String>,
    build_time: Option<String>,
    custom_modules: Option<String>,
    kpm_password: Option<String>,
    virt: Option<String>,
    zram: bool,
    bbg: bool,
    ddk: bool,
    kpm: bool,
    susfs: bool,
    rekernel: bool,
    ntsync: bool,
    networking: bool,
    zram_full_algo: bool,
    zram_extra_algos: Option<String>,
    android_version: Option<String>,
    kernel_version: Option<String>,
    sub_level: Option<String>,
    os_patch_level: Option<String>,
    force: Option<bool>,
}

#[derive(Debug, Deserialize)]
struct BuildRunsQuery {
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ArtifactDownloadRequest {
    artifact_id: u64,
    output_dir: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LocalBuildInitRequest {
    android_version: String,
    kernel_version: String,
    branch_month: String,
    force: Option<bool>,
    skip_deps: Option<bool>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LocalBuildRebuildRequest {
    clean_out: Option<bool>,
    reseed: Option<bool>,
    no_package: Option<bool>,
    build: Option<BuildGkiRequest>,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LocalBuildTemplate {
    name: String,
    android_version: String,
    kernel_version: String,
    template_path: String,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LocalBuildStatus {
    available: bool,
    script_root: String,
    init_script_path: String,
    rebuild_script_path: String,
    env_file_path: String,
    state_dir: String,
    sources_dir: String,
    workspace_dir: String,
    artifacts_dir: String,
    logs_dir: String,
    cache_dir: String,
    kernel_root: String,
    has_env_file: bool,
    workspace_ready: bool,
    template_root: Option<String>,
    template_name: Option<String>,
    template_android_version: Option<String>,
    template_kernel_version: Option<String>,
    sub_level: Option<String>,
    os_patch_level: Option<String>,
    template_branch: Option<String>,
    template_common_branch: Option<String>,
    branch_month: Option<String>,
    custom_external_modules_root: Option<String>,
    custom_external_modules_manifest: Option<String>,
    latest_log_path: Option<String>,
    supported_templates: Vec<LocalBuildTemplate>,
}

#[derive(Debug, Deserialize)]
struct LogQuery {
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
struct PackageQuery {
    r#type: Option<String>,
}

#[derive(Debug, Deserialize)]
struct AgentPortQuery {
    port: Option<u16>,
}

#[derive(Debug)]
struct ApiError {
    status: StatusCode,
    message: String,
}

impl ApiError {
    fn bad_request(message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::BAD_REQUEST,
            message: message.into(),
        }
    }

    fn service_unavailable(message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::SERVICE_UNAVAILABLE,
            message: message.into(),
        }
    }

    fn internal(error: anyhow::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            message: error.to_string(),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response<Body> {
        let body = Json(json!({ "error": self.message }));
        (self.status, body).into_response()
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(value: anyhow::Error) -> Self {
        ApiError::internal(value)
    }
}

impl From<serde_json::Error> for ApiError {
    fn from(value: serde_json::Error) -> Self {
        ApiError::internal(anyhow::Error::new(value))
    }
}

impl AppState {
    fn new(repo_root: PathBuf) -> Result<Self> {
        if let Ok(path) = cli_config_path() {
            if let Ok(proxy_settings) = read_proxy_settings_from_path(&path) {
                proxy_settings.apply_to_process_env();
            }
        }
        Ok(Self {
            inner: Arc::new(InnerState {
                agent: RemoteAgentClient::new()?,
                connection: RwLock::new(ConnectionState {
                    serial: None,
                    agent_host: DEFAULT_AGENT_HOST.into(),
                    agent_port: DEFAULT_AGENT_PORT,
                    connected: false,
                    mode: ConnectionMode::Disconnected,
                    last_error: None,
                    last_detected: Vec::new(),
                    last_detect_raw: String::new(),
                }),
                logs: Mutex::new(VecDeque::new()),
                tasks: Mutex::new(HashMap::new()),
                task_order: Mutex::new(VecDeque::new()),
                task_cancellers: Mutex::new(HashMap::new()),
                local_build: Mutex::new(LocalBuildManager::new(repo_root)?),
            }),
        })
    }

    fn log(&self, scope: &str, level: &str, message: impl Into<String>) {
        let message = message.into();
        let entry = LogEntry {
            id: Uuid::new_v4().to_string(),
            timestamp_ms: now_ms(),
            scope: scope.into(),
            level: level.into(),
            message,
        };
        let mut logs = self.inner.logs.lock().expect("logs");
        logs.push_back(entry);
        while logs.len() > MAX_LOG_LINES {
            logs.pop_front();
        }
    }

    fn connection(&self) -> ConnectionState {
        self.inner.connection.read().expect("connection").clone()
    }

    fn update_connection(&self, update: impl FnOnce(&mut ConnectionState)) {
        let mut connection = self.inner.connection.write().expect("connection");
        update(&mut connection);
    }

    fn recent_logs(&self, limit: usize) -> Vec<LogEntry> {
        let logs = self.inner.logs.lock().expect("logs");
        logs.iter()
            .rev()
            .take(limit)
            .cloned()
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect()
    }

    fn upsert_task(&self, task: LocalTask) {
        let id = task.snapshot.id.clone();
        let mut tasks = self.inner.tasks.lock().expect("tasks");
        let mut order = self.inner.task_order.lock().expect("task_order");
        if !tasks.contains_key(&id) {
            order.push_back(id.clone());
        }
        tasks.insert(id.clone(), task);
        while order.len() > MAX_TASKS {
            if let Some(oldest) = order.pop_front() {
                tasks.remove(&oldest);
            }
        }
    }

    fn get_local_task(&self, task_id: &str) -> Option<LocalTask> {
        self.inner
            .tasks
            .lock()
            .expect("tasks")
            .get(task_id)
            .cloned()
    }

    fn register_task_canceller(&self, task_id: &str) -> watch::Receiver<bool> {
        let (sender, receiver) = watch::channel(false);
        self.inner
            .task_cancellers
            .lock()
            .expect("task_cancellers")
            .insert(task_id.to_string(), sender);
        receiver
    }

    fn clear_task_canceller(&self, task_id: &str) {
        self.inner
            .task_cancellers
            .lock()
            .expect("task_cancellers")
            .remove(task_id);
    }

    fn request_task_cancel(&self, task_id: &str) -> Option<TaskSnapshot> {
        let sender = self
            .inner
            .task_cancellers
            .lock()
            .expect("task_cancellers")
            .get(task_id)
            .cloned();
        let mut task = self.get_local_task(task_id)?;
        if let Some(sender) = sender {
            let _ = sender.send(true);
            if task.snapshot.state == "pending" || task.snapshot.state == "running" {
                task.snapshot.message = Some("cancellation requested".into());
                let mut result = task.snapshot.result.clone();
                if let Value::Object(ref mut object) = result {
                    object.insert("cancelRequested".into(), Value::Bool(true));
                }
                task.snapshot.result = result;
                self.upsert_task(LocalTask {
                    snapshot: task.snapshot.clone(),
                    download_path: task.download_path.clone(),
                });
            }
        }
        Some(task.snapshot)
    }

    fn read_local_build<T>(&self, reader: impl FnOnce(&LocalBuildManager) -> T) -> T {
        let guard = self.inner.local_build.lock().expect("local_build");
        reader(&guard)
    }

    fn write_local_build<T>(
        &self,
        writer: impl FnOnce(&mut LocalBuildManager) -> Result<T>,
    ) -> Result<T> {
        let mut guard = self.inner.local_build.lock().expect("local_build");
        writer(&mut guard)
    }

    fn base_agent_url(&self) -> Result<String> {
        let connection = self.connection();
        if !connection.connected {
            return Err(anyhow!("device service not connected"));
        }
        Ok(format!(
            "http://{}:{}",
            connection.agent_host, connection.agent_port
        ))
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let host = env::var("ABK_DESKTOP_HOST").unwrap_or_else(|_| DEFAULT_SIDECAR_HOST.into());
    let port = env::args()
        .skip(1)
        .collect::<Vec<_>>()
        .windows(2)
        .find_map(|pair| {
            if pair[0] == "--port" {
                pair[1].parse::<u16>().ok()
            } else {
                None
            }
        })
        .unwrap_or(DEFAULT_SIDECAR_PORT);
    let state = AppState::new(repo_root())?;
    state.log(
        "sidecar",
        "info",
        format!("starting ABK desktop sidecar on {host}:{port}"),
    );

    let app = Router::new()
        .route("/api/v1/health", get(local_health))
        .route("/api/v1/device", get(get_device_state))
        .route("/api/v1/device/detect", post(detect_devices))
        .route("/api/v1/device/connect", post(connect_device))
        .route("/api/v1/device/disconnect", post(disconnect_device))
        .route("/api/v1/cli/run", post(run_cli_task))
        .route("/api/v1/logs", get(get_logs))
        .route("/api/v1/github/session", get(get_github_session))
        .route("/api/v1/github/login/start", post(start_github_login))
        .route("/api/v1/github/login/poll", post(poll_github_login))
        .route("/api/v1/github/fork/ensure", post(ensure_github_fork))
        .route("/api/v1/github/fork/sync", post(sync_github_fork))
        .route("/api/v1/github/logout", post(logout_github))
        .route(
            "/api/v1/settings/proxy",
            get(get_proxy_settings).post(save_proxy_settings),
        )
        .route("/api/v1/settings/download-dir", post(set_download_dir))
        .route("/api/v1/builds/gki", post(start_gki_build))
        .route("/api/v1/builds/runs", get(list_build_runs))
        .route("/api/v1/builds/runs/{run_id}", get(get_build_run))
        .route(
            "/api/v1/local-build/backends",
            get(list_local_build_backends),
        )
        .route(
            "/api/v1/local-build/backends/{backend_kind}/install",
            post(install_local_build_backend),
        )
        .route("/api/v1/local-build/catalog", get(get_local_build_catalog))
        .route(
            "/api/v1/local-build/settings",
            post(update_local_build_settings),
        )
        .route(
            "/api/v1/local-build/source-instances",
            get(list_local_build_source_instances).post(create_local_build_source_instance),
        )
        .route(
            "/api/v1/local-build/source-instances/{source_instance_id}/sync",
            post(sync_local_build_source_instance),
        )
        .route(
            "/api/v1/local-build/profiles",
            get(list_local_build_profiles).post(save_local_build_profile),
        )
        .route(
            "/api/v1/local-build/profiles/{profile_id}/build",
            post(build_local_build_profile),
        )
        .route(
            "/api/v1/local-build/tasks/{task_id}",
            get(get_local_build_task),
        )
        .route(
            "/api/v1/local-build/artifacts",
            get(list_local_build_artifacts),
        )
        .route("/api/v1/local-build/logs", get(list_local_build_logs))
        .route("/api/v1/local-build/status", get(get_local_build_status))
        .route("/api/v1/local-build/init", post(start_local_build_init))
        .route(
            "/api/v1/local-build/rebuild",
            post(start_local_build_rebuild),
        )
        .route(
            "/api/v1/builds/runs/{run_id}/artifacts",
            get(list_build_run_artifacts),
        )
        .route(
            "/api/v1/builds/runs/{run_id}/artifacts/download",
            post(download_build_artifact),
        )
        .route("/api/v1/session", get(proxy_session))
        .route("/api/v1/runtime", get(proxy_runtime))
        .route("/api/v1/root-grants", get(proxy_root_grants))
        .route("/api/v1/kernel-features", get(proxy_kernel_features))
        .route("/api/v1/packages", get(proxy_packages))
        .route("/api/v1/packages/info", post(proxy_package_info))
        .route(
            "/api/v1/root-grants/{package_name}/allow",
            post(proxy_root_grant_allow),
        )
        .route(
            "/api/v1/kernel-features/{feature_id}",
            post(proxy_kernel_feature_set),
        )
        .route(
            "/api/v1/root-grants/{package_name}/icon",
            get(proxy_root_grant_icon),
        )
        .route("/api/v1/susfs", get(proxy_susfs))
        .route("/api/v1/susfs/apply", post(proxy_susfs_apply))
        .route(
            "/api/v1/runtime/modules/{module_id}/enable",
            post(proxy_module_enable),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/pending-uninstall",
            post(proxy_module_pending_uninstall),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/action",
            post(proxy_module_action),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/module-info",
            get(proxy_module_webui_module_info),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/exec",
            post(proxy_module_webui_exec),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/spawn",
            post(proxy_module_webui_spawn),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/http-proxy",
            get(proxy_module_webui_http_proxy).post(proxy_module_webui_http_proxy),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/files",
            get(proxy_module_webui_root),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/files/{*relative_path}",
            get(proxy_module_webui_file),
        )
        .route("/api/v1/install/module", post(proxy_install_module))
        .route("/api/v1/install/apk", post(proxy_install_apk))
        .route("/api/v1/flash/image", post(proxy_flash_image))
        .route("/api/v1/diagnostics/export", post(proxy_export_diagnostics))
        .route("/api/v1/tasks/{task_id}", get(get_task))
        .route("/api/v1/tasks/{task_id}/cancel", post(cancel_task))
        .route("/api/v1/tasks/{task_id}/download", get(download_task_file))
        .route("/internal/insets.css", get(insets_css))
        .fallback(proxy_webui_root_asset_fallback)
        .with_state(state.clone())
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_headers(Any)
                .allow_methods(Any),
        );

    let addr = SocketAddr::from((host.parse::<std::net::IpAddr>()?, port));
    let listener = TcpListener::bind(addr).await?;
    println!("ABK desktop sidecar listening on http://{host}:{port}");
    axum::serve(listener, app).await?;
    Ok(())
}

async fn local_health(
    State(state): State<AppState>,
    Query(query): Query<AgentPortQuery>,
) -> Result<Json<Value>, ApiError> {
    let connection = state.connection();
    let port = query.port.unwrap_or(connection.agent_port);
    let agent_health = if connection.connected {
        state
            .inner
            .agent
            .get_json(
                &format!("http://{}:{}", connection.agent_host, port),
                "/api/v1/health",
            )
            .await
            .ok()
    } else {
        None
    };
    Ok(Json(json!({
        "status": "ok",
        "protocolVersion": "abk-desktop-sidecar-v1",
        "sidecar": {
            "host": DEFAULT_SIDECAR_HOST,
            "port": DEFAULT_SIDECAR_PORT,
        },
        "device": connection,
        "agent": agent_health,
    })))
}

async fn get_device_state(State(state): State<AppState>) -> Json<ConnectionState> {
    Json(state.connection())
}

async fn detect_devices(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    let spec = build_adb_detect_command();
    let output = tokio::task::spawn_blocking(move || run_command(&spec))
        .await
        .context("adb detect task failed")?
        .map_err(ApiError::from)?;
    let devices = parse_detected_devices(&output);
    state.update_connection(|connection| {
        connection.last_detect_raw = output.clone();
        connection.last_detected = devices.clone();
        connection.last_error = None;
        reconcile_connection_after_detect(connection, &devices);
    });
    state.log(
        "device.detect",
        "info",
        format!("detected {} adb device(s)", devices.len()),
    );
    Ok(Json(json!({
        "devices": devices,
        "raw": output,
    })))
}

async fn connect_device(
    State(state): State<AppState>,
    Json(request): Json<ConnectRequest>,
) -> Result<Json<Value>, ApiError> {
    let serial = match resolve_connect_serial(request.serial.as_deref(), &state.connection()) {
        Ok(serial) => serial,
        Err(error) => {
            let message = error.message.clone();
            state.update_connection(|connection| {
                connection.connected = false;
                connection.mode = ConnectionMode::Disconnected;
                connection.last_error = Some(message.clone());
            });
            return Err(error);
        }
    };
    let port = request.port.unwrap_or(DEFAULT_AGENT_PORT);

    let forward = build_adb_forward_command(&serial, port);
    let start = build_adb_start_agent_command(&serial, port);
    state.update_connection(|connection| {
        connection.serial = Some(serial.clone());
        connection.agent_port = port;
        connection.agent_host = DEFAULT_AGENT_HOST.into();
        connection.connected = false;
        connection.last_error = None;
    });

    let result = async {
        run_blocking_command(forward).await?;
        run_blocking_command(start).await?;
        state.log(
            "device.connect",
            "info",
            format!("started phone agent on port {port} for {serial}"),
        );

        wait_for_agent(
            &state,
            &format!("http://{}:{}", DEFAULT_AGENT_HOST, port),
            Duration::from_secs(20),
        )
        .await?;

        state
            .inner
            .agent
            .get_json(
                &format!("http://{}:{}", DEFAULT_AGENT_HOST, port),
                "/api/v1/health",
            )
            .await
            .map_err(ApiError::from)
    }
    .await;

    match result {
        Ok(health) => {
            state.update_connection(|connection| {
                connection.connected = true;
                connection.mode = ConnectionMode::Abk;
                connection.last_error = None;
            });
            Ok(Json(json!({
                "connected": true,
                "mode": ConnectionMode::Abk,
                "device": state.connection(),
                "agent": health,
            })))
        }
        Err(error) => {
            let message = error.message.clone();
            run_blocking_command(build_adb_stop_agent_command(&serial))
                .await
                .ok();
            run_blocking_command(build_adb_remove_forward_command(&serial, port))
                .await
                .ok();
            state.update_connection(|connection| {
                connection.connected = false;
                connection.mode = ConnectionMode::AdbFallback;
                connection.last_error = Some(message.clone());
            });
            state.log(
                "device.connect",
                "error",
                format!("failed to establish ABK agent for {serial}: {message}"),
            );
            Err(error)
        }
    }
}

async fn disconnect_device(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    let connection = state.connection();
    let serial = connection.serial.unwrap_or_default();
    run_blocking_command(build_adb_stop_agent_command(&serial))
        .await
        .ok();
    run_blocking_command(build_adb_remove_forward_command(
        &serial,
        connection.agent_port,
    ))
    .await
    .ok();
    state.update_connection(|current| {
        current.serial = None;
        current.connected = false;
        current.mode = ConnectionMode::Disconnected;
        current.last_error = None;
    });
    state.log(
        "device.disconnect",
        "info",
        "stopped forwarded agent session",
    );
    Ok(Json(json!({
        "connected": false,
        "device": state.connection(),
    })))
}

async fn run_cli_task(
    State(state): State<AppState>,
    Json(request): Json<CliRunRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let spec = build_cli_command(&request.args).map_err(ApiError::from)?;
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "cli.run".into(),
        state: "pending".into(),
        message: Some("cli command accepted".into()),
        output: Vec::new(),
        result: json!({ "args": request.args }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });
    state.log("builds", "info", format!("queued CLI task {id}"));

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("cli command running".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });
        let output = tokio::task::spawn_blocking(move || run_command(&spec)).await;
        match output {
            Ok(Ok(text)) => {
                let lines = split_lines(&text);
                task_state.log("builds", "info", format!("CLI task {id} succeeded"));
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "succeeded".into(),
                        message: Some("cli command completed".into()),
                        output: lines.clone(),
                        result: json!({ "stdout": text }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.log(
                    "builds",
                    "error",
                    format!("CLI task {id} failed: {message}"),
                );
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("cli command failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.log(
                    "builds",
                    "error",
                    format!("CLI task {id} join failure: {message}"),
                );
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("cli task join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn get_logs(State(state): State<AppState>, Query(query): Query<LogQuery>) -> Json<Value> {
    let limit = query.limit.unwrap_or(200).clamp(1, MAX_LOG_LINES);
    Json(json!({ "logs": state.recent_logs(limit) }))
}

async fn get_github_session() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "whoami".into()])
        .await
        .map(Json)
}

async fn start_github_login() -> Result<Json<Value>, ApiError> {
    let client_id = cli_client_id().await?;
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("failed to build github client")?;
    let response = http
        .post(GITHUB_OAUTH_DEVICE_URL)
        .header("Accept", "application/json")
        .header("User-Agent", "ABK-Desktop")
        .form(&[
            ("client_id", client_id.as_str()),
            ("scope", "repo workflow"),
        ])
        .send()
        .await
        .context("failed to request github device code")?;
    let status = response.status();
    let body = response
        .text()
        .await
        .context("failed to read github device response")?;
    if !status.is_success() {
        return Err(ApiError::service_unavailable(body));
    }
    let value: Value = serde_json::from_str(&body)?;
    Ok(Json(json!({
        "deviceCode": value.get("device_code").and_then(Value::as_str).unwrap_or_default(),
        "userCode": value.get("user_code").and_then(Value::as_str).unwrap_or_default(),
        "verificationUri": value.get("verification_uri").and_then(Value::as_str).unwrap_or_default(),
        "verificationUriComplete": value.get("verification_uri_complete").and_then(Value::as_str),
        "expiresIn": value.get("expires_in").and_then(Value::as_u64).unwrap_or(900),
        "interval": value.get("interval").and_then(Value::as_u64).unwrap_or(5),
    })))
}

async fn poll_github_login(
    Json(request): Json<GitHubLoginPollRequest>,
) -> Result<Json<Value>, ApiError> {
    let client_id = cli_client_id().await?;
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("failed to build github client")?;
    let response = http
        .post(GITHUB_OAUTH_TOKEN_URL)
        .header("Accept", "application/json")
        .header("User-Agent", "ABK-Desktop")
        .form(&[
            ("client_id", client_id.as_str()),
            ("device_code", request.device_code.as_str()),
            ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
        ])
        .send()
        .await
        .context("failed to poll github login")?;
    let status = response.status();
    let body = response
        .text()
        .await
        .context("failed to read github login response")?;
    if !status.is_success() {
        return Err(ApiError::service_unavailable(body));
    }
    let value: Value = serde_json::from_str(&body)?;
    if let Some(token) = value.get("access_token").and_then(Value::as_str) {
        persist_cli_token(token).await?;
        let session = run_cli_json_command(vec!["--json".into(), "whoami".into()])
            .await
            .unwrap_or_else(|_| json!({"ok": true, "loggedIn": true}));
        return Ok(Json(json!({"state": "authorized", "session": session})));
    }
    let error = value
        .get("error")
        .and_then(Value::as_str)
        .unwrap_or("unknown");
    Ok(Json(json!({
        "state": error,
        "interval": value.get("interval").and_then(Value::as_u64).unwrap_or(5),
        "error": value.get("error_description").and_then(Value::as_str),
    })))
}

async fn ensure_github_fork() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "fork".into(), "--no-sync".into()]).await?;
    get_github_session().await
}

async fn sync_github_fork() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "sync".into()]).await?;
    get_github_session().await
}

async fn logout_github() -> Result<Json<Value>, ApiError> {
    clear_cli_token().await?;
    get_github_session().await
}

async fn get_proxy_settings() -> Result<Json<Value>, ApiError> {
    let path = cli_config_path()?;
    let settings = read_proxy_settings_from_path(&path)?;
    Ok(Json(
        serde_json::to_value(settings).map_err(ApiError::from)?,
    ))
}

async fn save_proxy_settings(
    Json(request): Json<ProxySettingsRequest>,
) -> Result<Json<Value>, ApiError> {
    let path = cli_config_path()?;
    let settings = ProxySettings {
        http_proxy: normalize_proxy_value(request.http_proxy),
        https_proxy: normalize_proxy_value(request.https_proxy),
        all_proxy: normalize_proxy_value(request.all_proxy),
        no_proxy: normalize_proxy_value(request.no_proxy),
    };
    persist_proxy_settings_to_path(&path, &settings)?;
    settings.apply_to_process_env();
    Ok(Json(
        serde_json::to_value(settings).map_err(ApiError::from)?,
    ))
}

async fn set_download_dir(
    Json(request): Json<DownloadDirRequest>,
) -> Result<Json<Value>, ApiError> {
    let path = request.path.trim();
    if path.is_empty() {
        return Err(ApiError::bad_request("download directory path missing"));
    }
    run_cli_json_command(vec![
        "--json".into(),
        "artifacts".into(),
        "--set-download-dir".into(),
        path.into(),
    ])
    .await
    .map(Json)
}

async fn list_build_runs(Query(query): Query<BuildRunsQuery>) -> Result<Json<Value>, ApiError> {
    let limit = query.limit.unwrap_or(10);
    run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--limit".into(),
        limit.to_string(),
    ])
    .await
    .map(Json)
}

async fn get_build_run(Path(run_id): Path<u64>) -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--run-id".into(),
        run_id.to_string(),
    ])
    .await
    .map(Json)
}

async fn list_build_run_artifacts(Path(run_id): Path<u64>) -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec![
        "--json".into(),
        "artifacts".into(),
        "--run-id".into(),
        run_id.to_string(),
    ])
    .await
    .map(Json)
}

async fn list_local_build_backends(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildBackendsResponse>, ApiError> {
    Ok(Json(
        state.read_local_build(LocalBuildManager::list_backends),
    ))
}

async fn install_local_build_backend(
    State(state): State<AppState>,
    Path(backend_kind): Path<String>,
    Json(request): Json<InstallLocalBuildBackendRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let backend_kind = parse_local_build_backend_kind(&backend_kind)?;
    let plan = state
        .write_local_build(|manager| manager.plan_backend_install(backend_kind, &request))
        .map_err(ApiError::from)?;
    Ok(queue_local_build_backend_install_task(
        state,
        plan,
        "local.backend.install",
        "local backend install accepted",
        "installing local backend asset",
        "local backend asset installed",
        "local backend asset install failed",
    ))
}

async fn get_local_build_catalog(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildCatalogResponse>, ApiError> {
    Ok(Json(state.read_local_build(LocalBuildManager::catalog)))
}

async fn update_local_build_settings(
    State(state): State<AppState>,
    Json(request): Json<UpdateLocalBuildSettingsRequest>,
) -> Result<Json<LocalBuildSettings>, ApiError> {
    let settings = state
        .write_local_build(|manager| manager.update_settings(request))
        .map_err(ApiError::from)?;
    Ok(Json(settings))
}

async fn list_local_build_source_instances(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildSourceInstancesResponse>, ApiError> {
    Ok(Json(state.read_local_build(
        LocalBuildManager::list_source_instances,
    )))
}

async fn create_local_build_source_instance(
    State(state): State<AppState>,
    Json(request): Json<CreateLocalBuildSourceInstanceRequest>,
) -> Result<(StatusCode, Json<LocalBuildSourceInstance>), ApiError> {
    let source_instance = state
        .write_local_build(|manager| manager.create_source_instance(request))
        .map_err(ApiError::from)?;
    Ok((StatusCode::CREATED, Json(source_instance)))
}

async fn sync_local_build_source_instance(
    State(state): State<AppState>,
    Path(source_instance_id): Path<String>,
    Json(request): Json<SyncLocalBuildSourceInstanceRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let plan = state
        .write_local_build(|manager| manager.plan_source_sync(&source_instance_id, &request))
        .map_err(ApiError::from)?;
    Ok(queue_local_build_source_sync_task(
        state,
        plan,
        "local.build.source.sync",
        "local build source sync accepted",
        "syncing local source instance",
        "local source instance synced",
        "local source sync failed",
    ))
}

async fn list_local_build_profiles(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildProfilesResponse>, ApiError> {
    Ok(Json(
        state.read_local_build(LocalBuildManager::list_profiles),
    ))
}

async fn save_local_build_profile(
    State(state): State<AppState>,
    Json(request): Json<SaveLocalBuildProfileRequest>,
) -> Result<Json<LocalBuildProfile>, ApiError> {
    let profile = state
        .write_local_build(|manager| manager.save_profile(request))
        .map_err(ApiError::from)?;
    Ok(Json(profile))
}

async fn build_local_build_profile(
    State(state): State<AppState>,
    Path(profile_id): Path<String>,
    Json(request): Json<BuildLocalBuildProfileRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let plan = state
        .write_local_build(|manager| manager.plan_profile_build(&profile_id, &request))
        .map_err(ApiError::from)?;
    Ok(queue_local_build_profile_build_task(
        state,
        plan,
        request,
        "local.build.profile.build",
        "local build profile accepted",
        "running local build profile",
        "local build profile finished",
        "local build profile failed",
    ))
}

async fn get_local_build_task(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Json<TaskSnapshot>, ApiError> {
    let task = state
        .get_local_task(&task_id)
        .ok_or_else(|| ApiError::bad_request(format!("unknown task {task_id}")))?;
    Ok(Json(task.snapshot))
}

async fn list_local_build_artifacts(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildArtifactsResponse>, ApiError> {
    Ok(Json(
        state.read_local_build(LocalBuildManager::list_artifacts),
    ))
}

async fn list_local_build_logs(
    State(state): State<AppState>,
) -> Result<Json<LocalBuildLogsResponse>, ApiError> {
    Ok(Json(state.read_local_build(LocalBuildManager::list_logs)))
}

async fn get_local_build_status() -> Result<Json<Value>, ApiError> {
    let status = tokio::task::spawn_blocking(inspect_local_build_status)
        .await
        .context("local build status join failure")
        .map_err(ApiError::from)?
        .map_err(ApiError::from)?;
    Ok(Json(serde_json::to_value(status).map_err(ApiError::from)?))
}

async fn start_local_build_init(
    State(state): State<AppState>,
    Json(request): Json<LocalBuildInitRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let android_version = request.android_version.trim();
    let kernel_version = request.kernel_version.trim();
    let branch_month = request.branch_month.trim();
    if android_version.is_empty() || kernel_version.is_empty() || branch_month.is_empty() {
        return Err(ApiError::bad_request(
            "androidVersion, kernelVersion, and branchMonth are required",
        ));
    }
    let source_instance = state
        .write_local_build(|manager| {
            manager.ensure_legacy_source_instance(android_version, kernel_version, branch_month)
        })
        .map_err(ApiError::from)?;
    let plan = state
        .write_local_build(|manager| {
            manager.plan_source_sync(
                &source_instance.id,
                &SyncLocalBuildSourceInstanceRequest {
                    backend_kind: Some(crate::local_build::LocalBuildBackendKind::Script),
                    force: request.force,
                    skip_deps: request.skip_deps,
                    sudo_password: None,
                },
            )
        })
        .map_err(ApiError::from)?;
    Ok(queue_local_build_source_sync_task(
        state,
        plan,
        "local.build.init",
        "local build init accepted",
        "initializing local AOSP workspace",
        "local build workspace initialized",
        "local build init failed",
    ))
}

async fn start_local_build_rebuild(
    State(state): State<AppState>,
    Json(request): Json<LocalBuildRebuildRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let status = inspect_local_build_status().map_err(ApiError::from)?;
    if !status.available {
        return Err(ApiError::service_unavailable(format!(
            "local build scripts are unavailable under {}",
            status.script_root
        )));
    }
    if !status.has_env_file {
        return Err(ApiError::bad_request(
            "local build environment is not initialized yet",
        ));
    }
    let android_version = status.template_android_version.clone().ok_or_else(|| {
        ApiError::bad_request("current local build environment is missing Android version")
    })?;
    let kernel_version = status.template_kernel_version.clone().ok_or_else(|| {
        ApiError::bad_request("current local build environment is missing kernel version")
    })?;
    let branch_month = status.branch_month.clone().ok_or_else(|| {
        ApiError::bad_request("current local build environment is missing branch month")
    })?;
    let source_instance = state
        .write_local_build(|manager| {
            manager.ensure_legacy_source_instance(&android_version, &kernel_version, &branch_month)
        })
        .map_err(ApiError::from)?;
    let profile = state
        .write_local_build(|manager| {
            manager.ensure_legacy_profile(&source_instance.id, request.build.clone())
        })
        .map_err(ApiError::from)?;
    let plan = state
        .write_local_build(|manager| {
            manager.plan_profile_build(
                &profile.id,
                &BuildLocalBuildProfileRequest {
                    clean_out: request.clean_out,
                    reseed: request.reseed,
                    no_package: request.no_package,
                    sudo_password: None,
                },
            )
        })
        .map_err(ApiError::from)?;
    Ok(queue_local_build_profile_build_task(
        state,
        plan,
        BuildLocalBuildProfileRequest {
            clean_out: request.clean_out,
            reseed: request.reseed,
            no_package: request.no_package,
            sudo_password: None,
        },
        "local.build.rebuild",
        "local rebuild accepted",
        "local kernel rebuild running",
        "local kernel rebuild finished",
        "local kernel rebuild failed",
    ))
}

fn parse_local_build_backend_kind(value: &str) -> Result<LocalBuildBackendKind, ApiError> {
    match value.trim().to_ascii_lowercase().as_str() {
        "docker" => Ok(LocalBuildBackendKind::Docker),
        "podman" => Ok(LocalBuildBackendKind::Podman),
        "wsl" => Ok(LocalBuildBackendKind::Wsl),
        "script" => Ok(LocalBuildBackendKind::Script),
        other => Err(ApiError::bad_request(format!(
            "unsupported local build backend {other}"
        ))),
    }
}

fn queue_local_build_source_sync_task(
    state: AppState,
    plan: LocalBuildSourceSyncPlan,
    kind: &'static str,
    accepted_message: &'static str,
    running_message: &'static str,
    success_message: &'static str,
    failure_message: &'static str,
) -> (StatusCode, Json<TaskSnapshot>) {
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: kind.into(),
        state: "pending".into(),
        message: Some(accepted_message.into()),
        output: vec![
            "## prepare source sync".into(),
            format!("backend: {}", plan.backend_kind.as_str()),
            format!("source instance: {}", plan.source_instance.display_name),
            format!("command: {}", plan.command.display()),
        ],
        result: mark_task_result_cancelable(
            json!({
                "backendKind": plan.backend_kind,
                "sourceInstance": plan.source_instance,
            }),
            true,
        ),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });
    let cancel_rx = state.register_task_canceller(&id);

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running_snapshot = TaskSnapshot {
            state: "running".into(),
            message: Some(running_message.into()),
            result: mark_task_result_cancelable(snapshot.result.clone(), true),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running_snapshot.clone(),
            download_path: None,
        });
        match run_streaming_command_for_task(
            task_state.clone(),
            running_snapshot.clone(),
            plan.command.clone(),
            cancel_rx,
        )
        .await
        {
            Ok(CommandStreamingOutcome::Succeeded { text, lines }) => match task_state
                .write_local_build(|manager| {
                    manager.finalize_source_sync(
                        &plan.source_instance.id,
                        &snapshot.id,
                        plan.backend_kind,
                    )
                }) {
                Ok(source_instance) => {
                    let refreshed_status = inspect_local_build_status().ok();
                    let result = if let Some(status) = refreshed_status {
                        json!({
                            "backendKind": plan.backend_kind,
                            "sourceInstance": source_instance,
                            "status": status,
                            "stdout": text,
                        })
                    } else {
                        json!({
                            "backendKind": plan.backend_kind,
                            "sourceInstance": source_instance,
                            "stdout": text,
                        })
                    };
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "succeeded".into(),
                            message: Some(success_message.into()),
                            output: lines,
                            result,
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
                Err(error) => {
                    let message = error.to_string();
                    task_state
                        .write_local_build(|manager| {
                            manager.fail_source_sync(
                                &plan.source_instance.id,
                                &snapshot.id,
                                &message,
                            )
                        })
                        .ok();
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "failed".into(),
                            message: Some(failure_message.into()),
                            output: lines_from_message(&message),
                            result: json!({
                                "backendKind": plan.backend_kind,
                                "sourceInstanceId": plan.source_instance.id,
                                "error": message,
                            }),
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
            },
            Ok(CommandStreamingOutcome::Cancelled { text, lines }) => {
                let message = if text.trim().is_empty() {
                    "local source sync cancelled".to_string()
                } else {
                    text
                };
                task_state
                    .write_local_build(|manager| {
                        manager.fail_source_sync(&plan.source_instance.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "cancelled".into(),
                        message: Some("local source sync cancelled".into()),
                        output: lines,
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "sourceInstanceId": plan.source_instance.id,
                            "cancelled": true,
                            "stdout": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(CommandStreamingOutcome::Failed { message, lines }) => {
                task_state
                    .write_local_build(|manager| {
                        manager.fail_source_sync(&plan.source_instance.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines,
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "sourceInstanceId": plan.source_instance.id,
                            "error": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.message.clone();
                task_state
                    .write_local_build(|manager| {
                        manager.fail_source_sync(&plan.source_instance.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines_from_message(&message),
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "sourceInstanceId": plan.source_instance.id,
                            "error": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
        task_state.clear_task_canceller(&snapshot.id);
    });

    (StatusCode::ACCEPTED, Json(accepted_snapshot))
}

fn queue_local_build_backend_install_task(
    state: AppState,
    plan: LocalBuildBackendInstallPlan,
    kind: &'static str,
    accepted_message: &'static str,
    running_message: &'static str,
    success_message: &'static str,
    failure_message: &'static str,
) -> (StatusCode, Json<TaskSnapshot>) {
    let id = Uuid::new_v4().to_string();
    let preview = match &plan.action {
        LocalBuildBackendInstallAction::PullContainerImage { command } => command.display(),
        LocalBuildBackendInstallAction::ImportWslRootfs { command } => command.display(),
        LocalBuildBackendInstallAction::RestoreScriptAssets => {
            "re-materialize local build scripts".into()
        }
    };
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: kind.into(),
        state: "pending".into(),
        message: Some(accepted_message.into()),
        output: vec![
            "## prepare backend install".into(),
            format!("backend: {}", plan.backend.label),
            format!("action: {preview}"),
        ],
        result: mark_task_result_cancelable(
            json!({
                "backend": plan.backend,
            }),
            true,
        ),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });
    let cancel_rx = state.register_task_canceller(&id);

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running_snapshot = TaskSnapshot {
            state: "running".into(),
            message: Some(running_message.into()),
            result: mark_task_result_cancelable(snapshot.result.clone(), true),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running_snapshot.clone(),
            download_path: None,
        });

        let execution = async {
            match plan.action.clone() {
                LocalBuildBackendInstallAction::PullContainerImage { command }
                | LocalBuildBackendInstallAction::ImportWslRootfs { command } => {
                    run_streaming_command_for_task(
                        task_state.clone(),
                        running_snapshot.clone(),
                        command,
                        cancel_rx,
                    )
                    .await
                }
                LocalBuildBackendInstallAction::RestoreScriptAssets => {
                    task_state
                        .write_local_build(|manager| manager.restore_script_backend_assets())
                        .map_err(ApiError::from)?;
                    Ok(CommandStreamingOutcome::Succeeded {
                        text: "local build scripts restored".into(),
                        lines: vec![
                            "## prepare backend install".into(),
                            format!("backend: {}", plan.backend.label),
                            "local build scripts restored".into(),
                        ],
                    })
                }
            }
        }
        .await;

        match execution {
            Ok(CommandStreamingOutcome::Succeeded { text, lines }) => {
                let backends = task_state.read_local_build(LocalBuildManager::list_backends);
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "succeeded".into(),
                        message: Some(success_message.into()),
                        output: lines,
                        result: json!({
                            "backend": plan.backend,
                            "backends": backends,
                            "stdout": text,
                            "cancelable": false,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(CommandStreamingOutcome::Cancelled { text, lines }) => {
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "cancelled".into(),
                        message: Some("local backend install cancelled".into()),
                        output: lines,
                        result: json!({
                            "backend": plan.backend,
                            "stdout": text,
                            "cancelable": false,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(CommandStreamingOutcome::Failed { message, lines }) => {
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines,
                        result: json!({
                            "backend": plan.backend,
                            "error": message,
                            "cancelable": false,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let lines = error
                    .message
                    .lines()
                    .map(|line| line.to_string())
                    .collect();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines,
                        result: json!({
                            "backend": plan.backend,
                            "error": error.message,
                            "cancelable": false,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });
    (StatusCode::ACCEPTED, Json(accepted_snapshot))
}

fn queue_local_build_profile_build_task(
    state: AppState,
    plan: LocalBuildProfileBuildPlan,
    request: BuildLocalBuildProfileRequest,
    kind: &'static str,
    accepted_message: &'static str,
    running_message: &'static str,
    success_message: &'static str,
    failure_message: &'static str,
) -> (StatusCode, Json<TaskSnapshot>) {
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: kind.into(),
        state: "pending".into(),
        message: Some(accepted_message.into()),
        output: vec![
            "## prepare profile build".into(),
            format!("backend: {}", plan.backend_kind.as_str()),
            format!("profile: {}", plan.profile.name),
            format!("source instance: {}", plan.source_instance.display_name),
            format!("build command: {}", plan.build_command.display()),
        ],
        result: mark_task_result_cancelable(
            json!({
                "backendKind": plan.backend_kind,
                "profile": plan.profile,
                "sourceInstance": plan.source_instance,
                "request": request,
            }),
            true,
        ),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });
    let cancel_rx = state.register_task_canceller(&id);

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running_snapshot = TaskSnapshot {
            state: "running".into(),
            message: Some(running_message.into()),
            result: mark_task_result_cancelable(snapshot.result.clone(), true),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running_snapshot.clone(),
            download_path: None,
        });

        let execution = async {
            let mut lines = Vec::<String>::new();
            let cancel_rx = cancel_rx;
            if let Some(activation_command) = plan.activation_command.clone() {
                push_task_output_line(&mut lines, "## activate source instance".into());
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        output: lines.clone(),
                        ..running_snapshot.clone()
                    },
                    download_path: None,
                });
                match run_streaming_command_for_task(
                    task_state.clone(),
                    TaskSnapshot {
                        output: lines.clone(),
                        ..running_snapshot.clone()
                    },
                    activation_command,
                    cancel_rx.clone(),
                )
                .await?
                {
                    CommandStreamingOutcome::Succeeded {
                        text: _activation_text,
                        lines: updated_lines,
                    } => {
                        lines = updated_lines;
                    }
                    CommandStreamingOutcome::Cancelled { text, lines } => {
                        return Ok::<CommandStreamingOutcome, ApiError>(
                            CommandStreamingOutcome::Cancelled { text, lines },
                        );
                    }
                    CommandStreamingOutcome::Failed { message, lines } => {
                        return Ok::<CommandStreamingOutcome, ApiError>(
                            CommandStreamingOutcome::Failed { message, lines },
                        );
                    }
                }
                task_state
                    .write_local_build(|manager| {
                        manager.finalize_source_sync(
                            &plan.source_instance.id,
                            &snapshot.id,
                            plan.backend_kind,
                        )
                    })
                    .map_err(ApiError::from)?;
            }
            task_state
                .write_local_build(|manager| {
                    manager.materialize_profile_environment(
                        &plan.source_instance.id,
                        &plan.build_request,
                    )
                })
                .map_err(ApiError::from)?;
            push_task_output_line(&mut lines, "## run build".into());
            task_state.upsert_task(LocalTask {
                snapshot: TaskSnapshot {
                    output: lines.clone(),
                    ..running_snapshot.clone()
                },
                download_path: None,
            });
            match run_streaming_command_for_task(
                task_state.clone(),
                TaskSnapshot {
                    output: lines.clone(),
                    ..running_snapshot.clone()
                },
                plan.build_command.clone(),
                cancel_rx,
            )
            .await?
            {
                CommandStreamingOutcome::Succeeded {
                    text: build_text,
                    lines: updated_lines,
                } => {
                    lines = updated_lines;
                    Ok::<CommandStreamingOutcome, ApiError>(CommandStreamingOutcome::Succeeded {
                        text: build_text,
                        lines,
                    })
                }
                CommandStreamingOutcome::Cancelled { text, lines } => {
                    Ok::<CommandStreamingOutcome, ApiError>(CommandStreamingOutcome::Cancelled {
                        text,
                        lines,
                    })
                }
                CommandStreamingOutcome::Failed { message, lines } => {
                    Ok::<CommandStreamingOutcome, ApiError>(CommandStreamingOutcome::Failed {
                        message,
                        lines,
                    })
                }
            }
        }
        .await;

        match execution {
            Ok(CommandStreamingOutcome::Succeeded { text, lines }) => match task_state
                .write_local_build(|manager| {
                    manager.finalize_profile_build(
                        &plan.profile.id,
                        &snapshot.id,
                        plan.backend_kind,
                    )
                }) {
                Ok(profile) => {
                    let refreshed_status = inspect_local_build_status().ok();
                    let source_instance = task_state.read_local_build(|manager| {
                        manager
                            .list_source_instances()
                            .source_instances
                            .into_iter()
                            .find(|source| source.id == plan.source_instance.id)
                    });
                    let result = if let Some(status) = refreshed_status {
                        json!({
                            "backendKind": plan.backend_kind,
                            "profile": profile,
                            "sourceInstance": source_instance,
                            "request": request,
                            "status": status,
                            "stdout": text,
                        })
                    } else {
                        json!({
                            "backendKind": plan.backend_kind,
                            "profile": profile,
                            "sourceInstance": source_instance,
                            "request": request,
                            "stdout": text,
                        })
                    };
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "succeeded".into(),
                            message: Some(success_message.into()),
                            output: lines,
                            result,
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
                Err(error) => {
                    let message = error.to_string();
                    task_state
                        .write_local_build(|manager| {
                            manager.fail_profile_build(&plan.profile.id, &snapshot.id, &message)
                        })
                        .ok();
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "failed".into(),
                            message: Some(failure_message.into()),
                            output: lines_from_message(&message),
                            result: json!({
                                "backendKind": plan.backend_kind,
                                "profileId": plan.profile.id,
                                "sourceInstanceId": plan.source_instance.id,
                                "request": request,
                                "error": message,
                            }),
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
            },
            Ok(CommandStreamingOutcome::Cancelled { text, lines }) => {
                let message = if text.trim().is_empty() {
                    "local build task cancelled".to_string()
                } else {
                    text
                };
                task_state
                    .write_local_build(|manager| {
                        manager.fail_profile_build(&plan.profile.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "cancelled".into(),
                        message: Some("local build task cancelled".into()),
                        output: lines,
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "profileId": plan.profile.id,
                            "sourceInstanceId": plan.source_instance.id,
                            "request": request,
                            "cancelled": true,
                            "stdout": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(CommandStreamingOutcome::Failed { message, lines }) => {
                task_state
                    .write_local_build(|manager| {
                        manager.fail_profile_build(&plan.profile.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines,
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "profileId": plan.profile.id,
                            "sourceInstanceId": plan.source_instance.id,
                            "request": request,
                            "error": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.message.clone();
                task_state
                    .write_local_build(|manager| {
                        manager.fail_profile_build(&plan.profile.id, &snapshot.id, &message)
                    })
                    .ok();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some(failure_message.into()),
                        output: lines_from_message(&message),
                        result: json!({
                            "backendKind": plan.backend_kind,
                            "profileId": plan.profile.id,
                            "sourceInstanceId": plan.source_instance.id,
                            "request": request,
                            "error": message,
                        }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
        task_state.clear_task_canceller(&snapshot.id);
    });

    (StatusCode::ACCEPTED, Json(accepted_snapshot))
}

enum CommandStreamingOutcome {
    Succeeded { text: String, lines: Vec<String> },
    Failed { message: String, lines: Vec<String> },
    Cancelled { text: String, lines: Vec<String> },
}

async fn run_streaming_command_for_task(
    state: AppState,
    running_snapshot: TaskSnapshot,
    spec: crate::commands::CommandSpec,
    mut cancel_rx: watch::Receiver<bool>,
) -> Result<CommandStreamingOutcome, ApiError> {
    let mut command = TokioCommand::new(&spec.program);
    command
        .args(&spec.args)
        .current_dir(&spec.cwd)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped());
    let mut child = command
        .spawn()
        .with_context(|| format!("failed to execute {}", spec.display()))
        .map_err(ApiError::from)?;
    if let Some(input) = spec.stdin.as_deref() {
        if let Some(mut stdin) = child.stdin.take() {
            stdin
                .write_all(input.as_bytes())
                .await
                .with_context(|| format!("failed to write stdin for {}", spec.display()))
                .map_err(ApiError::from)?;
        }
    } else {
        let _ = child.stdin.take();
    }

    let (line_tx, mut line_rx) = mpsc::unbounded_channel::<String>();
    if let Some(stdout) = child.stdout.take() {
        tokio::spawn(read_process_output(stdout, line_tx.clone()));
    }
    if let Some(stderr) = child.stderr.take() {
        tokio::spawn(read_process_output(stderr, line_tx.clone()));
    }
    drop(line_tx);

    let mut output_lines = running_snapshot.output.clone();
    let mut stream_closed = false;
    let mut child_exited = false;
    let mut exit_status = None;
    let mut cancelled = false;

    loop {
        tokio::select! {
            maybe_line = line_rx.recv(), if !stream_closed => {
                match maybe_line {
                    Some(line) => {
                        push_task_output_line(&mut output_lines, line);
                        state.upsert_task(LocalTask {
                            snapshot: TaskSnapshot {
                                output: output_lines.clone(),
                                ..running_snapshot.clone()
                            },
                            download_path: None,
                        });
                    }
                    None => {
                        stream_closed = true;
                    }
                }
            }
            status = child.wait(), if !child_exited => {
                let status = status
                    .with_context(|| format!("failed to wait for {}", spec.display()))
                    .map_err(ApiError::from)?;
                child_exited = true;
                exit_status = Some(status);
            }
            changed = cancel_rx.changed(), if !*cancel_rx.borrow() => {
                if changed.is_ok() && *cancel_rx.borrow() {
                    cancelled = true;
                    let _ = child.start_kill();
                }
            }
        }

        if child_exited && stream_closed {
            break;
        }
    }

    let text = output_lines.join("\n");
    if cancelled {
        return Ok(CommandStreamingOutcome::Cancelled {
            text,
            lines: output_lines,
        });
    }

    if exit_status.is_some_and(|status| status.success()) {
        Ok(CommandStreamingOutcome::Succeeded {
            text,
            lines: output_lines,
        })
    } else {
        let message = if text.trim().is_empty() {
            format!("command failed while running {}", spec.display())
        } else {
            text.clone()
        };
        Ok(CommandStreamingOutcome::Failed {
            message,
            lines: output_lines,
        })
    }
}

async fn read_process_output<R>(reader: R, tx: mpsc::UnboundedSender<String>)
where
    R: tokio::io::AsyncRead + Unpin + Send + 'static,
{
    let mut lines = BufReader::new(reader).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        let clean = line.trim_end().to_string();
        if clean.is_empty() || clean.starts_with("[sudo] password for ") {
            continue;
        }
        if tx.send(clean).is_err() {
            break;
        }
    }
}

fn push_task_output_line(lines: &mut Vec<String>, line: String) {
    lines.push(line);
    if lines.len() > MAX_TASK_OUTPUT_LINES {
        let overflow = lines.len() - MAX_TASK_OUTPUT_LINES;
        lines.drain(0..overflow);
    }
}

fn lines_from_message(message: &str) -> Vec<String> {
    let mut lines = split_lines(message);
    if lines.len() > MAX_TASK_OUTPUT_LINES {
        let overflow = lines.len() - MAX_TASK_OUTPUT_LINES;
        lines.drain(0..overflow);
    }
    lines
}

fn mark_task_result_cancelable(result: Value, cancelable: bool) -> Value {
    match result {
        Value::Object(mut object) => {
            object.insert("cancelable".into(), Value::Bool(cancelable));
            Value::Object(object)
        }
        other => other,
    }
}

async fn track_dispatched_build_task(
    state: AppState,
    snapshot: TaskSnapshot,
    dispatch_result: Value,
    baseline_run_ids: HashSet<u64>,
    base_output: Vec<String>,
) -> Result<(), ApiError> {
    let dispatches = extract_dispatch_workflow_names(&dispatch_result);
    if dispatches.is_empty() {
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: "succeeded".into(),
                message: Some("build dispatch finished".into()),
                output: build_gki_tracking_output(&base_output, &[], "build dispatch finished"),
                result: merge_build_tracking_result(&dispatch_result, &[], "dispatch_finished"),
                ..snapshot
            },
            download_path: None,
        });
        return Ok(());
    }

    let discovery_started = SystemTime::now();
    let mut tracked_runs = loop {
        let runs_payload = run_cli_json_command(vec![
            "--json".into(),
            "status".into(),
            "--limit".into(),
            BUILD_TRACK_RUN_LIMIT.to_string(),
        ])
        .await?;
        let recent_runs = extract_runs_from_status(&runs_payload);
        let tracked_runs = select_dispatched_runs(&recent_runs, &baseline_run_ids, &dispatches);
        let discovered = tracked_runs.len();
        let expected = dispatches.len();
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: "running".into(),
                message: Some(format!(
                    "build dispatched, waiting for workflow runs ({discovered}/{expected})"
                )),
                output: build_gki_tracking_output(
                    &base_output,
                    &tracked_runs,
                    &format!("workflow discovery {discovered}/{expected}"),
                ),
                result: merge_build_tracking_result(
                    &dispatch_result,
                    &tracked_runs,
                    "discovering_runs",
                ),
                ..snapshot.clone()
            },
            download_path: None,
        });
        if discovered >= expected {
            break tracked_runs;
        }
        if discovery_started.elapsed().unwrap_or_default() < BUILD_DISCOVERY_TIMEOUT {
            sleep(BUILD_DISCOVERY_POLL_INTERVAL).await;
            continue;
        }
        return Err(ApiError::service_unavailable(format!(
            "build dispatched but only discovered {discovered}/{expected} workflow runs"
        )));
    };

    loop {
        let mut refreshed_runs = Vec::with_capacity(tracked_runs.len());
        for run in &tracked_runs {
            let run_id = extract_run_id(run)
                .ok_or_else(|| ApiError::service_unavailable("tracked workflow run missing id"))?;
            let run_payload = run_cli_json_command(vec![
                "--json".into(),
                "status".into(),
                "--run-id".into(),
                run_id.to_string(),
            ])
            .await?;
            refreshed_runs.push(
                run_payload
                    .get("run")
                    .cloned()
                    .unwrap_or_else(|| run.clone()),
            );
        }
        tracked_runs = refreshed_runs;
        let completed = tracked_runs
            .iter()
            .filter(|run| is_run_terminal(run))
            .count();
        let expected = tracked_runs.len();
        let all_terminal = completed == expected;
        let all_succeeded = tracked_runs.iter().all(|run| run_succeeded(run));
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: if all_terminal && all_succeeded {
                    "succeeded".into()
                } else if all_terminal {
                    "failed".into()
                } else {
                    "running".into()
                },
                message: Some(if all_terminal && all_succeeded {
                    "build workflow finished".into()
                } else if all_terminal {
                    "build workflow failed".into()
                } else {
                    format!("build workflow running ({completed}/{expected})")
                }),
                output: build_gki_tracking_output(
                    &base_output,
                    &tracked_runs,
                    &format!("workflow completion {completed}/{expected}"),
                ),
                result: merge_build_tracking_result(
                    &dispatch_result,
                    &tracked_runs,
                    if all_terminal {
                        "workflow_finished"
                    } else {
                        "running"
                    },
                ),
                ..snapshot.clone()
            },
            download_path: None,
        });
        if all_terminal {
            return Ok(());
        }
        sleep(BUILD_COMPLETION_POLL_INTERVAL).await;
    }
}

async fn start_gki_build(
    State(state): State<AppState>,
    Json(request): Json<BuildGkiRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let session = run_cli_json_command(vec!["--json".into(), "whoami".into()]).await?;
    if !session
        .get("loggedIn")
        .and_then(Value::as_bool)
        .unwrap_or(false)
    {
        return Err(ApiError::service_unavailable(
            "github account not logged in",
        ));
    }
    if session
        .get("needsFork")
        .and_then(Value::as_bool)
        .unwrap_or(true)
    {
        return Err(ApiError::bad_request("fork your ABK repository first"));
    }
    if session
        .get("needsSync")
        .and_then(Value::as_bool)
        .unwrap_or(false)
    {
        return Err(ApiError::bad_request("sync the fork before building"));
    }

    let baseline_run_ids = match run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--limit".into(),
        BUILD_TRACK_RUN_LIMIT.to_string(),
    ])
    .await
    {
        Ok(value) => extract_run_ids_from_status(&value),
        Err(error) => {
            state.log(
                "builds",
                "warn",
                format!(
                    "failed to capture baseline workflow runs: {}",
                    error.message
                ),
            );
            HashSet::new()
        }
    };

    let args = build_gki_cli_args(&request)?;
    let args_for_result = args.clone();
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "build.gki".into(),
        state: "pending".into(),
        message: Some("build request accepted".into()),
        output: Vec::new(),
        result: json!({
            "request": request,
            "cliArgs": args_for_result,
        }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("build is being dispatched".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });
        let output = tokio::task::spawn_blocking(move || {
            let spec = crate::commands::build_cli_command_parts(&args)?;
            run_command(&spec)
        })
        .await;
        match output {
            Ok(Ok(text)) => {
                let parsed = parse_cli_json_output(&text).unwrap_or_else(|_| {
                    json!({
                        "stdout": text,
                    })
                });
                let base_output = split_lines(&text);
                if parsed
                    .get("dryRun")
                    .and_then(Value::as_bool)
                    .unwrap_or(false)
                {
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "succeeded".into(),
                            message: Some("build dry run finished".into()),
                            output: base_output,
                            result: parsed,
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                    return;
                }
                if let Err(error) = track_dispatched_build_task(
                    task_state.clone(),
                    snapshot.clone(),
                    parsed,
                    baseline_run_ids,
                    base_output,
                )
                .await
                {
                    let message = error.message.clone();
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "failed".into(),
                            message: Some("build tracking failed".into()),
                            output: split_lines(&message),
                            result: json!({ "error": message }),
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("build dispatch failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("build dispatch join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn download_build_artifact(
    State(state): State<AppState>,
    Path(run_id): Path<u64>,
    Json(request): Json<ArtifactDownloadRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let output_dir = request.output_dir.clone();
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "artifact.download".into(),
        state: "pending".into(),
        message: Some("artifact download accepted".into()),
        output: Vec::new(),
        result: json!({
            "runId": run_id,
            "artifactId": request.artifact_id,
            "outputDir": output_dir,
        }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("artifact is being downloaded".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });

        let mut args = vec![
            "--json".into(),
            "artifacts".into(),
            "--run-id".into(),
            run_id.to_string(),
            "--download".into(),
            "--artifact-id".into(),
            request.artifact_id.to_string(),
        ];
        if let Some(dir) = output_dir.clone() {
            args.push("--output".into());
            args.push(dir);
        }

        let output = tokio::task::spawn_blocking(move || {
            let spec = crate::commands::build_cli_command_parts(&args)?;
            run_command(&spec)
        })
        .await;

        match output {
            Ok(Ok(text)) => {
                let parsed = parse_cli_json_output(&text).unwrap_or_else(|_| {
                    json!({
                        "stdout": text,
                    })
                });
                let download_path = parsed
                    .get("downloads")
                    .and_then(Value::as_array)
                    .and_then(|items| items.first())
                    .and_then(|item| item.get("path"))
                    .and_then(Value::as_str)
                    .map(PathBuf::from);
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "succeeded".into(),
                        message: Some("artifact download finished".into()),
                        output: split_lines(&text),
                        result: parsed,
                        download_name: download_path
                            .as_ref()
                            .and_then(|path| path.file_name())
                            .and_then(|name| name.to_str())
                            .map(ToString::to_string),
                        download_content_type: Some("application/zip".into()),
                        ..snapshot.clone()
                    },
                    download_path,
                });
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("artifact download failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("artifact download join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn proxy_session(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/session", None).await?,
    ))
}

async fn proxy_runtime(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/runtime", None).await?,
    ))
}

async fn proxy_root_grants(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/root-grants", None).await?,
    ))
}

async fn proxy_kernel_features(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/kernel-features", None).await?,
    ))
}

async fn proxy_packages(
    State(state): State<AppState>,
    Query(query): Query<PackageQuery>,
) -> Result<Json<Value>, ApiError> {
    let package_type = query.r#type.unwrap_or_else(|| "all".into());
    let path = format!(
        "/api/v1/packages?type={}",
        urlencoding::encode(&package_type)
    );
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn proxy_package_info(
    State(state): State<AppState>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::POST, "/api/v1/packages/info", Some(payload)).await?,
    ))
}

async fn proxy_root_grant_allow(
    State(state): State<AppState>,
    Path(package_name): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/root-grants/{}/allow",
        urlencoding::encode(package_name.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_kernel_feature_set(
    State(state): State<AppState>,
    Path(feature_id): Path<String>,
    Json(payload): Json<KernelFeatureRequest>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/kernel-features/{}",
        urlencoding::encode(feature_id.trim())
    );
    Ok(Json(
        proxy_agent_json(
            &state,
            Method::POST,
            &path,
            Some(json!({ "enabled": payload.enabled })),
        )
        .await?,
    ))
}

async fn proxy_root_grant_icon(
    State(state): State<AppState>,
    Path(package_name): Path<String>,
) -> Result<Response<Body>, ApiError> {
    let path = format!(
        "/api/v1/root-grants/{}/icon",
        urlencoding::encode(package_name.trim())
    );
    proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await
}

async fn proxy_susfs(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/susfs", None).await?,
    ))
}

async fn proxy_susfs_apply(
    State(state): State<AppState>,
    Json(payload): Json<Value>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let value =
        proxy_agent_json_with_status(&state, Method::POST, "/api/v1/susfs/apply", Some(payload))
            .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_module_enable(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/enable",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_pending_uninstall(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/pending-uninstall",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_action(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/action",
        urlencoding::encode(module_id.trim())
    );
    let value = proxy_agent_json_with_status(&state, Method::POST, &path, Some(payload)).await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_module_webui_module_info(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/module-info",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn proxy_module_webui_exec(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/exec",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_webui_spawn(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/spawn",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_webui_http_proxy(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    method: Method,
    query: Query<HashMap<String, String>>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response<Body>, ApiError> {
    let target = query.0.get("target").cloned().unwrap_or_default();
    if target.trim().is_empty() {
        return Err(ApiError::bad_request("target missing"));
    }
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/http-proxy?target={}",
        urlencoding::encode(module_id.trim()),
        urlencoding::encode(target.trim())
    );
    proxy_binary_response(
        &state,
        method,
        &path,
        forward_headers(&headers),
        Some(body.to_vec()),
    )
    .await
}

async fn proxy_module_webui_root(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
) -> Result<Response<Body>, ApiError> {
    proxy_module_webui_file_impl(state, module_id, String::new()).await
}

async fn proxy_module_webui_file(
    State(state): State<AppState>,
    Path((module_id, relative_path)): Path<(String, String)>,
) -> Result<Response<Body>, ApiError> {
    proxy_module_webui_file_impl(state, module_id, relative_path).await
}

async fn proxy_module_webui_file_impl(
    state: AppState,
    module_id: String,
    relative_path: String,
) -> Result<Response<Body>, ApiError> {
    let clean_module_id = module_id.trim().to_string();
    let path = if relative_path.trim().is_empty() {
        format!(
            "/api/v1/runtime/modules/{}/webui/files",
            urlencoding::encode(&clean_module_id)
        )
    } else {
        format!(
            "/api/v1/runtime/modules/{}/webui/files/{}",
            urlencoding::encode(&clean_module_id),
            relative_path
        )
    };
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = state
        .inner
        .agent
        .request(
            &base_url,
            reqwest_method(Method::GET)?,
            &path,
            &HeaderMap::new(),
            None,
        )
        .await
        .map_err(ApiError::from)?;
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let content_type = response
        .headers()
        .get(CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string)
        .unwrap_or_else(|| guess_content_type(&relative_path).to_string());
    let bytes = response
        .bytes()
        .await
        .context("failed to read module webui asset")?;
    let body_bytes =
        rewrite_module_webui_asset(&clean_module_id, &relative_path, &content_type, &bytes);

    let mut builder = Response::builder().status(status);
    builder = builder
        .header(
            CONTENT_TYPE,
            HeaderValue::from_str(&content_type)
                .unwrap_or(HeaderValue::from_static("application/octet-stream")),
        )
        .header(CACHE_CONTROL, HeaderValue::from_static("no-store"));
    Ok(builder.body(Body::from(body_bytes)).expect("response"))
}

async fn proxy_install_module(
    State(state): State<AppState>,
    Json(payload): Json<InstallModuleRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.zip_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/install/module",
        Some(json!({ "zipPath": remote_path })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_install_apk(
    State(state): State<AppState>,
    Json(payload): Json<InstallApkRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.apk_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/install/apk",
        Some(json!({ "apkPath": remote_path })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_flash_image(
    State(state): State<AppState>,
    Json(payload): Json<FlashImageRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.image_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/flash/image",
        Some(json!({
            "imagePath": remote_path,
            "partition": payload.partition,
        })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_export_diagnostics(
    State(state): State<AppState>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/diagnostics/export",
        Some(json!({})),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn get_task(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Json<Value>, ApiError> {
    if let Some(task) = state.get_local_task(task_id.trim()) {
        return Ok(Json(
            serde_json::to_value(task.snapshot).map_err(ApiError::from)?,
        ));
    }
    let path = format!("/api/v1/tasks/{}", urlencoding::encode(task_id.trim()));
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn cancel_task(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Json<Value>, ApiError> {
    let snapshot = state
        .request_task_cancel(task_id.trim())
        .ok_or_else(|| ApiError::bad_request(format!("unknown task {}", task_id.trim())))?;
    Ok(Json(
        serde_json::to_value(snapshot).map_err(ApiError::from)?,
    ))
}

async fn download_task_file(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Response<Body>, ApiError> {
    if let Some(task) = state.get_local_task(task_id.trim()) {
        if let Some(path) = task.download_path {
            let bytes = tokio::fs::read(&path)
                .await
                .with_context(|| format!("failed to read {}", path.display()))?;
            let file_name = path
                .file_name()
                .and_then(|value| value.to_str())
                .unwrap_or("download.bin");
            return Ok(Response::builder()
                .status(StatusCode::OK)
                .header(
                    CONTENT_TYPE,
                    HeaderValue::from_static("application/octet-stream"),
                )
                .header(
                    "content-disposition",
                    HeaderValue::from_str(&format!("attachment; filename=\"{file_name}\""))
                        .unwrap_or(HeaderValue::from_static("attachment")),
                )
                .body(Body::from(bytes))
                .expect("response"));
        }
    }
    let path = format!(
        "/api/v1/tasks/{}/download",
        urlencoding::encode(task_id.trim())
    );
    proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await
}

async fn insets_css() -> impl IntoResponse {
    (
        StatusCode::OK,
        [(CONTENT_TYPE, "text/css; charset=utf-8"), (CACHE_CONTROL, "no-store")],
        ":root{--ksu-safe-area-inset-top:0px;--ksu-safe-area-inset-right:0px;--ksu-safe-area-inset-bottom:0px;--ksu-safe-area-inset-left:0px;}",
    )
}

async fn proxy_webui_root_asset_fallback(
    State(state): State<AppState>,
    method: Method,
    uri: Uri,
    headers: HeaderMap,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return StatusCode::NOT_FOUND.into_response();
    }

    let Some((module_id, relative_path)) = fallback_webui_asset_target(
        uri.path(),
        headers.get("referer").and_then(|value| value.to_str().ok()),
    ) else {
        return StatusCode::NOT_FOUND.into_response();
    };

    let candidates = fallback_webui_asset_candidates(&relative_path);
    for candidate in candidates {
        let mut path = format!(
            "/api/v1/runtime/modules/{}/webui/files/{}",
            urlencoding::encode(module_id.trim()),
            candidate
        );
        if let Some(query) = uri.query() {
            path.push('?');
            path.push_str(query);
        }

        match proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await {
            Ok(response) if response.status() != StatusCode::NOT_FOUND => return response,
            Ok(_) => continue,
            Err(error) => return error.into_response(),
        }
    }

    StatusCode::NOT_FOUND.into_response()
}

async fn proxy_agent_json(
    state: &AppState,
    method: Method,
    path: &str,
    body: Option<Value>,
) -> Result<Value, ApiError> {
    proxy_agent_json_with_status(state, method, path, body).await
}

async fn proxy_agent_json_with_status(
    state: &AppState,
    method: Method,
    path: &str,
    body: Option<Value>,
) -> Result<Value, ApiError> {
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = match method {
        Method::GET => state
            .inner
            .agent
            .request(
                &base_url,
                reqwest_method(method)?,
                path,
                &HeaderMap::new(),
                None,
            )
            .await
            .map_err(ApiError::from)?,
        Method::POST => state
            .inner
            .agent
            .post_json(&base_url, path, &body.unwrap_or_else(|| json!({})))
            .await
            .map_err(ApiError::from)?,
        other => return Err(ApiError::bad_request(format!("unsupported method {other}"))),
    };
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let text = response
        .text()
        .await
        .context("failed to read agent response")?;
    let value = serde_json::from_str::<Value>(&text).unwrap_or_else(|_| json!({ "stdout": text }));
    if !status.is_success() && status != StatusCode::ACCEPTED {
        let message = value
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or_else(|| text.trim());
        return Err(ApiError {
            status,
            message: message.to_string(),
        });
    }
    Ok(value)
}

async fn proxy_binary_response(
    state: &AppState,
    method: Method,
    path: &str,
    headers: HeaderMap,
    body: Option<Vec<u8>>,
) -> Result<Response<Body>, ApiError> {
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = state
        .inner
        .agent
        .request(&base_url, reqwest_method(method)?, path, &headers, body)
        .await
        .map_err(ApiError::from)?;
    into_streaming_response(response).await
}

async fn into_streaming_response(response: reqwest::Response) -> Result<Response<Body>, ApiError> {
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let headers = response.headers().clone();
    let stream = response
        .bytes_stream()
        .map_err(|error| std::io::Error::other(error.to_string()));
    let mut builder = Response::builder().status(status);
    if let Some(content_type) = headers.get(CONTENT_TYPE) {
        builder = builder.header(CONTENT_TYPE, content_type);
    }
    builder = builder.header(CACHE_CONTROL, HeaderValue::from_static("no-store"));
    Ok(builder.body(Body::from_stream(stream)).expect("response"))
}

async fn run_blocking_command(spec: crate::commands::CommandSpec) -> Result<String, ApiError> {
    tokio::task::spawn_blocking(move || run_command(&spec))
        .await
        .context("command join failure")
        .map_err(ApiError::from)?
        .map_err(ApiError::from)
}

async fn wait_for_agent(
    state: &AppState,
    base_url: &str,
    timeout: Duration,
) -> Result<(), ApiError> {
    let started = std::time::Instant::now();
    loop {
        match state.inner.agent.get_json(base_url, "/api/v1/health").await {
            Ok(_) => return Ok(()),
            Err(error) if started.elapsed() < timeout => {
                state.log(
                    "device.connect",
                    "info",
                    format!("waiting for phone agent: {error}"),
                );
                sleep(Duration::from_millis(500)).await;
            }
            Err(error) => {
                return Err(ApiError::service_unavailable(format!(
                    "phone agent did not become ready: {error}"
                )))
            }
        }
    }
}

async fn stage_host_file_if_needed(state: &AppState, value: &str) -> Result<String, ApiError> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err(ApiError::bad_request("file path missing"));
    }
    let path = FsPath::new(trimmed);
    if !path.is_file() {
        return Ok(trimmed.to_string());
    }
    let connection = state.connection();
    if !connection.connected {
        return Err(ApiError::service_unavailable(
            "device service not connected",
        ));
    }
    let serial = connection.serial.unwrap_or_default();
    let file_name = path
        .file_name()
        .and_then(|value| value.to_str())
        .ok_or_else(|| ApiError::bad_request("file name missing"))?;
    let remote_path = format!("{SIDELOAD_DIR}/{file_name}");
    run_blocking_command(build_adb_shell_command(
        &serial,
        &format!("mkdir -p {SIDELOAD_DIR}"),
    ))
    .await?;
    run_blocking_command(build_adb_push_command(&serial, trimmed, &remote_path)).await?;
    state.log(
        "device.stage",
        "info",
        format!("pushed host file {} -> {remote_path}", path.display()),
    );
    Ok(remote_path)
}

async fn run_cli_json_command(parts: Vec<String>) -> Result<Value, ApiError> {
    let spec = crate::commands::build_cli_command_parts(&parts).map_err(ApiError::from)?;
    let output = run_blocking_command(spec).await?;
    parse_cli_json_output(&output)
}

fn parse_cli_json_output(output: &str) -> Result<Value, ApiError> {
    let trimmed = output.trim();
    if trimmed.is_empty() {
        return Err(ApiError::service_unavailable("cli returned empty output"));
    }
    if let Ok(value) = serde_json::from_str::<Value>(trimmed) {
        return Ok(value);
    }
    for line in trimmed.lines().rev() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        if let Ok(value) = serde_json::from_str::<Value>(line) {
            return Ok(value);
        }
    }
    Err(ApiError::service_unavailable(format!(
        "failed to parse cli json output\n{trimmed}"
    )))
}

async fn cli_client_id() -> Result<String, ApiError> {
    let config = read_cli_config_json().await?;
    Ok(config
        .get("client_id")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToString::to_string)
        .or_else(|| env::var("ABK_CLIENT_ID").ok())
        .unwrap_or_else(|| GITHUB_CLIENT_ID_FALLBACK.to_string()))
}

async fn read_cli_config_json() -> Result<Value, ApiError> {
    let path = cli_config_path()?;
    read_cli_config_json_from_path(&path)
}

fn read_cli_config_json_from_path(path: &PathBuf) -> Result<Value, ApiError> {
    if !path.is_file() {
        return Ok(json!({}));
    }
    let content =
        fs::read_to_string(&path).with_context(|| format!("failed to read {}", path.display()))?;
    serde_json::from_str(&content).map_err(ApiError::from)
}

async fn persist_cli_token(token: &str) -> Result<(), ApiError> {
    let path = cli_config_path()?;
    persist_cli_token_to_path(&path, token)
}

async fn clear_cli_token() -> Result<(), ApiError> {
    let path = cli_config_path()?;
    clear_cli_token_at_path(&path)
}

fn read_proxy_settings_from_path(path: &PathBuf) -> Result<ProxySettings, ApiError> {
    let config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    Ok(ProxySettings {
        http_proxy: object
            .get("http_proxy")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToString::to_string),
        https_proxy: object
            .get("https_proxy")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToString::to_string),
        all_proxy: object
            .get("all_proxy")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToString::to_string),
        no_proxy: object
            .get("no_proxy")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToString::to_string),
    })
}

fn persist_proxy_settings_to_path(
    path: &PathBuf,
    settings: &ProxySettings,
) -> Result<(), ApiError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let mut config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object_mut()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    write_optional_config_string(object, "http_proxy", settings.http_proxy.as_deref());
    write_optional_config_string(object, "https_proxy", settings.https_proxy.as_deref());
    write_optional_config_string(object, "all_proxy", settings.all_proxy.as_deref());
    write_optional_config_string(object, "no_proxy", settings.no_proxy.as_deref());
    let content = serde_json::to_string_pretty(&config)?;
    fs::write(path, content).with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn write_optional_config_string(
    object: &mut serde_json::Map<String, Value>,
    key: &str,
    value: Option<&str>,
) {
    if let Some(value) = value {
        object.insert(key.into(), Value::String(value.to_string()));
    } else {
        object.remove(key);
    }
}

fn persist_cli_token_to_path(path: &PathBuf, token: &str) -> Result<(), ApiError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let mut config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object_mut()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    object.insert("token".into(), Value::String(token.to_string()));
    let content = serde_json::to_string_pretty(&config)?;
    fs::write(&path, content).with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn clear_cli_token_at_path(path: &PathBuf) -> Result<(), ApiError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let mut config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object_mut()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    object.remove("token");
    let content = serde_json::to_string_pretty(&config)?;
    fs::write(&path, content).with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn cli_config_path() -> Result<PathBuf, ApiError> {
    let home =
        resolve_user_home_dir().ok_or_else(|| ApiError::service_unavailable("HOME is not set"))?;
    Ok(home.join(CLI_CONFIG_PATH_SUFFIX))
}

fn resolve_user_home_dir() -> Option<PathBuf> {
    if let Some(home) = env::var("HOME")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return Some(PathBuf::from(home));
    }
    if let Some(profile) = env::var("USERPROFILE")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return Some(PathBuf::from(profile));
    }
    let drive = env::var("HOMEDRIVE").ok();
    let path = env::var("HOMEPATH").ok();
    match (drive, path) {
        (Some(drive), Some(path)) if !drive.trim().is_empty() && !path.trim().is_empty() => {
            Some(PathBuf::from(format!("{}{}", drive.trim(), path.trim())))
        }
        _ => None,
    }
}

fn build_gki_cli_args(request: &BuildGkiRequest) -> Result<Vec<String>, ApiError> {
    let target = request.target.trim().to_lowercase();
    let valid_targets = ["a12", "a13", "a14", "a15", "a16", "custom"];
    if !valid_targets.contains(&target.as_str()) {
        return Err(ApiError::bad_request("unsupported GKI target"));
    }

    let mut args = vec!["--json".into(), "build".into(), "--force".into()];
    if target == "custom" {
        let sub_level = request
            .sub_level
            .clone()
            .filter(|value| !value.trim().is_empty())
            .ok_or_else(|| ApiError::bad_request("custom target requires subLevel"))?;
        let os_patch_level = request
            .os_patch_level
            .clone()
            .filter(|value| !value.trim().is_empty())
            .ok_or_else(|| ApiError::bad_request("custom target requires osPatchLevel"))?;
        args.extend(["--sub-level".into(), sub_level]);
        args.extend(["--os-patch-level".into(), os_patch_level]);
        if let Some(android_version) = request
            .android_version
            .clone()
            .filter(|value| !value.trim().is_empty())
        {
            args.extend(["--android-version".into(), android_version]);
        }
        if let Some(kernel_version) = request
            .kernel_version
            .clone()
            .filter(|value| !value.trim().is_empty())
        {
            args.extend(["--kernel-version".into(), kernel_version]);
        }
    } else {
        args.extend(["--matrix".into(), target]);
    }

    if let Some(value) = request
        .ksu_variant
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--ksu".into(), value]);
    }
    if let Some(value) = request
        .ksu_branch
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--ksu-branch".into(), value]);
    }
    if let Some(value) = request
        .version
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--version".into(), value]);
    }
    if let Some(value) = request
        .revision
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--revision".into(), value]);
    }
    if let Some(value) = request
        .custom_ref
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--custom-ref".into(), value]);
    }
    if let Some(value) = request
        .build_time
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--build-time".into(), value]);
    }
    if let Some(value) = request
        .custom_modules
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--custom-modules".into(), value]);
    }
    if let Some(value) = request
        .kpm_password
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--kpm-password".into(), value]);
    }
    if let Some(value) = request
        .virt
        .clone()
        .filter(|value| !value.trim().is_empty() && value.trim() != "off")
    {
        args.extend(["--virt".into(), value]);
    }
    if let Some(value) = request
        .zram_extra_algos
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--zram-extra-algos".into(), value]);
    }

    args.push(if request.zram { "--zram" } else { "--no-zram" }.into());
    args.push(if request.bbg { "--bbg" } else { "--no-bbg" }.into());
    args.push(if request.ddk { "--ddk" } else { "--no-ddk" }.into());
    args.push(if request.kpm { "--kpm" } else { "--no-kpm" }.into());
    args.push(
        if request.susfs {
            "--susfs"
        } else {
            "--no-susfs"
        }
        .into(),
    );
    args.push(
        if request.rekernel {
            "--rekernel"
        } else {
            "--no-rekernel"
        }
        .into(),
    );
    if request.ntsync {
        args.push("--ntsync".into());
    }
    if request.networking {
        args.push("--networking".into());
    }
    if request.zram_full_algo {
        args.push("--zram-full-algo".into());
    }
    Ok(args)
}

fn inspect_local_build_status() -> Result<LocalBuildStatus> {
    let repo_root = repo_root();
    let path_settings = load_local_build_path_settings(&repo_root)?;
    let script_root = resolve_local_build_root(&repo_root, &path_settings);
    let init_script_path = script_root.join("init.sh");
    let rebuild_script_path = script_root.join("rebuild.sh");
    let default_state_dir = script_root.join(".local-build");
    let default_env_file_path = default_state_dir.join("env.sh");
    let default_workspace_dir = resolve_local_build_workspace_dir(&script_root, &path_settings);
    let default_sources_dir = default_state_dir.join("sources");
    let active_source = active_local_build_source_instance(&repo_root, &path_settings);
    let (state_dir, env_file_path) = resolve_local_build_status_paths(
        &default_state_dir,
        &default_env_file_path,
        active_source.as_ref(),
    )?;
    let env = if env_file_path.is_file() {
        read_exported_env_file(&env_file_path)?
    } else {
        HashMap::new()
    };

    let workspace_dir = env_path(&env, "WORKSPACE_DIR").unwrap_or(default_workspace_dir);
    let sources_dir = env_path(&env, "SOURCES_DIR").unwrap_or(default_sources_dir);
    let artifacts_dir = env_path(&env, "ARTIFACTS_DIR").unwrap_or(workspace_dir.join("artifacts"));
    let logs_dir = env_path(&env, "LOGS_DIR").unwrap_or(workspace_dir.join("logs"));
    let cache_dir = env_path(&env, "CACHE_DIR").unwrap_or(workspace_dir.join("cache"));
    let kernel_root = env_path(&env, "KERNEL_ROOT").unwrap_or(workspace_dir.join("kernel"));
    let latest_log_path =
        latest_file_in_dir(&logs_dir).map(|path| path.to_string_lossy().to_string());
    let template_branch = env
        .get("TEMPLATE_BRANCH")
        .cloned()
        .and_then(non_empty_string);

    Ok(LocalBuildStatus {
        available: init_script_path.is_file() && rebuild_script_path.is_file(),
        script_root: script_root.to_string_lossy().to_string(),
        init_script_path: init_script_path.to_string_lossy().to_string(),
        rebuild_script_path: rebuild_script_path.to_string_lossy().to_string(),
        env_file_path: env_file_path.to_string_lossy().to_string(),
        state_dir: state_dir.to_string_lossy().to_string(),
        sources_dir: sources_dir.to_string_lossy().to_string(),
        workspace_dir: workspace_dir.to_string_lossy().to_string(),
        artifacts_dir: artifacts_dir.to_string_lossy().to_string(),
        logs_dir: logs_dir.to_string_lossy().to_string(),
        cache_dir: cache_dir.to_string_lossy().to_string(),
        kernel_root: kernel_root.to_string_lossy().to_string(),
        has_env_file: env_file_path.is_file(),
        workspace_ready: env_file_path.is_file() && kernel_root.is_dir(),
        template_root: env.get("TEMPLATE_ROOT").cloned().and_then(non_empty_string),
        template_name: env.get("TEMPLATE_NAME").cloned().and_then(non_empty_string),
        template_android_version: env
            .get("TEMPLATE_ANDROID_VERSION")
            .cloned()
            .and_then(non_empty_string),
        template_kernel_version: env
            .get("TEMPLATE_KERNEL_VERSION")
            .cloned()
            .and_then(non_empty_string),
        sub_level: env.get("SUB_LEVEL").cloned().and_then(non_empty_string),
        os_patch_level: env
            .get("OS_PATCH_LEVEL")
            .cloned()
            .and_then(non_empty_string),
        template_branch: template_branch.clone(),
        template_common_branch: env
            .get("TEMPLATE_COMMON_BRANCH")
            .cloned()
            .and_then(non_empty_string),
        branch_month: template_branch.and_then(|value| extract_branch_month(&value)),
        custom_external_modules_root: env
            .get("CUSTOM_EXTERNAL_MODULES_ROOT")
            .cloned()
            .and_then(non_empty_string),
        custom_external_modules_manifest: env
            .get("CUSTOM_EXTERNAL_MODULES_MANIFEST")
            .cloned()
            .and_then(non_empty_string),
        latest_log_path,
        supported_templates: discover_local_build_templates(&script_root),
    })
}

fn active_local_build_source_instance(
    repo_root: &FsPath,
    path_settings: &crate::local_build_paths::LocalBuildPathSettings,
) -> Option<LocalBuildSourceInstance> {
    #[derive(Debug, Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct LocalBuildStatusStoreSnapshot {
        settings: LocalBuildSettings,
        source_instances: Vec<LocalBuildSourceInstance>,
    }

    let store_path =
        resolve_local_build_profile_store_dir(repo_root, path_settings).join("state.json");
    let content = fs::read_to_string(store_path).ok()?;
    let store: LocalBuildStatusStoreSnapshot = serde_json::from_str(&content).ok()?;
    let active_source_id = store.settings.active_source_instance_id?;
    store
        .source_instances
        .into_iter()
        .find(|item| item.id == active_source_id)
}

fn resolve_local_build_status_paths(
    default_state_dir: &FsPath,
    default_env_file_path: &FsPath,
    active_source: Option<&LocalBuildSourceInstance>,
) -> Result<(PathBuf, PathBuf)> {
    let Some(source) = active_source else {
        return Ok((
            default_state_dir.to_path_buf(),
            default_env_file_path.to_path_buf(),
        ));
    };

    let source_state_dir = source_materialized_state_dir(source, default_state_dir);
    let source_env_file_path = source_materialized_env_file_path(source, &source_state_dir);

    if source_env_file_path.is_file() {
        return Ok((source_state_dir.clone(), source_env_file_path));
    }

    if let Some(materialized) = source.materialized.as_ref() {
        let state_dir = materialized
            .state_dir
            .clone()
            .and_then(non_empty_string)
            .map(PathBuf::from)
            .unwrap_or_else(|| source_state_dir.clone());
        let env_file_path = materialized
            .env_file_path
            .clone()
            .and_then(non_empty_string)
            .map(PathBuf::from)
            .unwrap_or_else(|| state_dir.join("env.sh"));
        if env_file_path.is_file() {
            return Ok((state_dir, env_file_path));
        }
    }

    if default_env_file_path.is_file() {
        let legacy_env = read_exported_env_file(default_env_file_path)?;
        if legacy_env_matches_source(&legacy_env, source) {
            let migrated_env_file_path =
                migrate_legacy_local_build_env(default_env_file_path, &source_state_dir)?;
            return Ok((source_state_dir, migrated_env_file_path));
        }
    }

    if let Some(materialized) = source.materialized.as_ref() {
        let state_dir = materialized
            .state_dir
            .clone()
            .and_then(non_empty_string)
            .map(PathBuf::from)
            .unwrap_or_else(|| default_state_dir.to_path_buf());
        let env_file_path = materialized
            .env_file_path
            .clone()
            .and_then(non_empty_string)
            .map(PathBuf::from)
            .unwrap_or_else(|| state_dir.join("env.sh"));
        return Ok((state_dir, env_file_path));
    }

    Ok((source_state_dir, source_env_file_path))
}

fn source_materialized_state_dir(
    source: &LocalBuildSourceInstance,
    default_state_dir: &FsPath,
) -> PathBuf {
    source
        .materialized
        .as_ref()
        .and_then(|materialized| materialized.state_dir.clone())
        .and_then(non_empty_string)
        .map(PathBuf::from)
        .or_else(|| {
            non_empty_string(source.cache_root.clone())
                .map(PathBuf::from)
                .map(|cache_root| cache_root.join(".local-build"))
        })
        .unwrap_or_else(|| default_state_dir.to_path_buf())
}

fn source_materialized_env_file_path(
    source: &LocalBuildSourceInstance,
    source_state_dir: &FsPath,
) -> PathBuf {
    source
        .materialized
        .as_ref()
        .and_then(|materialized| materialized.env_file_path.clone())
        .and_then(non_empty_string)
        .map(PathBuf::from)
        .unwrap_or_else(|| source_state_dir.join("env.sh"))
}

fn migrate_legacy_local_build_env(
    legacy_env_file_path: &FsPath,
    target_state_dir: &FsPath,
) -> Result<PathBuf> {
    let target_env_file_path = target_state_dir.join("env.sh");
    if target_env_file_path.is_file() {
        return Ok(target_env_file_path);
    }

    fs::create_dir_all(target_state_dir)
        .with_context(|| format!("failed to create {}", target_state_dir.display()))?;
    fs::copy(legacy_env_file_path, &target_env_file_path).with_context(|| {
        format!(
            "failed to copy {} -> {}",
            legacy_env_file_path.display(),
            target_env_file_path.display()
        )
    })?;

    let mut updates = HashMap::<String, String>::new();
    updates.insert(
        "STATE_DIR".into(),
        target_state_dir.to_string_lossy().to_string(),
    );
    rewrite_exported_env_file(&target_env_file_path, &updates)?;
    Ok(target_env_file_path)
}

fn legacy_env_matches_source(
    env: &HashMap<String, String>,
    source: &LocalBuildSourceInstance,
) -> bool {
    let workspace_matches = env
        .get("WORKSPACE_DIR")
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .is_some_and(|value| value == source.working_tree_root);
    let template_matches = env
        .get("TEMPLATE_ANDROID_VERSION")
        .and_then(|value| non_empty_string(value.clone()))
        .as_deref()
        == Some(source.android_version.as_str())
        && env
            .get("TEMPLATE_KERNEL_VERSION")
            .and_then(|value| non_empty_string(value.clone()))
            .as_deref()
            == Some(source.kernel_version.as_str())
        && env
            .get("TEMPLATE_BRANCH")
            .and_then(|value| extract_branch_month(value))
            .as_deref()
            == Some(source.branch_month.as_str());
    workspace_matches || template_matches
}

fn discover_local_build_templates(root: &FsPath) -> Vec<LocalBuildTemplate> {
    let mut templates = fs::read_dir(root)
        .ok()
        .into_iter()
        .flatten()
        .filter_map(|entry| {
            let entry = entry.ok()?;
            if !entry.file_type().ok()?.is_dir() {
                return None;
            }
            let name = entry.file_name().to_string_lossy().to_string();
            let suffix = name.strip_prefix("AOSP_Kernel_A")?;
            let (android_suffix, kernel_version) = suffix.split_once('_')?;
            if android_suffix.is_empty() || kernel_version.trim().is_empty() {
                return None;
            }
            Some(LocalBuildTemplate {
                name: name.clone(),
                android_version: format!("android{}", android_suffix.trim()),
                kernel_version: kernel_version.trim().to_string(),
                template_path: entry.path().to_string_lossy().to_string(),
            })
        })
        .collect::<Vec<_>>();
    templates.sort_by(|left, right| left.name.cmp(&right.name));
    templates
}

fn read_exported_env_file(path: &FsPath) -> Result<HashMap<String, String>> {
    let content =
        fs::read_to_string(path).with_context(|| format!("failed to read {}", path.display()))?;
    let mut values = HashMap::new();
    for line in content.lines() {
        let trimmed = line.trim();
        if !trimmed.starts_with("export ") {
            continue;
        }
        let Some((key, value)) = trimmed["export ".len()..].split_once('=') else {
            continue;
        };
        values.insert(
            key.trim().to_string(),
            strip_shell_quotes(value.trim()).to_string(),
        );
    }
    Ok(values)
}

fn rewrite_exported_env_file(path: &FsPath, updates: &HashMap<String, String>) -> Result<()> {
    let content =
        fs::read_to_string(path).with_context(|| format!("failed to read {}", path.display()))?;
    let mut seen = HashSet::<String>::new();
    let mut lines = Vec::new();

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("export ") {
            if let Some((key, _)) = trimmed["export ".len()..].split_once('=') {
                let clean_key = key.trim();
                if let Some(value) = updates.get(clean_key) {
                    lines.push(format!(
                        "export {}=\"{}\"",
                        clean_key,
                        shell_escape_double_quoted(value)
                    ));
                    seen.insert(clean_key.to_string());
                    continue;
                }
            }
        }
        lines.push(line.to_string());
    }

    let mut trailing = updates
        .iter()
        .filter(|(key, _)| !seen.contains(key.as_str()))
        .map(|(key, value)| format!("export {}=\"{}\"", key, shell_escape_double_quoted(value)))
        .collect::<Vec<_>>();
    trailing.sort();
    lines.extend(trailing);

    fs::write(path, format!("{}\n", lines.join("\n")))
        .with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn shell_escape_double_quoted(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('$', "\\$")
        .replace('`', "\\`")
}

fn strip_shell_quotes(value: &str) -> &str {
    value
        .strip_prefix('"')
        .and_then(|item| item.strip_suffix('"'))
        .or_else(|| {
            value
                .strip_prefix('\'')
                .and_then(|item| item.strip_suffix('\''))
        })
        .unwrap_or(value)
}

fn env_path(env: &HashMap<String, String>, key: &str) -> Option<PathBuf> {
    env.get(key)
        .and_then(|value| non_empty_string(value.to_string()).map(PathBuf::from))
}

fn non_empty_string(value: String) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed.to_string())
    }
}

fn extract_branch_month(value: &str) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.len() < 7 {
        return None;
    }
    let suffix = &trimmed[trimmed.len() - 7..];
    let bytes = suffix.as_bytes();
    if bytes.len() == 7
        && bytes[0].is_ascii_digit()
        && bytes[1].is_ascii_digit()
        && bytes[2].is_ascii_digit()
        && bytes[3].is_ascii_digit()
        && bytes[4] == b'-'
        && bytes[5].is_ascii_digit()
        && bytes[6].is_ascii_digit()
    {
        Some(suffix.to_string())
    } else {
        None
    }
}

fn latest_file_in_dir(dir: &FsPath) -> Option<PathBuf> {
    let mut latest: Option<(SystemTime, PathBuf)> = None;
    for entry in fs::read_dir(dir).ok()? {
        let entry = entry.ok()?;
        let file_type = entry.file_type().ok()?;
        if !file_type.is_file() {
            continue;
        }
        let modified = entry.metadata().ok()?.modified().ok()?;
        match &latest {
            Some((current, _)) if *current >= modified => {}
            _ => latest = Some((modified, entry.path())),
        }
    }
    latest.map(|(_, path)| path)
}

fn parse_detected_devices(output: &str) -> Vec<DetectedDevice> {
    output
        .lines()
        .skip(1)
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter_map(|line| {
            let mut parts = line.split_whitespace();
            let serial = parts.next()?.to_string();
            let status = parts.next().unwrap_or_default().to_string();
            let detail = parts.collect::<Vec<_>>().join(" ");
            Some(DetectedDevice {
                serial,
                status,
                detail,
            })
        })
        .collect()
}

fn reconcile_connection_after_detect(connection: &mut ConnectionState, devices: &[DetectedDevice]) {
    if connection.connected {
        return;
    }

    let candidates = available_device_candidates(devices);
    let current_serial = connection
        .serial
        .as_deref()
        .map(str::trim)
        .filter(|serial| !serial.is_empty());
    let serial_still_present = current_serial
        .map(|serial| candidates.iter().any(|device| device.serial == serial))
        .unwrap_or(false);

    if candidates.is_empty() {
        connection.serial = None;
        connection.mode = ConnectionMode::Disconnected;
        return;
    }

    if !serial_still_present {
        connection.serial = None;
    }
}

fn available_device_candidates(devices: &[DetectedDevice]) -> Vec<&DetectedDevice> {
    devices
        .iter()
        .filter(|device| device.status.eq_ignore_ascii_case("device"))
        .collect()
}

fn resolve_connect_serial(
    requested: Option<&str>,
    connection: &ConnectionState,
) -> Result<String, ApiError> {
    let requested = requested.unwrap_or_default().trim();
    if !requested.is_empty() {
        return Ok(requested.to_string());
    }

    let devices = available_device_candidates(&connection.last_detected);
    match devices.as_slice() {
        [device] => Ok(device.serial.clone()),
        [] => Err(ApiError::bad_request(
            "no usable adb device detected; run detect first",
        )),
        _ => Err(ApiError::bad_request(
            "multiple adb devices detected; choose a serial explicitly",
        )),
    }
}

fn reqwest_method(method: Method) -> Result<reqwest::Method, ApiError> {
    reqwest::Method::from_bytes(method.as_str().as_bytes())
        .map_err(|error| ApiError::bad_request(error.to_string()))
}

fn extract_run_ids_from_status(value: &Value) -> HashSet<u64> {
    extract_runs_from_status(value)
        .into_iter()
        .filter_map(|run| extract_run_id(&run))
        .collect()
}

fn extract_runs_from_status(value: &Value) -> Vec<Value> {
    value
        .get("runs")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default()
}

fn extract_dispatch_workflow_names(value: &Value) -> Vec<String> {
    value
        .get("dispatches")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|dispatch| {
            dispatch
                .get("workflowName")
                .and_then(Value::as_str)
                .map(str::trim)
                .filter(|name| !name.is_empty())
                .map(ToString::to_string)
        })
        .collect()
}

fn select_dispatched_runs(
    runs: &[Value],
    baseline_run_ids: &HashSet<u64>,
    dispatch_workflow_names: &[String],
) -> Vec<Value> {
    let mut expected_by_name = HashMap::<String, usize>::new();
    for workflow_name in dispatch_workflow_names {
        *expected_by_name
            .entry(workflow_name.trim().to_ascii_lowercase())
            .or_insert(0) += 1;
    }

    let mut matched = Vec::new();
    for run in runs {
        let Some(run_id) = extract_run_id(run) else {
            continue;
        };
        if baseline_run_ids.contains(&run_id) {
            continue;
        }
        let run_name = run
            .get("name")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .unwrap_or_default()
            .to_ascii_lowercase();
        let Some(remaining) = expected_by_name.get_mut(&run_name) else {
            continue;
        };
        if *remaining == 0 {
            continue;
        }
        *remaining -= 1;
        matched.push(run.clone());
        if expected_by_name.values().all(|count| *count == 0) {
            break;
        }
    }
    matched
}

fn build_gki_tracking_output(
    base_output: &[String],
    tracked_runs: &[Value],
    phase: &str,
) -> Vec<String> {
    let mut lines = Vec::new();
    lines.push(format!("## {phase}"));
    lines.extend(base_output.iter().cloned());
    if !tracked_runs.is_empty() {
        lines.push("## tracked workflow runs".into());
        for run in tracked_runs {
            lines.push(build_run_tracking_line(run));
            if let Some(url) = run.get("htmlUrl").and_then(Value::as_str) {
                let url = url.trim();
                if !url.is_empty() {
                    lines.push(format!("  {url}"));
                }
            }
        }
    }
    lines
}

fn build_run_tracking_line(run: &Value) -> String {
    let run_id = extract_run_id(run)
        .map(|id| format!("#{id}"))
        .unwrap_or_else(|| "#?".into());
    let run_name = run
        .get("name")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("workflow");
    let status = run
        .get("status")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("unknown");
    let conclusion = run
        .get("conclusion")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty());
    match conclusion {
        Some(conclusion) => format!("{run_id} {run_name} | {status} | {conclusion}"),
        None => format!("{run_id} {run_name} | {status}"),
    }
}

fn merge_build_tracking_result(
    dispatch_result: &Value,
    tracked_runs: &[Value],
    phase: &str,
) -> Value {
    let mut merged = dispatch_result.clone();
    let tracked_run_ids = tracked_runs
        .iter()
        .filter_map(extract_run_id)
        .map(Value::from)
        .collect::<Vec<_>>();
    if let Some(object) = merged.as_object_mut() {
        object.insert("trackingState".into(), Value::String(phase.into()));
        object.insert("trackedRuns".into(), Value::Array(tracked_runs.to_vec()));
        object.insert("runIds".into(), Value::Array(tracked_run_ids));
        object.insert(
            "completedRuns".into(),
            Value::from(
                tracked_runs
                    .iter()
                    .filter(|run| is_run_terminal(run))
                    .count() as u64,
            ),
        );
    }
    merged
}

fn extract_run_id(run: &Value) -> Option<u64> {
    run.get("id").and_then(Value::as_u64)
}

fn is_run_terminal(run: &Value) -> bool {
    run.get("status")
        .and_then(Value::as_str)
        .map(|status| status == "completed")
        .unwrap_or(false)
}

fn run_succeeded(run: &Value) -> bool {
    is_run_terminal(run)
        && run
            .get("conclusion")
            .and_then(Value::as_str)
            .map(|value| value == "success")
            .unwrap_or(false)
}

fn split_lines(value: &str) -> Vec<String> {
    value
        .lines()
        .map(str::trim_end)
        .filter(|line| !line.is_empty())
        .map(ToString::to_string)
        .collect()
}

fn guess_content_type(relative_path: &str) -> &'static str {
    MimeGuess::from_path(relative_path)
        .first_raw()
        .unwrap_or("application/octet-stream")
}

fn rewrite_module_webui_asset(
    module_id: &str,
    relative_path: &str,
    content_type: &str,
    bytes: &[u8],
) -> Vec<u8> {
    if !is_rewritable_webui_asset(content_type, relative_path) {
        return bytes.to_vec();
    }

    let base_prefix = format!(
        "/api/v1/runtime/modules/{}/webui/files/",
        urlencoding::encode(module_id.trim())
    );
    let mut text = String::from_utf8_lossy(bytes).into_owned();

    if is_html_content(content_type, relative_path) {
        text = inject_module_webui_html_prelude(module_id, &text, &base_prefix);
    }

    rewrite_root_asset_paths(&text, &base_prefix).into_bytes()
}

fn is_rewritable_webui_asset(content_type: &str, relative_path: &str) -> bool {
    is_html_content(content_type, relative_path)
        || is_javascript_content(content_type, relative_path)
        || is_css_content(content_type, relative_path)
}

fn is_html_content(content_type: &str, relative_path: &str) -> bool {
    let lower = content_type.to_ascii_lowercase();
    lower.contains("text/html")
        || lower.contains("application/xhtml+xml")
        || relative_path.trim().is_empty()
        || relative_path.ends_with(".html")
        || relative_path.ends_with(".htm")
}

fn is_javascript_content(content_type: &str, relative_path: &str) -> bool {
    let lower = content_type.to_ascii_lowercase();
    lower.contains("javascript")
        || lower.contains("ecmascript")
        || relative_path.ends_with(".js")
        || relative_path.ends_with(".mjs")
}

fn is_css_content(content_type: &str, relative_path: &str) -> bool {
    content_type.to_ascii_lowercase().contains("text/css") || relative_path.ends_with(".css")
}

fn inject_module_webui_html_prelude(module_id: &str, html: &str, base_prefix: &str) -> String {
    let mut prelude = String::new();
    if !html.to_ascii_lowercase().contains("<base ") {
        prelude.push_str(&format!(r#"<base href="{base_prefix}">"#));
    }
    prelude.push_str(r#"<script>"#);
    prelude.push_str(&module_webui_bridge_script(module_id));
    prelude.push_str(r#"</script>"#);

    let lower = html.to_ascii_lowercase();
    if let Some(head_index) = lower.find("<head") {
        if let Some(tag_end) = html[head_index..].find('>') {
            let insert_at = head_index + tag_end + 1;
            let mut output = String::with_capacity(html.len() + prelude.len());
            output.push_str(&html[..insert_at]);
            output.push_str(&prelude);
            output.push_str(&html[insert_at..]);
            return output;
        }
    }

    if let Some(index) = lower.find("</head>") {
        let mut output = String::with_capacity(html.len() + prelude.len());
        output.push_str(&html[..index]);
        output.push_str(&prelude);
        output.push_str(&html[index..]);
        return output;
    }

    format!("{prelude}{html}")
}

fn module_webui_bridge_script(module_id: &str) -> String {
    let api_root = format!(
        "/api/v1/runtime/modules/{}/webui",
        urlencoding::encode(module_id.trim())
    );
    let api_root_json =
        serde_json::to_string(&api_root).unwrap_or_else(|_| "\"/api/v1/runtime/modules\"".into());
    format!(
        r#"(function() {{
  if (window.ksu) return;
  const API_ROOT = {api_root_json};
  const jsonHeaders = {{ 'Content-Type': 'application/json' }};

  function parseJson(text) {{
    if (!text) return {{}};
    try {{
      return JSON.parse(text);
    }} catch (_) {{
      return {{}};
    }}
  }}

  function syncRequest(method, path, body) {{
    const xhr = new XMLHttpRequest();
    xhr.open(method, API_ROOT + '/' + path, false);
    for (const [key, value] of Object.entries(jsonHeaders)) {{
      xhr.setRequestHeader(key, value);
    }}
    xhr.send(body == null ? null : JSON.stringify(body));
    const payload = parseJson(xhr.responseText);
    if (xhr.status >= 200 && xhr.status < 300) {{
      return payload;
    }}
    throw new Error(payload.error || payload.stderr || xhr.responseText || 'KernelSU bridge request failed');
  }}

  async function asyncRequest(method, path, body) {{
    const response = await fetch(API_ROOT + '/' + path, {{
      method,
      headers: jsonHeaders,
      body: body == null ? undefined : JSON.stringify(body),
    }});
    const text = await response.text();
    const payload = parseJson(text);
    if (response.ok) {{
      return payload;
    }}
    throw new Error(payload.error || payload.stderr || text || ('KernelSU bridge request failed: ' + response.status));
  }}

  function resolveCallback(callback) {{
    if (typeof callback === 'function') return callback;
    if (typeof callback === 'string' && typeof window[callback] === 'function') {{
      return window[callback];
    }}
    return null;
  }}

  function normalizeOptions(options) {{
    if (options == null || options === '') return undefined;
    if (typeof options === 'string') {{
      try {{
        return JSON.parse(options);
      }} catch (_) {{
        return undefined;
      }}
    }}
    return options;
  }}

  function normalizeArgs(args) {{
    if (args == null || args === '') return [];
    if (Array.isArray(args)) return args;
    if (typeof args === 'string') {{
      try {{
        const parsed = JSON.parse(args);
        return Array.isArray(parsed) ? parsed : [];
      }} catch (_) {{
        return args ? [args] : [];
      }}
    }}
    return [];
  }}

  function emitSpawn(callback, payload) {{
    if (!callback) return;
    const code = Number(payload?.code ?? (payload?.success ? 0 : 1));
    const stdout = String(payload?.stdout ?? '');
    const stderr = String(payload?.stderr ?? '');
    if (typeof callback === 'function') {{
      callback(code, stdout, stderr);
      return;
    }}
    if (callback.stdout && typeof callback.stdout.emit === 'function') {{
      callback.stdout.emit('data', stdout);
    }}
    if (callback.stderr && typeof callback.stderr.emit === 'function' && stderr) {{
      callback.stderr.emit('data', stderr);
    }}
    if (typeof callback.emit === 'function') {{
      callback.emit('exit', code);
    }}
  }}

  const ksu = {{
    exec(command, optionsOrCallback, callback) {{
      if (arguments.length === 1) {{
        const payload = syncRequest('POST', 'exec', {{ command }});
        return String(payload?.stdout ?? '');
      }}
      let options;
      let cb;
      if (arguments.length === 2) {{
        cb = optionsOrCallback;
      }} else {{
        options = normalizeOptions(optionsOrCallback);
        cb = callback;
      }}
      const resolved = resolveCallback(cb);
      asyncRequest('POST', 'exec', {{ command, options }})
        .then((payload) => {{
          resolved?.(Number(payload?.code ?? (payload?.success ? 0 : 1)), String(payload?.stdout ?? ''), String(payload?.stderr ?? ''));
        }})
        .catch((error) => {{
          resolved?.(1, '', String(error?.message ?? error));
        }});
    }},
    spawn(command, args, options, callback) {{
      const resolved = typeof callback === 'string' ? window[callback] : callback;
      asyncRequest('POST', 'spawn', {{
        command,
        args: normalizeArgs(args),
        options: normalizeOptions(options),
      }})
        .then((payload) => emitSpawn(resolved, payload))
        .catch((error) => emitSpawn(resolved, {{ code: 1, stdout: '', stderr: String(error?.message ?? error) }}));
    }},
    moduleInfo() {{
      const payload = syncRequest('GET', 'module-info');
      return typeof payload?.raw === 'string' ? payload.raw : '{{}}';
    }},
    toast(message) {{
      console.info('[ABK Desktop toast]', message);
    }},
    exit() {{
      try {{
        window.close();
      }} catch (_error) {{}}
    }},
    fullScreen(_enabled) {{}},
    enableEdgeToEdge(_enabled) {{}},
  }};

  Object.defineProperty(window, 'ksu', {{
    value: ksu,
    configurable: true,
    enumerable: false,
    writable: false,
  }});
  window.KernelSU = ksu;
  window.__ABK_DESKTOP_WEBUI__ = true;
  console.info('KernelSU desktop webui bridge ready');
}})();"#,
    )
}

fn rewrite_root_asset_paths(text: &str, base_prefix: &str) -> String {
    let asset_prefix = format!("{base_prefix}assets/");
    text.replace("\"/assets/", &format!("\"{asset_prefix}"))
        .replace("'/assets/", &format!("'{asset_prefix}"))
        .replace("url(/assets/", &format!("url({asset_prefix}"))
}

fn fallback_webui_asset_target(
    request_path: &str,
    referer: Option<&str>,
) -> Option<(String, String)> {
    let relative_path = request_path.trim().trim_start_matches('/').trim();
    if relative_path.is_empty()
        || relative_path.starts_with("api/")
        || relative_path.starts_with("internal/")
    {
        return None;
    }

    let referer = referer?.trim();
    if referer.is_empty() {
        return None;
    }
    let referer_uri = Uri::try_from(referer).ok()?;
    let segments = referer_uri
        .path()
        .trim_start_matches('/')
        .split('/')
        .collect::<Vec<_>>();
    if segments.len() < 7
        || segments[0] != "api"
        || segments[1] != "v1"
        || segments[2] != "runtime"
        || segments[3] != "modules"
        || segments[5] != "webui"
        || segments[6] != "files"
    {
        return None;
    }

    let module_id = urlencoding::decode(segments[4]).ok()?.into_owned();
    if module_id.trim().is_empty() {
        return None;
    }

    Some((module_id, relative_path.to_string()))
}

fn fallback_webui_asset_candidates(relative_path: &str) -> Vec<String> {
    let clean = relative_path.trim().trim_start_matches('/').trim();
    if clean.is_empty() {
        return Vec::new();
    }

    let mut candidates = vec![clean.to_string()];
    if !clean.contains('/') && !clean.starts_with("assets/") {
        candidates.push(format!("assets/{clean}"));
    }
    candidates
}

fn forward_headers(headers: &HeaderMap) -> HeaderMap {
    let mut forwarded = HeaderMap::new();
    for (name, value) in headers {
        let skip = matches!(
            name.as_str().to_ascii_lowercase().as_str(),
            "host" | "connection" | "content-length" | "accept-encoding" | "origin" | "referer"
        );
        if !skip {
            forwarded.insert(name, value.clone());
        }
    }
    forwarded
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::local_build::LocalBuildBackendKind;

    #[test]
    fn parses_adb_detect_output() {
        let devices = parse_detected_devices(
            "List of devices attached\nABC123 device product:foo model:bar device:baz\n",
        );
        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].serial, "ABC123");
        assert_eq!(devices[0].status, "device");
    }

    #[test]
    fn clears_disconnected_serial_when_no_devices_are_detected() {
        let mut connection = ConnectionState {
            serial: Some("ABC123".into()),
            mode: ConnectionMode::AdbFallback,
            ..ConnectionState::default()
        };

        reconcile_connection_after_detect(&mut connection, &[]);

        assert_eq!(connection.serial, None);
        assert_eq!(connection.mode, ConnectionMode::Disconnected);
    }

    #[test]
    fn clears_disconnected_serial_when_previous_device_is_gone() {
        let mut connection = ConnectionState {
            serial: Some("ABC123".into()),
            mode: ConnectionMode::Disconnected,
            ..ConnectionState::default()
        };
        let devices = vec![DetectedDevice {
            serial: "XYZ789".into(),
            status: "device".into(),
            detail: String::new(),
        }];

        reconcile_connection_after_detect(&mut connection, &devices);

        assert_eq!(connection.serial, None);
        assert_eq!(connection.mode, ConnectionMode::Disconnected);
    }

    #[test]
    fn resolves_single_detected_serial_when_request_missing() {
        let connection = ConnectionState {
            last_detected: vec![DetectedDevice {
                serial: "ABC123".into(),
                status: "device".into(),
                detail: "product:foo".into(),
            }],
            ..ConnectionState::default()
        };

        let serial = resolve_connect_serial(None, &connection).unwrap();
        assert_eq!(serial, "ABC123");
    }

    #[test]
    fn rejects_ambiguous_detected_serials() {
        let connection = ConnectionState {
            last_detected: vec![
                DetectedDevice {
                    serial: "ABC123".into(),
                    status: "device".into(),
                    detail: String::new(),
                },
                DetectedDevice {
                    serial: "XYZ789".into(),
                    status: "device".into(),
                    detail: String::new(),
                },
            ],
            ..ConnectionState::default()
        };

        let error = resolve_connect_serial(None, &connection).unwrap_err();
        assert_eq!(error.status, StatusCode::BAD_REQUEST);
        assert!(error.message.contains("multiple adb devices"));
    }

    #[test]
    fn persists_cli_token_to_config_path() {
        let temp_root = std::env::temp_dir().join(format!("abk-test-{}", Uuid::new_v4()));
        let config_path = temp_root.join("config.json");

        persist_cli_token_to_path(&config_path, "token-123").unwrap();
        let config = read_cli_config_json_from_path(&config_path).unwrap();

        assert_eq!(
            config.get("token").and_then(Value::as_str),
            Some("token-123")
        );

        fs::remove_dir_all(temp_root).ok();
    }

    #[test]
    fn select_dispatched_runs_ignores_baseline_ids() {
        let runs = vec![
            json!({
                "id": 101_u64,
                "name": "自定义内核构建",
                "status": "queued",
            }),
            json!({
                "id": 100_u64,
                "name": "自定义内核构建",
                "status": "completed",
                "conclusion": "success",
            }),
        ];
        let baseline = HashSet::from([100_u64]);

        let matched = select_dispatched_runs(&runs, &baseline, &["自定义内核构建".into()]);

        assert_eq!(matched.len(), 1);
        assert_eq!(extract_run_id(&matched[0]), Some(101));
    }

    #[test]
    fn select_dispatched_runs_respects_duplicate_workflow_names() {
        let runs = vec![
            json!({
                "id": 203_u64,
                "name": "Matrix Build",
                "status": "queued",
            }),
            json!({
                "id": 202_u64,
                "name": "Matrix Build",
                "status": "in_progress",
            }),
            json!({
                "id": 201_u64,
                "name": "Other Build",
                "status": "queued",
            }),
        ];

        let matched = select_dispatched_runs(
            &runs,
            &HashSet::new(),
            &["Matrix Build".into(), "Matrix Build".into()],
        );

        assert_eq!(matched.len(), 2);
        assert_eq!(extract_run_id(&matched[0]), Some(203));
        assert_eq!(extract_run_id(&matched[1]), Some(202));
    }

    #[test]
    fn rewrites_module_webui_html_assets_with_base_prefix() {
        let rewritten = rewrite_module_webui_asset(
            "abi_bridge",
            "",
            "text/html; charset=utf-8",
            br#"<!doctype html><html><head><script type="module" src="/assets/index.js"></script><link rel="stylesheet" href="assets/index.css"></head><body></body></html>"#,
        );
        let html = String::from_utf8(rewritten).unwrap();

        assert!(html.contains(r#"<base href="/api/v1/runtime/modules/abi_bridge/webui/files/">"#));
        assert!(html.find("<base ").unwrap() < html.find("<script").unwrap());
        assert!(html
            .contains(r#"src="/api/v1/runtime/modules/abi_bridge/webui/files/assets/index.js""#));
        assert!(html.contains(r#"href="assets/index.css""#));
    }

    #[test]
    fn rewrites_module_webui_javascript_root_asset_paths() {
        let rewritten = rewrite_module_webui_asset(
            "abi_bridge",
            "assets/index.js",
            "application/javascript",
            br#"const a="/assets/index.css";const b='/assets/fallback.js';"#,
        );
        let script = String::from_utf8(rewritten).unwrap();

        assert!(
            script.contains(r#""/api/v1/runtime/modules/abi_bridge/webui/files/assets/index.css""#)
        );
        assert!(script
            .contains(r#"'/api/v1/runtime/modules/abi_bridge/webui/files/assets/fallback.js'"#));
    }

    #[test]
    fn resolves_root_asset_fallback_from_webui_referer() {
        let resolved = fallback_webui_asset_target(
            "/index-BphXklzb.js",
            Some("http://127.0.0.1:38765/api/v1/runtime/modules/abi_bridge/webui/files"),
        );

        assert_eq!(
            resolved,
            Some(("abi_bridge".into(), "index-BphXklzb.js".into()))
        );
    }

    #[test]
    fn ignores_non_webui_referer_for_root_asset_fallback() {
        let resolved =
            fallback_webui_asset_target("/index-BphXklzb.js", Some("http://127.0.0.1:38765/home"));

        assert_eq!(resolved, None);
    }

    #[test]
    fn expands_root_asset_fallback_to_assets_directory() {
        assert_eq!(
            fallback_webui_asset_candidates("index-BphXklzb.js"),
            vec![
                "index-BphXklzb.js".to_string(),
                "assets/index-BphXklzb.js".to_string(),
            ]
        );
        assert_eq!(
            fallback_webui_asset_candidates("assets/index-BphXklzb.js"),
            vec!["assets/index-BphXklzb.js".to_string()]
        );
    }

    #[test]
    fn migrates_legacy_local_build_env_to_active_source_state_dir() {
        let temp_root = std::env::temp_dir().join(format!("abk-test-{}", Uuid::new_v4()));
        let script_root = temp_root.join("script");
        let legacy_state_dir = script_root.join(".local-build");
        let legacy_env_path = legacy_state_dir.join("env.sh");
        let source_cache_root = temp_root
            .join("profile")
            .join("sources")
            .join("android13-5.15@2025-03");
        let source_workspace_root = temp_root.join("workspace").join("android13-5.15@2025-03");
        let target_state_dir = source_cache_root.join(".local-build");
        fs::create_dir_all(&legacy_state_dir).unwrap();
        fs::write(
            &legacy_env_path,
            format!(
                "export ROOT_DIR='{}'\nexport STATE_DIR='{}'\nexport WORKSPACE_DIR='{}'\nexport TEMPLATE_ANDROID_VERSION='android13'\nexport TEMPLATE_KERNEL_VERSION='5.15'\nexport TEMPLATE_BRANCH='common-android13-5.15-2025-03'\n",
                script_root.display(),
                legacy_state_dir.display(),
                source_workspace_root.display()
            ),
        )
        .unwrap();

        let source = LocalBuildSourceInstance {
            id: "android13-5.15@2025-03".into(),
            display_name: "android13/5.15@2025-03".into(),
            kernel_line_id: "android13/5.15".into(),
            android_version: "android13".into(),
            kernel_version: "5.15".into(),
            branch_month: "2025-03".into(),
            cache_root: source_cache_root.to_string_lossy().to_string(),
            working_tree_root: source_workspace_root.to_string_lossy().to_string(),
            state: "ready".into(),
            created_at_ms: 0,
            updated_at_ms: 0,
            last_synced_at_ms: Some(1),
            active_backend_kind: Some(LocalBuildBackendKind::Docker),
            last_task_id: None,
            last_error: None,
            materialized: None,
        };

        let (state_dir, env_file_path) =
            resolve_local_build_status_paths(&legacy_state_dir, &legacy_env_path, Some(&source))
                .unwrap();

        assert_eq!(state_dir, target_state_dir);
        assert_eq!(env_file_path, target_state_dir.join("env.sh"));
        assert!(env_file_path.is_file());

        let env = read_exported_env_file(&env_file_path).unwrap();
        assert_eq!(
            env.get("STATE_DIR").map(String::as_str),
            Some(target_state_dir.to_string_lossy().as_ref())
        );
        assert_eq!(
            env.get("WORKSPACE_DIR").map(String::as_str),
            Some(source_workspace_root.to_string_lossy().as_ref())
        );

        fs::remove_dir_all(temp_root).ok();
    }
}
