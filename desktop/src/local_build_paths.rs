use anyhow::{anyhow, Context, Result};
use serde::{Deserialize, Serialize};
use std::env;
use std::fs;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

const LOCAL_BUILD_INIT_SCRIPT: &str = include_str!("../assets/local_build/init.sh");
const LOCAL_BUILD_REBUILD_SCRIPT: &str = include_str!("../assets/local_build/rebuild.sh");

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildPathSettings {
    pub script_root_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub profile_store_dir: Option<String>,
}

pub fn local_build_config_root(repo_root: &Path) -> PathBuf {
    if let Some(path) = env::var("ABK_DESKTOP_STATE_ROOT")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path);
    }
    if let Some(path) = env::var("XDG_CONFIG_HOME")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path).join("abk-desktop");
    }
    if let Some(path) = env::var("APPDATA")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path).join("ABK Desktop");
    }
    if let Some(path) = env::var("HOME")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path).join(".config").join("abk-desktop");
    }
    repo_root.join(".abk-desktop")
}

pub fn local_build_config_path(repo_root: &Path) -> PathBuf {
    local_build_config_root(repo_root).join("local-build-config.json")
}

pub fn load_local_build_path_settings(repo_root: &Path) -> Result<LocalBuildPathSettings> {
    let path = local_build_config_path(repo_root);
    if !path.is_file() {
        return Ok(LocalBuildPathSettings::default());
    }
    let content =
        fs::read_to_string(&path).with_context(|| format!("failed to read {}", path.display()))?;
    serde_json::from_str(&content).with_context(|| format!("failed to parse {}", path.display()))
}

pub fn persist_local_build_path_settings(
    repo_root: &Path,
    settings: &LocalBuildPathSettings,
) -> Result<()> {
    let path = local_build_config_path(repo_root);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let payload = serde_json::to_vec_pretty(settings)
        .context("failed to serialize local build path settings")?;
    fs::write(&path, payload).with_context(|| format!("failed to write {}", path.display()))
}

pub fn resolve_local_build_root(repo_root: &Path, settings: &LocalBuildPathSettings) -> PathBuf {
    if let Some(path) = settings
        .script_root_dir
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path);
    }
    if let Some(raw) = env::var("ABK_LOCAL_BUILD_ROOT")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(raw);
    }
    if looks_like_dev_checkout(repo_root) {
        return repo_root
            .parent()
            .expect("repo root lives under kernelexp")
            .join("new_test");
    }
    local_build_config_root(repo_root).join("local-build-root")
}

fn looks_like_dev_checkout(repo_root: &Path) -> bool {
    repo_root.join("desktop").is_dir() && repo_root.join("cli").join("abk.py").is_file()
}

pub fn resolve_local_build_workspace_dir(
    script_root: &Path,
    settings: &LocalBuildPathSettings,
) -> PathBuf {
    settings
        .workspace_dir
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| script_root.join(".local-build").join("workspace"))
}

pub fn resolve_local_build_profile_store_dir(
    repo_root: &Path,
    settings: &LocalBuildPathSettings,
) -> PathBuf {
    settings
        .profile_store_dir
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| local_build_config_root(repo_root).join("local-build"))
}

pub fn normalize_optional_dir_setting(value: Option<String>) -> Result<Option<String>> {
    let Some(value) = value.map(|value| value.trim().to_string()) else {
        return Ok(None);
    };
    if value.is_empty() {
        return Ok(None);
    }
    let path = PathBuf::from(&value);
    if !path.is_absolute() {
        return Err(anyhow!("directory settings must use absolute paths"));
    }
    Ok(Some(path.to_string_lossy().to_string()))
}

pub fn ensure_local_build_root_materialized(
    repo_root: &Path,
    settings: &LocalBuildPathSettings,
) -> Result<PathBuf> {
    let target_root = resolve_local_build_root(repo_root, settings);
    if local_build_root_ready(&target_root) {
        return Ok(target_root);
    }
    materialize_local_build_assets(&target_root)?;
    Ok(target_root)
}

fn local_build_root_ready(root: &Path) -> bool {
    root.join("init.sh").is_file() && root.join("rebuild.sh").is_file()
}

fn materialize_local_build_assets(target_root: &Path) -> Result<()> {
    fs::create_dir_all(target_root)
        .with_context(|| format!("failed to create {}", target_root.display()))?;
    write_local_build_asset(&target_root.join("init.sh"), LOCAL_BUILD_INIT_SCRIPT)?;
    write_local_build_asset(&target_root.join("rebuild.sh"), LOCAL_BUILD_REBUILD_SCRIPT)?;
    Ok(())
}

fn write_local_build_asset(target_path: &Path, content: &str) -> Result<()> {
    if let Some(parent) = target_path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let needs_write = match fs::read_to_string(target_path) {
        Ok(existing) => existing != content,
        Err(_) => true,
    };
    if needs_write {
        fs::write(target_path, content)
            .with_context(|| format!("failed to write {}", target_path.display()))?;
    }
    #[cfg(unix)]
    fs::set_permissions(target_path, fs::Permissions::from_mode(0o755))
        .with_context(|| format!("failed to chmod {}", target_path.display()))?;
    Ok(())
}
