use crate::commands::{
    build_local_init_command, build_local_rebuild_command, repo_root, wrap_command_with_sudo,
    CommandSpec,
};
use crate::local_build_paths::{
    ensure_local_build_root_materialized, load_local_build_path_settings,
    normalize_optional_dir_setting, persist_local_build_path_settings,
    resolve_local_build_profile_store_dir, resolve_local_build_root,
    resolve_local_build_workspace_dir, LocalBuildPathSettings,
};
use crate::proxy::ProxySettings;
use crate::{latest_file_in_dir, BuildGkiRequest};
use anyhow::{anyhow, Context, Result};
#[cfg(unix)]
use libc::{getgid, getuid};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use uuid::Uuid;

const STORE_SCHEMA_VERSION: u32 = 1;
const DEFAULT_CONTAINER_IMAGE: &str =
    "ghcr.io/xingguangcuican6666/abk:2026-07-17-rsync-fix";
const DEFAULT_WSL_ROOTFS_TAR_URL: &str =
    "https://github.com/xingguangcuican6666/ABK/releases/download/v1.0.0-wsl/wsl-ubuntu-abk.tar";
const CONTAINER_HOME_MOUNT_TARGET: &str = "/tmp/abk-home";
const DOCKER_CONTAINER_HOST_ALIAS: &str = "host.docker.internal";
const DOCKER_CONTAINER_HOST_MAP: &str = "host.docker.internal:host-gateway";
const PODMAN_CONTAINER_HOST_ALIAS: &str = "host.containers.internal";
const DEFAULT_WSL_DISTRO_NAME: &str = "ABK-Desktop";

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[serde(rename_all = "snake_case")]
pub enum LocalBuildBackendKind {
    Docker,
    Podman,
    Wsl,
    Script,
}

impl LocalBuildBackendKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Docker => "docker",
            Self::Podman => "podman",
            Self::Wsl => "wsl",
            Self::Script => "script",
        }
    }

    pub fn display_name(self) -> &'static str {
        match self {
            Self::Docker => "Docker",
            Self::Podman => "Podman",
            Self::Wsl => "WSL",
            Self::Script => "Script adapter",
        }
    }

    pub fn family(self) -> &'static str {
        match self {
            Self::Docker | Self::Podman => "container",
            Self::Wsl => "wsl",
            Self::Script => "script",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendCapabilities {
    pub family: String,
    pub host_owned_paths: bool,
    pub supports_source_sync: bool,
    pub supports_build_execution: bool,
    pub supports_profile_projection: bool,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendDescriptor {
    pub kind: LocalBuildBackendKind,
    pub label: String,
    pub available: bool,
    pub is_global_default: bool,
    pub install_supported: bool,
    pub install_label: Option<String>,
    pub install_detail: Option<String>,
    pub authorization_required: bool,
    pub authorization_kind: Option<String>,
    pub authorization_message: Option<String>,
    pub capabilities: LocalBuildBackendCapabilities,
    pub detail: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SupportedKernelLine {
    pub id: String,
    pub android_version: String,
    pub kernel_version: String,
    pub display_name: String,
    pub branch_month_format: String,
    pub script_template_path: String,
    pub script_template_available: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildCatalogResponse {
    pub kernel_lines: Vec<SupportedKernelLine>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendsResponse {
    pub global_default_backend_kind: LocalBuildBackendKind,
    pub backends: Vec<LocalBuildBackendDescriptor>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSettings {
    pub global_default_backend_kind: LocalBuildBackendKind,
    pub active_source_instance_id: Option<String>,
    pub script_root_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub profile_store_dir: Option<String>,
}

impl Default for LocalBuildSettings {
    fn default() -> Self {
        Self {
            global_default_backend_kind: LocalBuildBackendKind::Script,
            active_source_instance_id: None,
            script_root_dir: None,
            workspace_dir: None,
            profile_store_dir: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildMaterializedState {
    pub script_root: Option<String>,
    pub env_file_path: Option<String>,
    pub state_dir: Option<String>,
    pub sources_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub artifacts_dir: Option<String>,
    pub logs_dir: Option<String>,
    pub cache_dir: Option<String>,
    pub kernel_root: Option<String>,
    pub template_name: Option<String>,
    pub template_root: Option<String>,
    pub template_branch: Option<String>,
    pub template_common_branch: Option<String>,
    pub sub_level: Option<String>,
    pub os_patch_level: Option<String>,
    pub latest_log_path: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSourceInstance {
    pub id: String,
    pub display_name: String,
    pub kernel_line_id: String,
    pub android_version: String,
    pub kernel_version: String,
    pub branch_month: String,
    pub cache_root: String,
    pub working_tree_root: String,
    pub state: String,
    pub created_at_ms: u64,
    pub updated_at_ms: u64,
    pub last_synced_at_ms: Option<u64>,
    pub active_backend_kind: Option<LocalBuildBackendKind>,
    pub last_task_id: Option<String>,
    pub last_error: Option<String>,
    pub materialized: Option<LocalBuildMaterializedState>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildProfile {
    pub id: String,
    pub name: String,
    pub source_instance_id: String,
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub build: BuildGkiRequest,
    pub created_at_ms: u64,
    pub updated_at_ms: u64,
    pub last_built_at_ms: Option<u64>,
    pub last_task_id: Option<String>,
    pub last_error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildProfilesResponse {
    pub settings: LocalBuildSettings,
    pub profiles: Vec<LocalBuildProfile>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSourceInstancesResponse {
    pub settings: LocalBuildSettings,
    pub source_instances: Vec<LocalBuildSourceInstance>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildArtifactEntry {
    pub id: String,
    pub task_id: String,
    pub profile_id: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: LocalBuildBackendKind,
    pub path: String,
    pub file_name: String,
    pub exists: bool,
    pub created_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildArtifactsResponse {
    pub artifacts: Vec<LocalBuildArtifactEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildLogEntry {
    pub id: String,
    pub task_id: String,
    pub profile_id: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: LocalBuildBackendKind,
    pub path: String,
    pub file_name: String,
    pub exists: bool,
    pub created_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildLogsResponse {
    pub logs: Vec<LocalBuildLogEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateLocalBuildSourceInstanceRequest {
    pub kernel_line_id: String,
    pub branch_month: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SyncLocalBuildSourceInstanceRequest {
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub force: Option<bool>,
    pub skip_deps: Option<bool>,
    pub sudo_password: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveLocalBuildProfileRequest {
    pub id: Option<String>,
    pub name: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub build: Option<BuildGkiRequest>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct BuildLocalBuildProfileRequest {
    pub clean_out: Option<bool>,
    pub reseed: Option<bool>,
    pub no_package: Option<bool>,
    pub sudo_password: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct InstallLocalBuildBackendRequest {
    pub sudo_password: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateLocalBuildSettingsRequest {
    pub global_default_backend_kind: Option<LocalBuildBackendKind>,
    pub script_root_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub profile_store_dir: Option<String>,
}

#[derive(Debug, Clone)]
pub struct LocalBuildSourceSyncPlan {
    pub source_instance: LocalBuildSourceInstance,
    pub backend_kind: LocalBuildBackendKind,
    pub command: CommandSpec,
}

#[derive(Debug, Clone)]
pub struct LocalBuildProfileBuildPlan {
    pub profile: LocalBuildProfile,
    pub source_instance: LocalBuildSourceInstance,
    pub backend_kind: LocalBuildBackendKind,
    pub activation_command: Option<CommandSpec>,
    pub build_command: CommandSpec,
    pub build_request: BuildGkiRequest,
}

#[derive(Debug, Clone)]
pub enum LocalBuildBackendInstallAction {
    PullContainerImage { command: CommandSpec },
    RestoreScriptAssets,
    ImportWslRootfs { command: CommandSpec },
}

#[derive(Debug, Clone)]
pub struct LocalBuildBackendInstallPlan {
    pub backend: LocalBuildBackendDescriptor,
    pub action: LocalBuildBackendInstallAction,
}

#[derive(Debug, Serialize, Deserialize)]
struct LocalBuildStore {
    schema_version: u32,
    settings: LocalBuildSettings,
    source_instances: Vec<LocalBuildSourceInstance>,
    profiles: Vec<LocalBuildProfile>,
    artifacts: Vec<LocalBuildArtifactEntry>,
    logs: Vec<LocalBuildLogEntry>,
}

#[derive(Debug, Clone)]
struct BackendProbe {
    available: bool,
    install_supported: bool,
    install_label: Option<String>,
    install_detail: Option<String>,
    detail: Option<String>,
    authorization_required: bool,
    authorization_kind: Option<String>,
    authorization_message: Option<String>,
}

impl Default for LocalBuildStore {
    fn default() -> Self {
        Self {
            schema_version: STORE_SCHEMA_VERSION,
            settings: LocalBuildSettings::default(),
            source_instances: Vec::new(),
            profiles: Vec::new(),
            artifacts: Vec::new(),
            logs: Vec::new(),
        }
    }
}

#[derive(Debug)]
pub struct LocalBuildManager {
    repo_root: PathBuf,
    config_path: PathBuf,
    path_settings: LocalBuildPathSettings,
    data_root: PathBuf,
    store_path: PathBuf,
    store: LocalBuildStore,
}

impl LocalBuildManager {
    pub fn new(repo_root: PathBuf) -> Result<Self> {
        let path_settings = load_local_build_path_settings(&repo_root)?;
        let _ = ensure_local_build_root_materialized(&repo_root, &path_settings)?;
        let config_path = crate::local_build_paths::local_build_config_path(&repo_root);
        let data_root = resolve_local_build_profile_store_dir(&repo_root, &path_settings);
        fs::create_dir_all(&data_root)
            .with_context(|| format!("failed to create {}", data_root.display()))?;
        let store_path = data_root.join("state.json");
        let store = load_store(&store_path)?;
        let mut manager = Self {
            repo_root,
            config_path,
            path_settings,
            data_root,
            store_path,
            store,
        };
        let roots_changed = manager.refresh_source_instance_roots();
        if !manager
            .collect_backend_descriptors()
            .iter()
            .any(|backend| backend.kind == manager.store.settings.global_default_backend_kind)
        {
            manager.store.settings.global_default_backend_kind = manager.default_backend_kind();
            manager.persist()?;
        } else if roots_changed {
            manager.persist()?;
        }
        Ok(manager)
    }

    fn export_settings(&self) -> LocalBuildSettings {
        LocalBuildSettings {
            global_default_backend_kind: self.store.settings.global_default_backend_kind,
            active_source_instance_id: self.store.settings.active_source_instance_id.clone(),
            script_root_dir: self.path_settings.script_root_dir.clone(),
            workspace_dir: self.path_settings.workspace_dir.clone(),
            profile_store_dir: self.path_settings.profile_store_dir.clone(),
        }
    }

    fn script_root(&self) -> PathBuf {
        resolve_local_build_root(&self.repo_root, &self.path_settings)
    }

    fn workspace_base_dir(&self) -> PathBuf {
        resolve_local_build_workspace_dir(&self.script_root(), &self.path_settings)
    }

    fn refresh_source_instance_roots(&mut self) -> bool {
        let mut changed = false;
        let workspace_base_dir = self.workspace_base_dir();
        for source in &mut self.store.source_instances {
            let next_cache_root = self.data_root.join("sources").join(&source.id);
            let next_working_tree_root = workspace_base_dir.join(&source.id);
            let next_cache_root_text = next_cache_root.to_string_lossy().to_string();
            let next_working_tree_root_text = next_working_tree_root.to_string_lossy().to_string();
            if source.cache_root != next_cache_root_text
                || source.working_tree_root != next_working_tree_root_text
            {
                changed = true;
                source.cache_root = next_cache_root_text;
                source.working_tree_root = next_working_tree_root_text;
                source.materialized = None;
            }
        }
        changed
    }

    fn source_cache_root(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        if source.cache_root.trim().is_empty() {
            self.data_root.join("sources").join(&source.id)
        } else {
            PathBuf::from(source.cache_root.trim())
        }
    }

    fn source_workspace_dir(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        if source.working_tree_root.trim().is_empty() {
            self.workspace_base_dir().join(&source.id)
        } else {
            PathBuf::from(source.working_tree_root.trim())
        }
    }

    fn source_state_dir(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        self.source_cache_root(source).join(".local-build")
    }

    fn source_sources_dir(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        self.source_cache_root(source).join("sources")
    }

    fn source_template_root(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        self.source_cache_root(source).join("template")
    }

    fn source_container_home_dir(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        self.source_cache_root(source).join("container-home")
    }

    fn source_env_file_path(&self, source: &LocalBuildSourceInstance) -> PathBuf {
        self.source_state_dir(source).join("env.sh")
    }

    fn source_command_envs(&self, source: &LocalBuildSourceInstance) -> Vec<(String, String)> {
        vec![
            (
                "ABK_LOCAL_BUILD_SOURCE_INSTANCE_ID".into(),
                source.id.clone(),
            ),
            (
                "ABK_LOCAL_BUILD_STATE_DIR".into(),
                self.source_state_dir(source).to_string_lossy().to_string(),
            ),
            (
                "ABK_LOCAL_BUILD_SOURCES_DIR".into(),
                self.source_sources_dir(source)
                    .to_string_lossy()
                    .to_string(),
            ),
            (
                "ABK_LOCAL_BUILD_WORKSPACE_DIR".into(),
                self.source_workspace_dir(source)
                    .to_string_lossy()
                    .to_string(),
            ),
            (
                "ABK_LOCAL_BUILD_TEMPLATE_ROOT".into(),
                self.source_template_root(source)
                    .to_string_lossy()
                    .to_string(),
            ),
        ]
    }

    fn with_source_command_envs(
        &self,
        mut command: CommandSpec,
        source: &LocalBuildSourceInstance,
    ) -> CommandSpec {
        for (key, value) in self.source_command_envs(source) {
            if let Some(existing) = command.env.iter_mut().find(|(item, _)| item == &key) {
                existing.1 = value;
            } else {
                command.env.push((key, value));
            }
        }
        command
    }

    fn build_script_init_command(
        &self,
        source: &LocalBuildSourceInstance,
        force: bool,
        skip_deps: bool,
    ) -> Result<CommandSpec> {
        let command = build_local_init_command(
            &source.android_version,
            &source.kernel_version,
            &source.branch_month,
            force,
            skip_deps,
        )?;
        Ok(self.with_source_command_envs(command, source))
    }

    fn build_script_rebuild_command(
        &self,
        source: &LocalBuildSourceInstance,
        clean_out: bool,
        reseed: bool,
        no_package: bool,
    ) -> CommandSpec {
        let command = build_local_rebuild_command(clean_out, reseed, no_package);
        self.with_source_command_envs(command, source)
    }

    fn inspect_source_materialized_env(
        &self,
        source: &LocalBuildSourceInstance,
    ) -> Option<HashMap<String, String>> {
        let env_file_path = self.source_env_file_path(source);
        if !env_file_path.is_file() {
            let legacy_env_file_path = self.script_root().join(".local-build").join("env.sh");
            if legacy_env_file_path.is_file() {
                let legacy_env = read_exported_env_file(&legacy_env_file_path).ok()?;
                if legacy_env_matches_source(&legacy_env, source) {
                    let target_state_dir = self.source_state_dir(source);
                    fs::create_dir_all(&target_state_dir).ok()?;
                    fs::copy(&legacy_env_file_path, &env_file_path).ok()?;
                    let mut updates = HashMap::<String, String>::new();
                    updates.insert(
                        "STATE_DIR".into(),
                        target_state_dir.to_string_lossy().to_string(),
                    );
                    rewrite_exported_env_file(&env_file_path, &updates).ok()?;
                } else {
                    return None;
                }
            } else {
                return None;
            }
        }
        read_exported_env_file(&env_file_path).ok()
    }

    fn inspect_source_materialized_state(
        &self,
        source: &LocalBuildSourceInstance,
    ) -> Option<LocalBuildMaterializedState> {
        let env = self.inspect_source_materialized_env(source)?;
        let state_dir = self.source_state_dir(source);
        let workspace_dir =
            env_path(&env, "WORKSPACE_DIR").unwrap_or(self.source_workspace_dir(source));
        let sources_dir = env_path(&env, "SOURCES_DIR").unwrap_or(self.source_sources_dir(source));
        let artifacts_dir =
            env_path(&env, "ARTIFACTS_DIR").unwrap_or(workspace_dir.join("artifacts"));
        let logs_dir = env_path(&env, "LOGS_DIR").unwrap_or(workspace_dir.join("logs"));
        let cache_dir = env_path(&env, "CACHE_DIR").unwrap_or(workspace_dir.join("cache"));
        let kernel_root = env_path(&env, "KERNEL_ROOT").unwrap_or(workspace_dir.join("kernel"));
        let latest_log_path =
            latest_file_in_dir(&logs_dir).map(|path| path.to_string_lossy().to_string());
        Some(LocalBuildMaterializedState {
            script_root: Some(self.script_root().to_string_lossy().to_string()),
            env_file_path: Some(
                self.source_env_file_path(source)
                    .to_string_lossy()
                    .to_string(),
            ),
            state_dir: Some(state_dir.to_string_lossy().to_string()),
            sources_dir: Some(sources_dir.to_string_lossy().to_string()),
            workspace_dir: Some(workspace_dir.to_string_lossy().to_string()),
            artifacts_dir: Some(artifacts_dir.to_string_lossy().to_string()),
            logs_dir: Some(logs_dir.to_string_lossy().to_string()),
            cache_dir: Some(cache_dir.to_string_lossy().to_string()),
            kernel_root: Some(kernel_root.to_string_lossy().to_string()),
            template_name: env.get("TEMPLATE_NAME").cloned().and_then(non_empty_string),
            template_root: env.get("TEMPLATE_ROOT").cloned().and_then(non_empty_string),
            template_branch: env
                .get("TEMPLATE_BRANCH")
                .cloned()
                .and_then(non_empty_string),
            template_common_branch: env
                .get("TEMPLATE_COMMON_BRANCH")
                .cloned()
                .and_then(non_empty_string),
            sub_level: env.get("SUB_LEVEL").cloned().and_then(non_empty_string),
            os_patch_level: env
                .get("OS_PATCH_LEVEL")
                .cloned()
                .and_then(non_empty_string),
            latest_log_path,
        })
    }

    pub fn list_backends(&self) -> LocalBuildBackendsResponse {
        LocalBuildBackendsResponse {
            global_default_backend_kind: self.store.settings.global_default_backend_kind,
            backends: self.collect_backend_descriptors(),
        }
    }

    pub fn catalog(&self) -> LocalBuildCatalogResponse {
        LocalBuildCatalogResponse {
            kernel_lines: supported_kernel_lines(&self.script_root()),
        }
    }

    pub fn update_settings(
        &mut self,
        request: UpdateLocalBuildSettingsRequest,
    ) -> Result<LocalBuildSettings> {
        if let Some(kind) = request.global_default_backend_kind {
            if !self
                .collect_backend_descriptors()
                .iter()
                .any(|backend| backend.kind == kind)
            {
                return Err(anyhow!("unsupported backend kind {}", kind.as_str()));
            }
            self.store.settings.global_default_backend_kind = kind;
        }

        let next_path_settings = LocalBuildPathSettings {
            script_root_dir: normalize_optional_dir_setting(request.script_root_dir)?,
            workspace_dir: normalize_optional_dir_setting(request.workspace_dir)?,
            profile_store_dir: normalize_optional_dir_setting(request.profile_store_dir)?,
        };
        let _ = ensure_local_build_root_materialized(&self.repo_root, &next_path_settings)?;
        let next_data_root =
            resolve_local_build_profile_store_dir(&self.repo_root, &next_path_settings);
        if next_data_root != self.data_root {
            fs::create_dir_all(&next_data_root)
                .with_context(|| format!("failed to create {}", next_data_root.display()))?;
            self.data_root = next_data_root;
            self.store_path = self.data_root.join("state.json");
        }
        self.path_settings = next_path_settings;
        self.refresh_source_instance_roots();
        persist_local_build_path_settings(&self.repo_root, &self.path_settings)
            .with_context(|| format!("failed to write {}", self.config_path.display()))?;
        self.persist()?;
        Ok(self.export_settings())
    }

    pub fn list_source_instances(&self) -> LocalBuildSourceInstancesResponse {
        let mut source_instances = self.store.source_instances.clone();
        source_instances.sort_by(|left, right| right.updated_at_ms.cmp(&left.updated_at_ms));
        LocalBuildSourceInstancesResponse {
            settings: self.export_settings(),
            source_instances,
        }
    }

    pub fn create_source_instance(
        &mut self,
        request: CreateLocalBuildSourceInstanceRequest,
    ) -> Result<LocalBuildSourceInstance> {
        let kernel_line = find_kernel_line(&self.script_root(), &request.kernel_line_id)?;
        let branch_month = normalize_branch_month(&request.branch_month)?;
        let id = source_instance_id(&kernel_line.id, &branch_month);
        if let Some(existing) = self
            .store
            .source_instances
            .iter()
            .find(|source| source.id == id)
            .cloned()
        {
            return Ok(existing);
        }
        let now = now_ms();
        let cache_root = self.data_root.join("sources").join(&id);
        let working_tree_root = self.workspace_base_dir().join(&id);
        let source_instance = LocalBuildSourceInstance {
            id: id.clone(),
            display_name: format!(
                "{}/{}@{}",
                kernel_line.android_version, kernel_line.kernel_version, branch_month
            ),
            kernel_line_id: kernel_line.id.clone(),
            android_version: kernel_line.android_version.clone(),
            kernel_version: kernel_line.kernel_version.clone(),
            branch_month,
            cache_root: cache_root.to_string_lossy().to_string(),
            working_tree_root: working_tree_root.to_string_lossy().to_string(),
            state: "draft".into(),
            created_at_ms: now,
            updated_at_ms: now,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };
        self.store.source_instances.push(source_instance.clone());
        self.persist()?;
        Ok(source_instance)
    }

    pub fn plan_source_sync(
        &mut self,
        source_instance_id: &str,
        request: &SyncLocalBuildSourceInstanceRequest,
    ) -> Result<LocalBuildSourceSyncPlan> {
        let backend_kind = request
            .backend_kind
            .unwrap_or(self.store.settings.global_default_backend_kind);
        let backend = self.backend_descriptor(backend_kind);
        if !backend.available {
            return Err(anyhow!("{} is not available on this host", backend.label));
        }
        if !backend.capabilities.supports_source_sync {
            return Err(anyhow!(
                "{} source sync is not implemented yet",
                backend.label
            ));
        }
        let source_instance = self.require_source_instance(source_instance_id)?;
        let command = match backend_kind {
            LocalBuildBackendKind::Script => self.build_script_init_command(
                &source_instance,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
            )?,
            LocalBuildBackendKind::Docker => self.build_container_init_command(
                "docker",
                &source_instance,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
                None,
            )?,
            LocalBuildBackendKind::Podman => self.build_container_init_command(
                "podman",
                &source_instance,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
                None,
            )?,
            _ => {
                return Err(anyhow!(
                    "{} source sync is not implemented yet",
                    backend.label
                ))
            }
        };
        let command =
            authorize_command_if_needed(command, &backend, request.sudo_password.as_deref())?;
        {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "syncing".into();
            source_instance.updated_at_ms = now_ms();
            source_instance.last_error = None;
        }
        self.persist()?;
        Ok(LocalBuildSourceSyncPlan {
            source_instance: self.require_source_instance(source_instance_id)?,
            backend_kind,
            command,
        })
    }

    pub fn finalize_source_sync(
        &mut self,
        source_instance_id: &str,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
    ) -> Result<LocalBuildSourceInstance> {
        let source_instance = self.require_source_instance(source_instance_id)?;
        let materialized = self.inspect_source_materialized_state(&source_instance);
        let now = now_ms();
        let active_source_instance_id = {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "ready".into();
            source_instance.updated_at_ms = now;
            source_instance.last_synced_at_ms = Some(now);
            source_instance.active_backend_kind = Some(backend_kind);
            source_instance.last_task_id = Some(task_id.to_string());
            source_instance.last_error = None;
            if let Some(materialized) = materialized {
                source_instance.materialized = Some(materialized);
            }
            source_instance.id.clone()
        };
        self.store.settings.active_source_instance_id = Some(active_source_instance_id);
        self.persist()?;
        self.require_source_instance(source_instance_id)
    }

    pub fn fail_source_sync(
        &mut self,
        source_instance_id: &str,
        task_id: &str,
        error: &str,
    ) -> Result<LocalBuildSourceInstance> {
        {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "failed".into();
            source_instance.updated_at_ms = now_ms();
            source_instance.last_task_id = Some(task_id.to_string());
            source_instance.last_error = Some(error.trim().to_string());
        }
        self.persist()?;
        self.require_source_instance(source_instance_id)
    }

    pub fn list_profiles(&self) -> LocalBuildProfilesResponse {
        let mut profiles = self.store.profiles.clone();
        profiles.sort_by(|left, right| right.updated_at_ms.cmp(&left.updated_at_ms));
        LocalBuildProfilesResponse {
            settings: self.export_settings(),
            profiles,
        }
    }

    pub fn save_profile(
        &mut self,
        request: SaveLocalBuildProfileRequest,
    ) -> Result<LocalBuildProfile> {
        let source_instance = self.require_source_instance(&request.source_instance_id)?;
        let now = now_ms();
        let normalized_build = normalize_build_request(
            request
                .build
                .unwrap_or_else(|| default_build_request_for_source(&source_instance)),
            &source_instance,
        );
        if let Some(profile_id) = request.id.as_deref() {
            {
                let profile = self.require_profile_mut(profile_id)?;
                profile.name = request
                    .name
                    .unwrap_or_else(|| profile.name.clone())
                    .trim()
                    .to_string();
                profile.source_instance_id = source_instance.id.clone();
                profile.backend_kind = request.backend_kind;
                profile.build = normalized_build.clone();
                profile.updated_at_ms = now;
                profile.last_error = None;
            }
            self.materialize_profile_environment(&source_instance.id, &normalized_build)?;
            self.persist()?;
            return self.require_profile(profile_id);
        }

        let source_display = format!(
            "{}/{}@{}",
            source_instance.android_version,
            source_instance.kernel_version,
            source_instance.branch_month
        );
        let profile = LocalBuildProfile {
            id: Uuid::new_v4().to_string(),
            name: request
                .name
                .unwrap_or_else(|| format!("Profile {}", source_display))
                .trim()
                .to_string(),
            source_instance_id: source_instance.id.clone(),
            backend_kind: request.backend_kind,
            build: normalized_build.clone(),
            created_at_ms: now,
            updated_at_ms: now,
            last_built_at_ms: None,
            last_task_id: None,
            last_error: None,
        };
        self.store.profiles.push(profile.clone());
        self.materialize_profile_environment(&source_instance.id, &normalized_build)?;
        self.persist()?;
        Ok(profile)
    }

    pub fn plan_profile_build(
        &mut self,
        profile_id: &str,
        request: &BuildLocalBuildProfileRequest,
    ) -> Result<LocalBuildProfileBuildPlan> {
        let profile = self.require_profile(profile_id)?;
        let source_instance = self.require_source_instance(&profile.source_instance_id)?;
        if source_instance.last_synced_at_ms.is_none() {
            return Err(anyhow!(
                "source instance {} has not been synced yet",
                source_instance.display_name
            ));
        }
        let backend_kind = profile
            .backend_kind
            .unwrap_or(self.store.settings.global_default_backend_kind);
        let backend = self.backend_descriptor(backend_kind);
        if !backend.available {
            return Err(anyhow!("{} is not available on this host", backend.label));
        }
        if !backend.capabilities.supports_build_execution {
            return Err(anyhow!(
                "{} build execution is not implemented yet",
                backend.label
            ));
        }
        let build_request = normalize_build_request(profile.build.clone(), &source_instance);
        self.materialize_profile_environment(&source_instance.id, &build_request)?;
        let build_command = match backend_kind {
            LocalBuildBackendKind::Script => self.build_script_rebuild_command(
                &source_instance,
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
            ),
            LocalBuildBackendKind::Docker => self.build_container_rebuild_command(
                "docker",
                &source_instance,
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
                &build_request,
            )?,
            LocalBuildBackendKind::Podman => self.build_container_rebuild_command(
                "podman",
                &source_instance,
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
                &build_request,
            )?,
            _ => {
                return Err(anyhow!(
                    "{} build execution is not implemented yet",
                    backend.label
                ))
            }
        };
        let build_command =
            authorize_command_if_needed(build_command, &backend, request.sudo_password.as_deref())?;
        {
            let profile_mut = self.require_profile_mut(profile_id)?;
            profile_mut.updated_at_ms = now_ms();
            profile_mut.last_error = None;
        }
        self.persist()?;
        Ok(LocalBuildProfileBuildPlan {
            profile,
            source_instance,
            backend_kind,
            activation_command: None,
            build_command,
            build_request,
        })
    }

    pub fn materialize_profile_environment(
        &mut self,
        source_instance_id: &str,
        build_request: &BuildGkiRequest,
    ) -> Result<LocalBuildSourceInstance> {
        let source_instance = self.require_source_instance(source_instance_id)?;
        let env_file_path = self.source_env_file_path(&source_instance);
        if !env_file_path.is_file() {
            if self.inspect_source_materialized_env(&source_instance).is_none() {
                self.write_generated_source_environment(&source_instance, build_request)?;
            }
        }

        let updates = local_build_env_updates(build_request);
        rewrite_exported_env_file(&env_file_path, &updates)?;

        let materialized = self.inspect_source_materialized_state(&source_instance);
        let now = now_ms();
        {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.updated_at_ms = now;
            source_instance.last_error = None;
            if let Some(materialized) = materialized {
                source_instance.materialized = Some(materialized);
            }
        }
        self.persist()?;
        self.require_source_instance(source_instance_id)
    }

    pub fn finalize_profile_build(
        &mut self,
        profile_id: &str,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
    ) -> Result<LocalBuildProfile> {
        let source_instance_id = {
            let profile = self.require_profile_mut(profile_id)?;
            let now = now_ms();
            profile.updated_at_ms = now;
            profile.last_built_at_ms = Some(now);
            profile.last_task_id = Some(task_id.to_string());
            profile.last_error = None;
            profile.source_instance_id.clone()
        };
        let source_instance = self.require_source_instance(&source_instance_id)?;
        let materialized = self.inspect_source_materialized_state(&source_instance);
        if let Some(materialized) = materialized.clone() {
            let captured_source_id = {
                let source_instance = self.require_source_instance_mut(&source_instance_id)?;
                source_instance.updated_at_ms = now_ms();
                source_instance.active_backend_kind = Some(backend_kind);
                source_instance.materialized = Some(materialized.clone());
                source_instance.last_task_id = Some(task_id.to_string());
                source_instance.last_error = None;
                source_instance.id.clone()
            };
            self.store.settings.active_source_instance_id = Some(captured_source_id.clone());
            self.capture_artifacts_and_logs(
                &captured_source_id,
                Some(profile_id),
                task_id,
                backend_kind,
                &materialized,
            );
        }
        self.persist()?;
        self.require_profile(profile_id)
    }

    pub fn fail_profile_build(
        &mut self,
        profile_id: &str,
        task_id: &str,
        error: &str,
    ) -> Result<LocalBuildProfile> {
        {
            let profile = self.require_profile_mut(profile_id)?;
            profile.updated_at_ms = now_ms();
            profile.last_task_id = Some(task_id.to_string());
            profile.last_error = Some(error.trim().to_string());
        }
        self.persist()?;
        self.require_profile(profile_id)
    }

    pub fn list_artifacts(&self) -> LocalBuildArtifactsResponse {
        let mut artifacts = self.store.artifacts.clone();
        artifacts.sort_by(|left, right| right.created_at_ms.cmp(&left.created_at_ms));
        LocalBuildArtifactsResponse { artifacts }
    }

    pub fn list_logs(&self) -> LocalBuildLogsResponse {
        let mut logs = self.store.logs.clone();
        logs.sort_by(|left, right| right.created_at_ms.cmp(&left.created_at_ms));
        LocalBuildLogsResponse { logs }
    }

    pub fn ensure_legacy_source_instance(
        &mut self,
        android_version: &str,
        kernel_version: &str,
        branch_month: &str,
    ) -> Result<LocalBuildSourceInstance> {
        let kernel_line_id = format!(
            "{}/{}",
            android_version.trim().to_lowercase(),
            kernel_version.trim()
        );
        self.create_source_instance(CreateLocalBuildSourceInstanceRequest {
            kernel_line_id,
            branch_month: branch_month.to_string(),
        })
    }

    pub fn ensure_legacy_profile(
        &mut self,
        source_instance_id: &str,
        build: Option<BuildGkiRequest>,
    ) -> Result<LocalBuildProfile> {
        if let Some(existing) = self
            .store
            .profiles
            .iter()
            .find(|profile| profile.id == "legacy-script-profile")
            .cloned()
        {
            let request = SaveLocalBuildProfileRequest {
                id: Some(existing.id),
                name: Some("Legacy script profile".into()),
                source_instance_id: source_instance_id.to_string(),
                backend_kind: Some(LocalBuildBackendKind::Script),
                build: build.or(Some(existing.build)),
            };
            return self.save_profile(request);
        }
        self.save_profile(SaveLocalBuildProfileRequest {
            id: Some("legacy-script-profile".into()),
            name: Some("Legacy script profile".into()),
            source_instance_id: source_instance_id.to_string(),
            backend_kind: Some(LocalBuildBackendKind::Script),
            build,
        })
    }

    pub fn plan_backend_install(
        &mut self,
        kind: LocalBuildBackendKind,
        request: &InstallLocalBuildBackendRequest,
    ) -> Result<LocalBuildBackendInstallPlan> {
        let backend = self.backend_descriptor(kind);
        if !backend.install_supported {
            return Err(anyhow!("{} has no install action available", backend.label));
        }
        let action = match kind {
            LocalBuildBackendKind::Docker => LocalBuildBackendInstallAction::PullContainerImage {
                command: authorize_command_if_needed(
                    CommandSpec {
                        program: "docker".into(),
                        args: vec!["pull".into(), container_image_for(kind)],
                        cwd: repo_root(),
                        env: Vec::new(),
                        stdin: None,
                    },
                    &backend,
                    request.sudo_password.as_deref(),
                )?,
            },
            LocalBuildBackendKind::Podman => LocalBuildBackendInstallAction::PullContainerImage {
                command: authorize_command_if_needed(
                    CommandSpec {
                        program: "podman".into(),
                        args: vec!["pull".into(), container_image_for(kind)],
                        cwd: repo_root(),
                        env: Vec::new(),
                        stdin: None,
                    },
                    &backend,
                    request.sudo_password.as_deref(),
                )?,
            },
            LocalBuildBackendKind::Script => LocalBuildBackendInstallAction::RestoreScriptAssets,
            LocalBuildBackendKind::Wsl => LocalBuildBackendInstallAction::ImportWslRootfs {
                command: build_wsl_import_command(&self.data_root)?,
            },
        };
        Ok(LocalBuildBackendInstallPlan { backend, action })
    }

    pub fn restore_script_backend_assets(&mut self) -> Result<()> {
        let _ = ensure_local_build_root_materialized(&self.repo_root, &self.path_settings)?;
        Ok(())
    }

    fn collect_backend_descriptors(&self) -> Vec<LocalBuildBackendDescriptor> {
        let default_kind = self.store.settings.global_default_backend_kind;
        let backends = [
            backend_descriptor(
                LocalBuildBackendKind::Docker,
                inspect_container_backend(
                    "docker",
                    &container_image_for(LocalBuildBackendKind::Docker),
                ),
                true,
                true,
                vec![
                    "Host-owned source caches and artifacts.".into(),
                    format!(
                        "Container image: {}",
                        container_image_for(LocalBuildBackendKind::Docker)
                    ),
                    "Runs new_test scripts inside the container with host bind mounts.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Podman,
                inspect_container_backend(
                    "podman",
                    &container_image_for(LocalBuildBackendKind::Podman),
                ),
                true,
                true,
                vec![
                    "Docker-compatible container backend.".into(),
                    format!(
                        "Container image: {}",
                        container_image_for(LocalBuildBackendKind::Podman)
                    ),
                    "Runs new_test scripts inside the container with host bind mounts.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Wsl,
                inspect_wsl_backend(),
                true,
                false,
                vec![
                    "Windows-first backend for host-owned worktrees.".into(),
                    format!("Rootfs tar: {DEFAULT_WSL_ROOTFS_TAR_URL}"),
                    "Protocol reserved, execution not wired yet.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Script,
                if script_backend_available(&self.script_root()) {
                    BackendProbe {
                        available: true,
                        install_supported: false,
                        install_label: None,
                        install_detail: None,
                        detail: None,
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    }
                } else {
                    BackendProbe {
                        available: false,
                        install_supported: true,
                        install_label: Some("Restore local scripts".into()),
                        install_detail: Some(
                            "Re-materialize init.sh and rebuild.sh into the configured local build root."
                                .into(),
                        ),
                        detail: Some(
                            "init.sh or rebuild.sh is missing under the local build root.".into(),
                        ),
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    }
                },
                true,
                true,
                vec![
                    "Linux development adapter around new_test/init.sh and rebuild.sh.".into(),
                    "Each source instance materializes into its own isolated workspace.".into(),
                ],
            ),
        ];
        backends
            .into_iter()
            .map(|mut descriptor| {
                descriptor.is_global_default = descriptor.kind == default_kind;
                descriptor
            })
            .collect()
    }

    fn backend_descriptor(&self, kind: LocalBuildBackendKind) -> LocalBuildBackendDescriptor {
        self.collect_backend_descriptors()
            .into_iter()
            .find(|backend| backend.kind == kind)
            .unwrap_or_else(|| {
                backend_descriptor(
                    kind,
                    BackendProbe {
                        available: false,
                        install_supported: false,
                        install_label: None,
                        install_detail: None,
                        detail: None,
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    },
                    false,
                    false,
                    Vec::new(),
                )
            })
    }

    fn default_backend_kind(&self) -> LocalBuildBackendKind {
        self.collect_backend_descriptors()
            .into_iter()
            .find(|backend| backend.available && backend.capabilities.supports_build_execution)
            .map(|backend| backend.kind)
            .unwrap_or(LocalBuildBackendKind::Script)
    }

    fn persist(&self) -> Result<()> {
        let payload = serde_json::to_vec_pretty(&self.store)
            .context("failed to serialize local build store")?;
        fs::write(&self.store_path, payload)
            .with_context(|| format!("failed to write {}", self.store_path.display()))
    }

    fn build_container_init_command(
        &self,
        engine: &str,
        source_instance: &LocalBuildSourceInstance,
        force: bool,
        skip_deps: bool,
        build_request: Option<&BuildGkiRequest>,
    ) -> Result<CommandSpec> {
        let script_root = self.script_root();
        let script = script_root.join("init.sh");
        let mut script_args = vec![
            script.to_string_lossy().to_string(),
            "--android".into(),
            source_instance.android_version.clone(),
            "--kernel".into(),
            source_instance.kernel_version.clone(),
            "--branch-month".into(),
            source_instance.branch_month.clone(),
        ];
        if force {
            script_args.push("--force".into());
        }
        if skip_deps {
            script_args.push("--skip-deps".into());
        }
        self.build_container_command(
            engine,
            source_instance,
            script_root,
            script_args,
            build_request,
        )
    }

    fn build_container_rebuild_command(
        &self,
        engine: &str,
        source_instance: &LocalBuildSourceInstance,
        clean_out: bool,
        reseed: bool,
        no_package: bool,
        build_request: &BuildGkiRequest,
    ) -> Result<CommandSpec> {
        let script_root = self.script_root();
        let script = script_root.join("rebuild.sh");
        let mut script_args = vec![script.to_string_lossy().to_string()];
        if clean_out {
            script_args.push("--clean-out".into());
        }
        if reseed {
            script_args.push("--reseed".into());
        }
        if no_package {
            script_args.push("--no-package".into());
        }
        self.build_container_command(
            engine,
            source_instance,
            script_root,
            script_args,
            Some(build_request),
        )
    }

    fn build_container_command(
        &self,
        engine: &str,
        source_instance: &LocalBuildSourceInstance,
        script_root: PathBuf,
        script_args: Vec<String>,
        build_request: Option<&BuildGkiRequest>,
    ) -> Result<CommandSpec> {
        let image = container_image_for(match engine {
            "docker" => LocalBuildBackendKind::Docker,
            "podman" => LocalBuildBackendKind::Podman,
            _ => return Err(anyhow!("unsupported container engine {}", engine)),
        });
        let cache_root = self.source_cache_root(source_instance);
        let state_dir = self.source_state_dir(source_instance);
        let sources_dir = self.source_sources_dir(source_instance);
        let template_root = self.source_template_root(source_instance);
        let workspace_dir = self.source_workspace_dir(source_instance);
        let home_dir = self.source_container_home_dir(source_instance);
        fs::create_dir_all(&cache_root)
            .with_context(|| format!("failed to create {}", cache_root.display()))?;
        fs::create_dir_all(&state_dir)
            .with_context(|| format!("failed to create {}", state_dir.display()))?;
        fs::create_dir_all(&sources_dir)
            .with_context(|| format!("failed to create {}", sources_dir.display()))?;
        fs::create_dir_all(&template_root)
            .with_context(|| format!("failed to create {}", template_root.display()))?;
        fs::create_dir_all(&home_dir)
            .with_context(|| format!("failed to create {}", home_dir.display()))?;
        fs::create_dir_all(&workspace_dir)
            .with_context(|| format!("failed to create {}", workspace_dir.display()))?;

        let mut mounts = vec![
            repo_root(),
            self.script_root(),
            workspace_dir.clone(),
            cache_root,
        ];
        if let Some(build_request) = build_request {
            mounts.extend(extract_custom_module_paths(build_request));
        }
        let mounts = normalize_mounts(mounts);
        let (uid, gid) = current_uid_gid();

        let mut args = vec![
            "run".into(),
            "--rm".into(),
            "--pull".into(),
            "never".into(),
            "--user".into(),
            format!("{uid}:{gid}"),
            "-e".into(),
            format!("HOME={CONTAINER_HOME_MOUNT_TARGET}"),
            "-e".into(),
            format!(
                "ABK_LOCAL_BUILD_ABK_SOURCE_DIR={}",
                repo_root().to_string_lossy()
            ),
            "-e".into(),
            format!("ABK_LOCAL_BUILD_SOURCE_INSTANCE_ID={}", source_instance.id),
            "-e".into(),
            format!("ABK_LOCAL_BUILD_STATE_DIR={}", state_dir.to_string_lossy()),
            "-e".into(),
            format!(
                "ABK_LOCAL_BUILD_SOURCES_DIR={}",
                sources_dir.to_string_lossy()
            ),
            "-e".into(),
            format!(
                "ABK_LOCAL_BUILD_WORKSPACE_DIR={}",
                workspace_dir.to_string_lossy()
            ),
            "-e".into(),
            format!(
                "ABK_LOCAL_BUILD_TEMPLATE_ROOT={}",
                template_root.to_string_lossy()
            ),
            "-w".into(),
            script_root.to_string_lossy().to_string(),
        ];
        args.extend(container_runtime_network_args(
            engine,
            &ProxySettings::from_env(),
        ));
        for mount in mounts {
            let path = mount.to_string_lossy().to_string();
            args.push("-v".into());
            args.push(format!("{path}:{path}"));
        }
        args.push("-v".into());
        args.push(format!(
            "{}:{}",
            home_dir.to_string_lossy(),
            CONTAINER_HOME_MOUNT_TARGET
        ));
        args.push("--entrypoint".into());
        args.push("bash".into());
        args.push(image);
        args.extend(script_args);
        Ok(CommandSpec {
            program: engine.into(),
            args,
            cwd: repo_root(),
            env: Vec::new(),
            stdin: None,
        })
    }

    fn require_source_instance(
        &self,
        source_instance_id: &str,
    ) -> Result<LocalBuildSourceInstance> {
        self.store
            .source_instances
            .iter()
            .find(|source| source.id == source_instance_id)
            .cloned()
            .ok_or_else(|| anyhow!("unknown source instance {}", source_instance_id))
    }

    fn require_source_instance_mut(
        &mut self,
        source_instance_id: &str,
    ) -> Result<&mut LocalBuildSourceInstance> {
        self.store
            .source_instances
            .iter_mut()
            .find(|source| source.id == source_instance_id)
            .ok_or_else(|| anyhow!("unknown source instance {}", source_instance_id))
    }

    fn require_profile(&self, profile_id: &str) -> Result<LocalBuildProfile> {
        self.store
            .profiles
            .iter()
            .find(|profile| profile.id == profile_id)
            .cloned()
            .ok_or_else(|| anyhow!("unknown local build profile {}", profile_id))
    }

    fn require_profile_mut(&mut self, profile_id: &str) -> Result<&mut LocalBuildProfile> {
        self.store
            .profiles
            .iter_mut()
            .find(|profile| profile.id == profile_id)
            .ok_or_else(|| anyhow!("unknown local build profile {}", profile_id))
    }

    fn capture_artifacts_and_logs(
        &mut self,
        source_instance_id: &str,
        profile_id: Option<&str>,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
        materialized: &LocalBuildMaterializedState,
    ) {
        let created_at_ms = now_ms();
        if let Some(artifacts_dir) = materialized.artifacts_dir.as_deref() {
            for path in list_regular_files(Path::new(artifacts_dir)) {
                let file_name = path
                    .file_name()
                    .map(|item| item.to_string_lossy().to_string())
                    .unwrap_or_else(|| path.to_string_lossy().to_string());
                self.store.artifacts.push(LocalBuildArtifactEntry {
                    id: Uuid::new_v4().to_string(),
                    task_id: task_id.to_string(),
                    profile_id: profile_id.map(ToString::to_string),
                    source_instance_id: source_instance_id.to_string(),
                    backend_kind,
                    path: path.to_string_lossy().to_string(),
                    file_name,
                    exists: path.is_file(),
                    created_at_ms,
                });
            }
        }
        if let Some(log_path) = materialized.latest_log_path.as_deref() {
            let path = PathBuf::from(log_path);
            let file_name = path
                .file_name()
                .map(|item| item.to_string_lossy().to_string())
                .unwrap_or_else(|| path.to_string_lossy().to_string());
            self.store.logs.push(LocalBuildLogEntry {
                id: Uuid::new_v4().to_string(),
                task_id: task_id.to_string(),
                profile_id: profile_id.map(ToString::to_string),
                source_instance_id: source_instance_id.to_string(),
                backend_kind,
                path: path.to_string_lossy().to_string(),
                file_name,
                exists: path.is_file(),
                created_at_ms,
            });
        }
    }
}

fn load_store(path: &Path) -> Result<LocalBuildStore> {
    if !path.is_file() {
        return Ok(LocalBuildStore::default());
    }
    let content =
        fs::read_to_string(path).with_context(|| format!("failed to read {}", path.display()))?;
    let store: LocalBuildStore =
        serde_json::from_str(&content).context("failed to parse local build store")?;
    if store.schema_version != STORE_SCHEMA_VERSION {
        return Ok(LocalBuildStore::default());
    }
    Ok(store)
}

fn read_exported_env_file(path: &Path) -> Result<HashMap<String, String>> {
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

fn rewrite_exported_env_file(path: &Path, updates: &HashMap<String, String>) -> Result<()> {
    let content =
        fs::read_to_string(path).with_context(|| format!("failed to read {}", path.display()))?;
    let mut seen = HashSet::<String>::new();
    let mut output = Vec::<String>::new();
    for line in content.lines() {
        if let Some((prefix, key, _suffix)) = split_export_assignment_line(line) {
            if let Some(value) = updates.get(key) {
                output.push(format!("{prefix}{key}={}", shell_quote(value)));
                seen.insert(key.to_string());
                continue;
            }
            output.push(line.to_string());
            seen.insert(key.to_string());
        } else {
            output.push(line.to_string());
        }
    }
    for (key, value) in updates {
        if seen.contains(key) {
            continue;
        }
        output.push(format!("export {key}={}", shell_quote(value)));
    }
    fs::write(path, output.join("\n"))
        .with_context(|| format!("failed to write {}", path.display()))
}

fn split_export_assignment_line(line: &str) -> Option<(&str, &str, &str)> {
    let trimmed = line.trim_start();
    let prefix_len = line.len() - trimmed.len();
    let prefix = &line[..prefix_len];
    let after_export = trimmed.strip_prefix("export ")?;
    let equals_index = after_export.find('=')?;
    let key = after_export[..equals_index].trim();
    if key.is_empty() {
        return None;
    }
    let suffix = &after_export[equals_index + 1..];
    Some((prefix, key, suffix))
}

fn shell_quote(value: &str) -> String {
    let escaped = value.replace('\'', r"'\''");
    format!("'{escaped}'")
}

fn local_build_env_updates(request: &BuildGkiRequest) -> HashMap<String, String> {
    let custom_modules = request
        .custom_modules
        .clone()
        .unwrap_or_default()
        .trim()
        .to_string();
    let ksu_branch = request
        .ksu_branch
        .clone()
        .unwrap_or_else(|| "Stable".into());

    let mut updates = HashMap::<String, String>::new();
    updates.insert(
        "KSU_VARIANT".into(),
        request
            .ksu_variant
            .clone()
            .unwrap_or_else(|| "ReSukiSU".into())
            .trim()
            .to_string(),
    );
    updates.insert("KSU_TRACK".into(), local_ksu_track_label(&ksu_branch).into());
    updates.insert(
        "KSU_CUSTOM_REF".into(),
        request
            .custom_ref
            .clone()
            .unwrap_or_default()
            .trim()
            .to_string(),
    );
    updates.insert("ENABLE_SUSFS".into(), bool_env(request.susfs));
    updates.insert("USE_ZRAM".into(), bool_env(request.zram));
    updates.insert("ZRAM_FULL_ALGO".into(), bool_env(request.zram_full_algo));
    updates.insert(
        "ZRAM_EXTRA_ALGOS".into(),
        request
            .zram_extra_algos
            .clone()
            .unwrap_or_default()
            .trim()
            .to_string(),
    );
    updates.insert("USE_BBG".into(), bool_env(request.bbg));
    updates.insert("USE_DDK".into(), bool_env(request.ddk));
    updates.insert("USE_NTSYNC".into(), bool_env(request.ntsync));
    updates.insert("USE_NETWORKING".into(), bool_env(request.networking));
    updates.insert("USE_KPM".into(), bool_env(request.kpm));
    updates.insert(
        "KPM_PASSWORD".into(),
        request
            .kpm_password
            .clone()
            .unwrap_or_default()
            .trim()
            .to_string(),
    );
    updates.insert("USE_REKERNEL".into(), bool_env(request.rekernel));
    updates.insert("SUPP_OP".into(), "false".into());
    updates.insert(
        "USE_CUSTOM_EXTERNAL_MODULES".into(),
        bool_env(!custom_modules.is_empty()),
    );
    updates.insert("CUSTOM_EXTERNAL_MODULES".into(), custom_modules);
    updates.insert(
        "VIRTUALIZATION_SUPPORT".into(),
        request
            .virt
            .clone()
            .unwrap_or_else(|| "off".into())
            .trim()
            .to_string(),
    );
    updates.insert(
        "VERSION_INPUT".into(),
        request
            .version
            .clone()
            .unwrap_or_default()
            .trim()
            .to_string(),
    );
    updates.insert(
        "BUILD_TIME".into(),
        request
            .build_time
            .clone()
            .unwrap_or_default()
            .trim()
            .to_string(),
    );
    if let Some(revision) = request
        .revision
        .clone()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        updates.insert("REVISION".into(), revision);
    }
    updates
}

impl LocalBuildManager {
    fn write_generated_source_environment(
        &self,
        source: &LocalBuildSourceInstance,
        build_request: &BuildGkiRequest,
    ) -> Result<()> {
        let values = self.build_source_environment_values(source, build_request)?;
        let env_file_path = self.source_env_file_path(source);
        if let Some(parent) = env_file_path.parent() {
            fs::create_dir_all(parent)
                .with_context(|| format!("failed to create {}", parent.display()))?;
        }
        let mut lines = vec![
            "#!/usr/bin/env bash".to_string(),
            "# Generated by ABK desktop.".to_string(),
        ];
        let mut keys = values.keys().cloned().collect::<Vec<_>>();
        keys.sort();
        for key in keys {
            if let Some(value) = values.get(&key) {
                lines.push(format!("export {key}={}", shell_quote(value)));
            }
        }
        fs::write(&env_file_path, format!("{}\n", lines.join("\n")))
            .with_context(|| format!("failed to write {}", env_file_path.display()))?;
        Ok(())
    }

    fn build_source_environment_values(
        &self,
        source: &LocalBuildSourceInstance,
        build_request: &BuildGkiRequest,
    ) -> Result<HashMap<String, String>> {
        let state_dir = self.source_state_dir(source);
        let sources_dir = self.source_sources_dir(source);
        let workspace_dir = self.source_workspace_dir(source);
        let template_root = self.source_template_root(source);
        let kernel_root = workspace_dir.join("kernel");
        fs::create_dir_all(&state_dir)
            .with_context(|| format!("failed to create {}", state_dir.display()))?;
        fs::create_dir_all(&sources_dir)
            .with_context(|| format!("failed to create {}", sources_dir.display()))?;
        fs::create_dir_all(&template_root)
            .with_context(|| format!("failed to create {}", template_root.display()))?;
        fs::create_dir_all(&workspace_dir)
            .with_context(|| format!("failed to create {}", workspace_dir.display()))?;
        fs::create_dir_all(&kernel_root)
            .with_context(|| format!("failed to create {}", kernel_root.display()))?;

        let template_patch_level = build_request
            .os_patch_level
            .as_deref()
            .filter(|value| !value.trim().is_empty())
            .map(|value| value.trim().to_string())
            .or_else(|| {
                source
                    .materialized
                    .as_ref()
                    .and_then(|materialized| materialized.os_patch_level.clone())
            })
            .unwrap_or_else(|| source.branch_month.clone());
        let template_sublevel = build_request
            .sub_level
            .as_deref()
            .filter(|value| !value.trim().is_empty())
            .map(|value| value.trim().to_string())
            .or_else(|| {
                source
                    .materialized
                    .as_ref()
                    .and_then(|materialized| materialized.sub_level.clone())
            })
            .unwrap_or_default();
        let template_branch = format!(
            "common-{}-{}-{}",
            source.android_version, source.kernel_version, template_patch_level
        );
        let template_common_branch = source
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.template_common_branch.clone())
            .unwrap_or_else(|| template_branch.clone());

        let mut values = HashMap::<String, String>::new();
        values.insert("ROOT_DIR".into(), self.script_root().to_string_lossy().to_string());
        values.insert("SOURCE_INSTANCE_ID".into(), source.id.clone());
        values.insert("STATE_DIR".into(), state_dir.to_string_lossy().to_string());
        values.insert("SOURCES_DIR".into(), sources_dir.to_string_lossy().to_string());
        values.insert("WORKSPACE_DIR".into(), workspace_dir.to_string_lossy().to_string());
        values.insert(
            "ARTIFACTS_DIR".into(),
            workspace_dir.join("artifacts").to_string_lossy().to_string(),
        );
        values.insert(
            "LOGS_DIR".into(),
            workspace_dir.join("logs").to_string_lossy().to_string(),
        );
        values.insert(
            "CACHE_DIR".into(),
            workspace_dir.join("cache").to_string_lossy().to_string(),
        );
        values.insert(
            "KEYS_DIR".into(),
            workspace_dir.join("keys").to_string_lossy().to_string(),
        );
        values.insert(
            "STATE_DATA_DIR".into(),
            workspace_dir.join("state").to_string_lossy().to_string(),
        );
        values.insert(
            "TEMPLATE_ROOT".into(),
            template_root.to_string_lossy().to_string(),
        );
        values.insert(
            "TEMPLATE_NAME".into(),
            template_root
                .file_name()
                .map(|value| value.to_string_lossy().to_string())
                .unwrap_or_else(|| format!("AOSP_Kernel_{}_{}", source.android_version, source.kernel_version)),
        );
        values.insert("KERNEL_ROOT".into(), kernel_root.to_string_lossy().to_string());
        values.insert(
            "DEFCONFIG".into(),
            kernel_root
                .join("common/arch/arm64/configs/gki_defconfig")
                .to_string_lossy()
                .to_string(),
        );
        values.insert("ABK_SOURCE".into(), self.repo_root.to_string_lossy().to_string());
        values.insert(
            "ANYKERNEL3_SOURCE".into(),
            sources_dir.join("AnyKernel3").to_string_lossy().to_string(),
        );
        values.insert(
            "KERNEL_PATCHES_SOURCE".into(),
            sources_dir.join("kernel_patches").to_string_lossy().to_string(),
        );
        values.insert(
            "SUKISU_PATCHES_SOURCE".into(),
            sources_dir.join("SukiSU_patch").to_string_lossy().to_string(),
        );
        values.insert(
            "ACTION_BUILD_SOURCE".into(),
            sources_dir.join("Action-Build").to_string_lossy().to_string(),
        );
        values.insert(
            "SUSFS_SOURCE".into(),
            sources_dir.join("susfs4ksu").to_string_lossy().to_string(),
        );
        values.insert(
            "GCC_SOURCE".into(),
            sources_dir.join("gcc").to_string_lossy().to_string(),
        );
        values.insert(
            "VIRTUALIZATION_SOURCE".into(),
            sources_dir
                .join("Droidspaces-OSS")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "VIRTUALIZATION_SUPPORT_PATCHES".into(),
            sources_dir
                .join("Droidspaces-OSS/Documentation/resources/kernel-patches/GKI")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "AVBTOOL".into(),
            kernel_root
                .join("prebuilts/kernel-build-tools/linux-x86/bin/avbtool")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "MKBOOTIMG".into(),
            kernel_root
                .join("tools/mkbootimg/mkbootimg.py")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "UNPACK_BOOTIMG".into(),
            kernel_root
                .join("tools/mkbootimg/unpack_bootimg.py")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "BOOT_SIGN_KEY_PATH".into(),
            workspace_dir
                .join("keys/boot_sign_key.pem")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "CCACHE_DIR".into(),
            workspace_dir.join("cache/ccache").to_string_lossy().to_string(),
        );
        values.insert(
            "BAZEL_DISK_CACHE".into(),
            workspace_dir.join("cache/bazel-disk").to_string_lossy().to_string(),
        );
        values.insert(
            "CUSTOM_EXTERNAL_MODULES_MANIFEST".into(),
            workspace_dir
                .join("state/custom_external_modules.tsv")
                .to_string_lossy()
                .to_string(),
        );
        values.insert(
            "CUSTOM_EXTERNAL_MODULES_ROOT".into(),
            workspace_dir.join("custom_modules").to_string_lossy().to_string(),
        );
        values.insert(
            "TEMPLATE_ANDROID_VERSION".into(),
            source.android_version.clone(),
        );
        values.insert(
            "TEMPLATE_KERNEL_VERSION".into(),
            source.kernel_version.clone(),
        );
        values.insert("TEMPLATE_SUB_LEVEL".into(), template_sublevel.clone());
        values.insert("TEMPLATE_OS_PATCH_LEVEL".into(), template_patch_level.clone());
        values.insert("TEMPLATE_BRANCH".into(), template_branch.clone());
        values.insert("TEMPLATE_COMMON_BRANCH".into(), template_common_branch);
        values.insert("ANDROID_VERSION".into(), source.android_version.clone());
        values.insert("KERNEL_VERSION".into(), source.kernel_version.clone());
        values.insert("SUB_LEVEL".into(), template_sublevel);
        values.insert("OS_PATCH_LEVEL".into(), template_patch_level);
        values.insert(
            "REVISION".into(),
            build_request
                .revision
                .clone()
                .unwrap_or_else(|| String::from("r1")),
        );
        values.insert(
            "KSU_VARIANT".into(),
            build_request
                .ksu_variant
                .clone()
                .unwrap_or_else(|| String::from("ReSukiSU")),
        );
        values.insert(
            "KSU_TRACK".into(),
            local_ksu_track_label(build_request.ksu_branch.as_deref().unwrap_or("Stable"))
                .into(),
        );
        values.insert(
            "KSU_CUSTOM_REF".into(),
            build_request.custom_ref.clone().unwrap_or_default(),
        );
        values.insert("ENABLE_SUSFS".into(), bool_env(build_request.susfs));
        values.insert("USE_ZRAM".into(), bool_env(build_request.zram));
        values.insert(
            "ZRAM_FULL_ALGO".into(),
            bool_env(build_request.zram_full_algo),
        );
        values.insert(
            "ZRAM_EXTRA_ALGOS".into(),
            build_request.zram_extra_algos.clone().unwrap_or_default(),
        );
        values.insert("USE_BBG".into(), bool_env(build_request.bbg));
        values.insert("USE_DDK".into(), bool_env(build_request.ddk));
        values.insert("USE_NTSYNC".into(), bool_env(build_request.ntsync));
        values.insert("USE_NETWORKING".into(), bool_env(build_request.networking));
        values.insert("USE_KPM".into(), bool_env(build_request.kpm));
        values.insert(
            "KPM_PASSWORD".into(),
            build_request.kpm_password.clone().unwrap_or_default(),
        );
        values.insert("USE_REKERNEL".into(), bool_env(build_request.rekernel));
        values.insert("SUPP_OP".into(), "false".into());
        values.insert(
            "USE_CUSTOM_EXTERNAL_MODULES".into(),
            bool_env(!build_request
                .custom_modules
                .clone()
                .unwrap_or_default()
                .trim()
                .is_empty()),
        );
        values.insert(
            "CUSTOM_EXTERNAL_MODULES".into(),
            build_request.custom_modules.clone().unwrap_or_default(),
        );
        values.insert(
            "VIRTUALIZATION_SUPPORT".into(),
            build_request.virt.clone().unwrap_or_else(|| "off".into()),
        );
        values.insert(
            "VERSION_INPUT".into(),
            build_request.version.clone().unwrap_or_default(),
        );
        values.insert(
            "BUILD_TIME".into(),
            build_request.build_time.clone().unwrap_or_default(),
        );
        values.insert(
            "ABK_MANAGER_CERT_ENV_FILE".into(),
            self.repo_root
                .join("app/signing/abk-manager-cert.env")
                .to_string_lossy()
                .to_string(),
        );
        values.insert("ABK_MANAGER_PACKAGE".into(), String::new());
        values.insert("ABK_MANAGER_CERT_SIZE".into(), String::new());
        values.insert("ABK_MANAGER_CERT_SHA256".into(), String::new());
        Ok(values)
    }
}

fn bool_env(value: bool) -> String {
    if value { "true".into() } else { "false".into() }
}

fn local_ksu_track_label(value: &str) -> &'static str {
    match value.trim().to_ascii_lowercase().as_str() {
        "dev" => "Dev(开发)",
        "custom" => "Custom(自定义)",
        _ => "Stable(标准)",
    }
}

fn strip_shell_quotes(value: &str) -> &str {
    value
        .strip_prefix('"')
        .and_then(|inner| inner.strip_suffix('"'))
        .or_else(|| {
            value
                .strip_prefix('\'')
                .and_then(|inner| inner.strip_suffix('\''))
        })
        .unwrap_or(value)
}

fn env_path(env: &HashMap<String, String>, key: &str) -> Option<PathBuf> {
    env.get(key)
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
}

fn non_empty_string(value: String) -> Option<String> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed.to_string())
    }
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

fn extract_branch_month(value: &str) -> Option<String> {
    let trimmed = value.trim();
    let mut parts = trimmed.rsplitn(2, '-');
    let month = parts.next()?;
    let prefix = parts.next()?;
    let year = prefix.rsplit('-').next()?;
    if year.len() == 4
        && month.len() == 2
        && year.bytes().all(|byte| byte.is_ascii_digit())
        && month.bytes().all(|byte| byte.is_ascii_digit())
    {
        Some(format!("{year}-{month}"))
    } else {
        None
    }
}

fn backend_descriptor(
    kind: LocalBuildBackendKind,
    probe: BackendProbe,
    supports_source_sync: bool,
    supports_build_execution: bool,
    notes: Vec<String>,
) -> LocalBuildBackendDescriptor {
    LocalBuildBackendDescriptor {
        kind,
        label: kind.display_name().to_string(),
        available: probe.available,
        is_global_default: false,
        install_supported: probe.install_supported,
        install_label: probe.install_label,
        install_detail: probe.install_detail,
        authorization_required: probe.authorization_required,
        authorization_kind: probe.authorization_kind,
        authorization_message: probe.authorization_message,
        capabilities: LocalBuildBackendCapabilities {
            family: kind.family().to_string(),
            host_owned_paths: true,
            supports_source_sync,
            supports_build_execution,
            supports_profile_projection: kind == LocalBuildBackendKind::Script,
            notes,
        },
        detail: probe.detail,
    }
}

fn supported_kernel_lines(script_root: &Path) -> Vec<SupportedKernelLine> {
    [
        ("android12", "5.10"),
        ("android13", "5.15"),
        ("android14", "6.1"),
        ("android15", "6.6"),
        ("android16", "6.12"),
    ]
    .into_iter()
    .map(|(android_version, kernel_version)| {
        let id = format!("{android_version}/{kernel_version}");
        let script_template_path =
            script_template_path(&script_root, android_version, kernel_version);
        SupportedKernelLine {
            id,
            android_version: android_version.to_string(),
            kernel_version: kernel_version.to_string(),
            display_name: format!("{android_version} / {kernel_version}"),
            branch_month_format: "YYYY-MM".into(),
            script_template_available: Path::new(&script_template_path).is_dir(),
            script_template_path,
        }
    })
    .collect()
}

fn script_template_path(script_root: &Path, android_version: &str, kernel_version: &str) -> String {
    let android_suffix = android_version.trim_start_matches("android");
    script_root
        .join(format!(
            "AOSP_Kernel_A{}_{}",
            android_suffix, kernel_version
        ))
        .to_string_lossy()
        .to_string()
}

fn find_kernel_line(script_root: &Path, kernel_line_id: &str) -> Result<SupportedKernelLine> {
    supported_kernel_lines(script_root)
        .into_iter()
        .find(|line| line.id.eq_ignore_ascii_case(kernel_line_id.trim()))
        .ok_or_else(|| anyhow!("unsupported kernel line {}", kernel_line_id.trim()))
}

fn normalize_branch_month(raw: &str) -> Result<String> {
    let value = raw.trim();
    let bytes = value.as_bytes();
    let valid = bytes.len() == 7
        && bytes[0].is_ascii_digit()
        && bytes[1].is_ascii_digit()
        && bytes[2].is_ascii_digit()
        && bytes[3].is_ascii_digit()
        && bytes[4] == b'-'
        && bytes[5].is_ascii_digit()
        && bytes[6].is_ascii_digit();
    if !valid {
        return Err(anyhow!("branchMonth must use YYYY-MM"));
    }
    Ok(value.to_string())
}

fn source_instance_id(kernel_line_id: &str, branch_month: &str) -> String {
    format!(
        "{}@{}",
        kernel_line_id.replace('/', "-"),
        branch_month.trim()
    )
}

fn default_build_request_for_source(source_instance: &LocalBuildSourceInstance) -> BuildGkiRequest {
    BuildGkiRequest {
        target: "custom".into(),
        ksu_variant: Some("ReSukiSU".into()),
        ksu_branch: Some("Stable".into()),
        version: Some(String::new()),
        revision: Some(if source_instance.kernel_version == "5.10" {
            "r11".into()
        } else {
            String::new()
        }),
        custom_ref: Some(String::new()),
        build_time: Some(String::new()),
        custom_modules: Some(String::new()),
        kpm_password: Some(String::new()),
        virt: Some("off".into()),
        zram: false,
        bbg: false,
        ddk: false,
        kpm: false,
        susfs: true,
        rekernel: false,
        ntsync: false,
        networking: false,
        zram_full_algo: false,
        zram_extra_algos: Some(String::new()),
        android_version: Some(source_instance.android_version.clone()),
        kernel_version: Some(source_instance.kernel_version.clone()),
        sub_level: source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.sub_level.clone()),
        os_patch_level: source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.os_patch_level.clone())
            .or_else(|| Some(source_instance.branch_month.clone())),
        force: None,
    }
}

fn normalize_build_request(
    mut build_request: BuildGkiRequest,
    source_instance: &LocalBuildSourceInstance,
) -> BuildGkiRequest {
    build_request.target = "custom".into();
    build_request.android_version = Some(source_instance.android_version.clone());
    build_request.kernel_version = Some(source_instance.kernel_version.clone());
    if build_request
        .sub_level
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.sub_level = source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.sub_level.clone());
    }
    if build_request
        .os_patch_level
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.os_patch_level = source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.os_patch_level.clone())
            .or_else(|| Some(source_instance.branch_month.clone()));
    }
    if source_instance.kernel_version != "5.10" {
        build_request.revision = Some(String::new());
    } else if build_request
        .revision
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.revision = Some("r11".into());
    }
    build_request.force = None;
    build_request
}

fn script_backend_available(script_root: &Path) -> bool {
    script_root.join("init.sh").is_file() && script_root.join("rebuild.sh").is_file()
}

fn command_available(program: &str, args: &[&str]) -> bool {
    Command::new(program)
        .args(args)
        .current_dir(repo_root())
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false)
}

fn command_exists(program: &str, args: &[&str]) -> bool {
    Command::new(program)
        .args(args)
        .current_dir(repo_root())
        .output()
        .is_ok()
}

fn powershell_quote(value: &str) -> String {
    value.replace('\'', "''")
}

fn build_wsl_import_command(data_root: &Path) -> Result<CommandSpec> {
    if !cfg!(windows) {
        return Err(anyhow!("WSL backend installation is available on Windows only"));
    }
    let wsl_root = data_root.join("wsl");
    let tar_path = wsl_root.join("wsl-ubuntu-abk.tar");
    let distro_root = wsl_root.join("distro");
    let script = format!(
        "$ErrorActionPreference='Stop'; \
        $root='{root}'; \
        $tarPath='{tar_path}'; \
        $distroRoot='{distro_root}'; \
        $distro='{distro}'; \
        New-Item -ItemType Directory -Force -Path $root | Out-Null; \
        Invoke-WebRequest -Uri '{url}' -OutFile $tarPath; \
        $existing = wsl -l -q | ForEach-Object {{ $_.Trim() }}; \
        if ($existing -contains $distro) {{ Write-Host \"WSL distro already present: $distro\"; exit 0; }}; \
        New-Item -ItemType Directory -Force -Path $distroRoot | Out-Null; \
        wsl --import $distro $distroRoot $tarPath --version 2",
        root = powershell_quote(&wsl_root.to_string_lossy()),
        tar_path = powershell_quote(&tar_path.to_string_lossy()),
        distro_root = powershell_quote(&distro_root.to_string_lossy()),
        distro = powershell_quote(DEFAULT_WSL_DISTRO_NAME),
        url = powershell_quote(DEFAULT_WSL_ROOTFS_TAR_URL),
    );
    Ok(CommandSpec {
        program: "powershell.exe".into(),
        args: vec![
            "-NoProfile".into(),
            "-ExecutionPolicy".into(),
            "Bypass".into(),
            "-Command".into(),
            script,
        ],
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    })
}

fn container_image_for(kind: LocalBuildBackendKind) -> String {
    let env_name = match kind {
        LocalBuildBackendKind::Docker => "ABK_LOCAL_BUILD_DOCKER_IMAGE",
        LocalBuildBackendKind::Podman => "ABK_LOCAL_BUILD_PODMAN_IMAGE",
        _ => return DEFAULT_CONTAINER_IMAGE.into(),
    };
    env::var(env_name)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| DEFAULT_CONTAINER_IMAGE.into())
}

fn inspect_container_backend(program: &str, image: &str) -> BackendProbe {
    if !command_available(program, &["--version"]) {
        return BackendProbe {
            available: false,
            install_supported: false,
            install_label: None,
            install_detail: None,
            detail: Some(format!("{program} is not installed on this host.")),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        };
    }
    match Command::new(program)
        .args(["image", "inspect", image])
        .current_dir(repo_root())
        .output()
    {
        Ok(output) if output.status.success() => BackendProbe {
            available: true,
            install_supported: false,
            install_label: None,
            install_detail: None,
            detail: None,
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        },
        Ok(output) => {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            let text = format!("{}\n{}", stdout.trim(), stderr.trim())
                .trim()
                .to_string();
            let lower = text.to_ascii_lowercase();
            if lower.contains("permission denied") {
                return BackendProbe {
                    available: true,
                    install_supported: false,
                    install_label: None,
                    install_detail: None,
                    detail: Some(format!(
                        "{program} daemon is not accessible by the current user."
                    )),
                    authorization_required: true,
                    authorization_kind: Some("sudo".into()),
                    authorization_message: Some(format!(
                        "Authorize elevated access so ABK can run {program} through sudo."
                    )),
                };
            }
            let detail = if lower.contains("no such image")
                || lower.contains("not found")
                || lower.contains("image not known")
            {
                format!("Container image {image} is not present locally.")
            } else if text.is_empty() {
                format!("failed to inspect {program} image {image}.")
            } else {
                text
            };
            BackendProbe {
                available: false,
                install_supported: lower.contains("no such image")
                    || lower.contains("not found")
                    || lower.contains("image not known"),
                install_label: if lower.contains("no such image")
                    || lower.contains("not found")
                    || lower.contains("image not known")
                {
                    Some(format!("Pull {program} image"))
                } else {
                    None
                },
                install_detail: if lower.contains("no such image")
                    || lower.contains("not found")
                    || lower.contains("image not known")
                {
                    Some(format!("Pull {image} into the local {program} image store."))
                } else {
                    None
                },
                detail: Some(detail),
                authorization_required: false,
                authorization_kind: None,
                authorization_message: None,
            }
        }
        Err(error) => BackendProbe {
            available: false,
            install_supported: false,
            install_label: None,
            install_detail: None,
            detail: Some(format!("failed to execute {program}: {error}")),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        },
    }
}

fn inspect_wsl_backend() -> BackendProbe {
    if !cfg!(windows) {
        return BackendProbe {
            available: false,
            install_supported: false,
            install_label: None,
            install_detail: None,
            detail: Some(
                "wsl.exe is not available on this host. This backend is for Windows only.".into(),
            ),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        };
    }

    if !command_exists("wsl.exe", &["--help"]) {
        return BackendProbe {
            available: false,
            install_supported: false,
            install_label: None,
            install_detail: None,
            detail: Some("wsl.exe is not available on this host.".into()),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        };
    }

    let distro_list = Command::new("wsl.exe")
        .args(["-l", "-q"])
        .current_dir(repo_root())
        .output()
        .ok()
        .map(|output| String::from_utf8_lossy(&output.stdout).to_string())
        .unwrap_or_default();
    let distro_ready = distro_list
        .lines()
        .map(str::trim)
        .any(|line| line.eq_ignore_ascii_case(DEFAULT_WSL_DISTRO_NAME));

    BackendProbe {
        available: distro_ready,
        install_supported: cfg!(windows),
        install_label: if cfg!(windows) {
            Some("Import ABK WSL rootfs".into())
        } else {
            None
        },
        install_detail: if cfg!(windows) {
            Some(format!(
                "Download and import the ABK WSL rootfs from {DEFAULT_WSL_ROOTFS_TAR_URL}."
            ))
        } else {
            None
        },
        detail: if distro_ready {
            Some("ABK WSL distro is already imported. Execution wiring is still pending.".into())
        } else {
            Some("wsl.exe is available, but the ABK WSL distro has not been imported yet.".into())
        },
        authorization_required: false,
        authorization_kind: None,
        authorization_message: None,
    }
}

fn current_uid_gid() -> (u32, u32) {
    #[cfg(unix)]
    // SAFETY: getuid/getgid are pure libc calls without preconditions.
    unsafe {
        return (getuid(), getgid());
    }

    #[cfg(not(unix))]
    {
        (0, 0)
    }
}

fn extract_custom_module_paths(build_request: &BuildGkiRequest) -> Vec<PathBuf> {
    build_request
        .custom_modules
        .as_deref()
        .unwrap_or_default()
        .split('|')
        .filter_map(parse_custom_module_path)
        .collect()
}

fn parse_custom_module_path(entry: &str) -> Option<PathBuf> {
    let head = entry.trim().split(';').next()?.trim();
    if head.is_empty() {
        return None;
    }
    let path = head
        .strip_prefix("module:")
        .or_else(|| head.strip_prefix("set:"))
        .unwrap_or(head)
        .split('#')
        .next()
        .unwrap_or_default()
        .trim();
    if path.starts_with('/') {
        Some(PathBuf::from(path))
    } else {
        None
    }
}

fn normalize_mounts(paths: Vec<PathBuf>) -> Vec<PathBuf> {
    let mut unique = paths
        .into_iter()
        .filter_map(|path| fs::canonicalize(path).ok())
        .collect::<Vec<_>>();
    unique.sort();
    unique.dedup();
    let mut filtered = Vec::new();
    for candidate in &unique {
        if !unique_path_has_parent_in_list(candidate, &unique) {
            filtered.push(candidate.clone());
        }
    }
    filtered
}

fn unique_path_has_parent_in_list(candidate: &Path, all: &[PathBuf]) -> bool {
    all.iter()
        .any(|other| other != candidate && candidate.starts_with(other))
}

fn container_runtime_network_args(engine: &str, proxy_settings: &ProxySettings) -> Vec<String> {
    if proxy_settings.requires_host_network_for_container() {
        let mut args = vec!["--network".into(), "host".into()];
        args.extend(proxy_settings.container_env_args());
        return args;
    }
    let mut args = Vec::new();
    if let Some(host_map) = container_host_map_arg(engine) {
        args.push("--add-host".into());
        args.push(host_map.into());
    }
    args.extend(proxy_settings.container_env_args_for_host(container_proxy_host_alias(engine)));
    args
}

fn container_proxy_host_alias(engine: &str) -> Option<&'static str> {
    match engine {
        "docker" => Some(DOCKER_CONTAINER_HOST_ALIAS),
        "podman" => Some(PODMAN_CONTAINER_HOST_ALIAS),
        _ => None,
    }
}

fn container_host_map_arg(engine: &str) -> Option<&'static str> {
    match engine {
        "docker" => Some(DOCKER_CONTAINER_HOST_MAP),
        _ => None,
    }
}

fn authorize_command_if_needed(
    command: CommandSpec,
    backend: &LocalBuildBackendDescriptor,
    sudo_password: Option<&str>,
) -> Result<CommandSpec> {
    if !backend.authorization_required {
        return Ok(command);
    }
    let password = sudo_password
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| anyhow!("{} requires elevated authorization", backend.label))?;
    match backend.authorization_kind.as_deref() {
        Some("sudo") | None => Ok(wrap_command_with_sudo(command, password)),
        Some(other) => Err(anyhow!("unsupported authorization kind {}", other)),
    }
}

fn list_regular_files(dir: &Path) -> Vec<PathBuf> {
    let mut files = fs::read_dir(dir)
        .ok()
        .into_iter()
        .flatten()
        .filter_map(|entry| {
            let entry = entry.ok()?;
            if !entry.file_type().ok()?.is_file() {
                return None;
            }
            Some(entry.path())
        })
        .collect::<Vec<_>>();
    files.sort();
    files
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn docker_network_args_use_host_network_for_loopback_proxy() {
        let proxy_settings = ProxySettings {
            http_proxy: Some("http://127.0.0.1:7890".into()),
            https_proxy: Some("http://localhost:7890".into()),
            all_proxy: Some("socks5://127.0.0.1:7891".into()),
            no_proxy: Some("127.0.0.1,localhost".into()),
        };

        assert_eq!(
            container_runtime_network_args("docker", &proxy_settings),
            vec![
                "--network",
                "host",
                "-e",
                "http_proxy=http://127.0.0.1:7890",
                "-e",
                "HTTP_PROXY=http://127.0.0.1:7890",
                "-e",
                "https_proxy=http://localhost:7890",
                "-e",
                "HTTPS_PROXY=http://localhost:7890",
                "-e",
                "all_proxy=socks5://127.0.0.1:7891",
                "-e",
                "ALL_PROXY=socks5://127.0.0.1:7891",
                "-e",
                "no_proxy=127.0.0.1,localhost",
                "-e",
                "NO_PROXY=127.0.0.1,localhost",
            ]
        );
    }

    #[test]
    fn docker_network_args_rewrite_non_loopback_proxy_to_host_alias() {
        let proxy_settings = ProxySettings {
            http_proxy: Some("http://proxy.example:7890".into()),
            https_proxy: None,
            all_proxy: None,
            no_proxy: Some("127.0.0.1,localhost".into()),
        };

        assert_eq!(
            container_runtime_network_args("docker", &proxy_settings),
            vec![
                "--add-host",
                "host.docker.internal:host-gateway",
                "-e",
                "http_proxy=http://proxy.example:7890",
                "-e",
                "HTTP_PROXY=http://proxy.example:7890",
                "-e",
                "no_proxy=127.0.0.1,localhost",
                "-e",
                "NO_PROXY=127.0.0.1,localhost",
            ]
        );
    }

    #[test]
    fn container_home_uses_short_mount_target_inside_container() {
        let repo_root = repo_root();
        let manager = LocalBuildManager::new(repo_root.clone()).expect("manager");
        let source = LocalBuildSourceInstance {
            id: "android14-6.1@2025-01".into(),
            display_name: "android14/6.1 2025-01".into(),
            kernel_line_id: "android14/6.1".into(),
            android_version: "android14".into(),
            kernel_version: "6.1".into(),
            branch_month: "2025-01".into(),
            cache_root: String::new(),
            working_tree_root: String::new(),
            state: "ready".into(),
            created_at_ms: 0,
            updated_at_ms: 0,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };

        let command = manager
            .build_container_init_command("docker", &source, false, true, None)
            .expect("command");

        assert!(command
            .args
            .contains(&format!("HOME={CONTAINER_HOME_MOUNT_TARGET}")));
        let expected_home_mount = format!(
            "{}:{CONTAINER_HOME_MOUNT_TARGET}",
            manager.source_container_home_dir(&source).to_string_lossy()
        );
        assert!(command
            .args
            .iter()
            .any(|arg| { arg == &expected_home_mount }));
    }

    #[test]
    fn container_init_command_isolates_source_instance_paths() {
        let repo_root = repo_root();
        let manager = LocalBuildManager::new(repo_root.clone()).expect("manager");
        let source_a = LocalBuildSourceInstance {
            id: "android14-6.1@2025-01".into(),
            display_name: "android14/6.1 2025-01".into(),
            kernel_line_id: "android14/6.1".into(),
            android_version: "android14".into(),
            kernel_version: "6.1".into(),
            branch_month: "2025-01".into(),
            cache_root: String::new(),
            working_tree_root: String::new(),
            state: "ready".into(),
            created_at_ms: 0,
            updated_at_ms: 0,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };
        let source_b = LocalBuildSourceInstance {
            id: "android15-6.6@2026-07".into(),
            display_name: "android15/6.6 2026-07".into(),
            kernel_line_id: "android15/6.6".into(),
            android_version: "android15".into(),
            kernel_version: "6.6".into(),
            branch_month: "2026-07".into(),
            cache_root: String::new(),
            working_tree_root: String::new(),
            state: "ready".into(),
            created_at_ms: 0,
            updated_at_ms: 0,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };

        let command_a = manager
            .build_container_init_command("docker", &source_a, false, true, None)
            .expect("command a");
        let command_b = manager
            .build_container_init_command("docker", &source_b, false, true, None)
            .expect("command b");

        let workspace_a = manager
            .source_workspace_dir(&source_a)
            .to_string_lossy()
            .to_string();
        let workspace_b = manager
            .source_workspace_dir(&source_b)
            .to_string_lossy()
            .to_string();
        let state_a = manager
            .source_state_dir(&source_a)
            .to_string_lossy()
            .to_string();
        let state_b = manager
            .source_state_dir(&source_b)
            .to_string_lossy()
            .to_string();
        let template_a = manager
            .source_template_root(&source_a)
            .to_string_lossy()
            .to_string();
        let template_b = manager
            .source_template_root(&source_b)
            .to_string_lossy()
            .to_string();

        assert_ne!(workspace_a, workspace_b);
        assert_ne!(state_a, state_b);
        assert_ne!(template_a, template_b);

        assert!(command_a
            .args
            .iter()
            .any(|arg| arg == &format!("ABK_LOCAL_BUILD_WORKSPACE_DIR={workspace_a}")));
        assert!(command_b
            .args
            .iter()
            .any(|arg| arg == &format!("ABK_LOCAL_BUILD_WORKSPACE_DIR={workspace_b}")));
        assert!(command_a
            .args
            .iter()
            .any(|arg| arg == &format!("ABK_LOCAL_BUILD_STATE_DIR={state_a}")));
        assert!(command_b
            .args
            .iter()
            .any(|arg| arg == &format!("ABK_LOCAL_BUILD_STATE_DIR={state_b}")));
    }

    #[test]
    fn profile_build_plan_uses_rebuild_only_for_synced_source_instance() {
        let repo_root = repo_root();
        let mut manager = LocalBuildManager::new(repo_root.clone()).expect("manager");

        let source = manager
            .create_source_instance(CreateLocalBuildSourceInstanceRequest {
                kernel_line_id: "android14/6.1".into(),
                branch_month: "2025-01".into(),
            })
            .expect("source");
        let synced_source = {
            let source_mut = manager
                .require_source_instance_mut(&source.id)
                .expect("source mut");
            source_mut.state = "ready".into();
            source_mut.last_synced_at_ms = Some(1);
            source_mut.clone()
        };
        let profile = manager
            .save_profile(SaveLocalBuildProfileRequest {
                id: None,
                name: Some("profile".into()),
                source_instance_id: synced_source.id.clone(),
                backend_kind: Some(LocalBuildBackendKind::Script),
                build: Some(default_build_request_for_source(&synced_source)),
            })
            .expect("profile");

        let plan = manager
            .plan_profile_build(
                &profile.id,
                &BuildLocalBuildProfileRequest {
                    clean_out: Some(true),
                    reseed: Some(true),
                    no_package: Some(false),
                    sudo_password: None,
                },
            )
            .expect("plan");

        assert!(plan.activation_command.is_none());
        assert_eq!(plan.build_command.program, "bash");
        assert!(plan
            .build_command
            .args
            .first()
            .is_some_and(|arg| arg.ends_with("rebuild.sh")));
    }
}
