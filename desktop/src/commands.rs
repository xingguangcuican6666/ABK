use crate::local_build_paths::{
    load_local_build_path_settings, resolve_local_build_root, resolve_local_build_workspace_dir,
};
use anyhow::{anyhow, Context, Result};
use std::env;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandSpec {
    pub program: String,
    pub args: Vec<String>,
    pub cwd: PathBuf,
    pub env: Vec<(String, String)>,
    pub stdin: Option<String>,
}

impl CommandSpec {
    pub fn display(&self) -> String {
        self.env
            .iter()
            .map(|(key, value)| format!("{key}={value}"))
            .chain(std::iter::once(self.program.clone()))
            .chain(self.args.iter().cloned())
            .collect::<Vec<_>>()
            .join(" ")
    }
}

fn local_build_command_envs() -> Vec<(String, String)> {
    let repo_root = repo_root();
    let settings = load_local_build_path_settings(&repo_root).unwrap_or_default();
    let script_root = resolve_local_build_root(&repo_root, &settings);
    let workspace_dir = resolve_local_build_workspace_dir(&script_root, &settings);
    let default_workspace_dir = script_root.join(".local-build").join("workspace");
    let mut envs = vec![(
        "ABK_LOCAL_BUILD_ABK_SOURCE_DIR".into(),
        repo_root.to_string_lossy().to_string(),
    )];
    if workspace_dir != default_workspace_dir {
        envs.push((
            "ABK_LOCAL_BUILD_WORKSPACE_DIR".into(),
            workspace_dir.to_string_lossy().to_string(),
        ));
    }
    envs
}

pub fn repo_root() -> PathBuf {
    if let Some(path) = env::var("ABK_DESKTOP_APP_ROOT")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return PathBuf::from(path);
    }
    if let Some(path) = resolve_bundled_app_root_from_current_exe() {
        return path;
    }
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("desktop lives under repo root")
        .to_path_buf()
}

fn resolve_bundled_app_root_from_current_exe() -> Option<PathBuf> {
    let exe_path = env::current_exe().ok()?;
    let exe_dir = exe_path.parent()?;
    resolve_bundled_app_root_from_base(exe_dir)
}

fn resolve_bundled_app_root_from_base(base_dir: &Path) -> Option<PathBuf> {
    for relative in [
        PathBuf::from("resources").join("abk"),
        PathBuf::from("..").join("resources").join("abk"),
        PathBuf::from("..").join("share").join("abk"),
    ] {
        let candidate = base_dir.join(relative);
        if looks_like_app_root(&candidate) {
            return Some(candidate);
        }
    }
    None
}

fn looks_like_app_root(path: &Path) -> bool {
    path.is_dir()
        && path.join("cli").join("abk.py").is_file()
        && path.join("hmbird_patch.c").is_file()
}

pub fn local_build_root() -> PathBuf {
    let repo_root = repo_root();
    let settings = load_local_build_path_settings(&repo_root).unwrap_or_default();
    resolve_local_build_root(&repo_root, &settings)
}

pub fn build_cli_command(raw_args: &str) -> Result<CommandSpec> {
    let parsed = shell_words::split(raw_args).context("failed to parse CLI args")?;
    if parsed.is_empty() {
        return Err(anyhow!("CLI args are empty"));
    }
    build_cli_command_parts(&parsed)
}

pub fn build_cli_command_parts(parts: &[String]) -> Result<CommandSpec> {
    if parts.is_empty() {
        return Err(anyhow!("CLI args are empty"));
    }
    let script = repo_root().join("cli").join("abk.py");
    let (program, mut args) = cli_python_invocation();
    args.push(script.to_string_lossy().to_string());
    args.extend(parts.iter().cloned());
    Ok(CommandSpec {
        program,
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    })
}

fn cli_python_invocation() -> (String, Vec<String>) {
    if let Some(path) = env::var("ABK_DESKTOP_PYTHON")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
    {
        return (path, Vec::new());
    }
    if cfg!(windows) {
        return ("python".into(), Vec::new());
    }
    ("python3".into(), Vec::new())
}

pub fn build_adb_detect_command() -> CommandSpec {
    CommandSpec {
        program: "adb".into(),
        args: vec!["devices".into(), "-l".into()],
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_forward_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "forward".into(),
        format!("tcp:{port}"),
        format!("tcp:{port}"),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_remove_forward_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend(["forward".into(), "--remove".into(), format!("tcp:{port}")]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_start_agent_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "am".into(),
        "start-foreground-service".into(),
        "-a".into(),
        "com.abk.kernel.agent.START".into(),
        "-n".into(),
        "com.abk.kernel/.agent.AbkAgentService".into(),
        "--es".into(),
        "host".into(),
        "127.0.0.1".into(),
        "--ei".into(),
        "port".into(),
        port.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_stop_agent_command(serial: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "am".into(),
        "startservice".into(),
        "-a".into(),
        "com.abk.kernel.agent.STOP".into(),
        "-n".into(),
        "com.abk.kernel/.agent.AbkAgentService".into(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_push_command(serial: &str, local_path: &str, remote_path: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "push".into(),
        local_path.to_string(),
        remote_path.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_adb_shell_command(serial: &str, script: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "sh".into(),
        "-lc".into(),
        script.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
        env: Vec::new(),
        stdin: None,
    }
}

pub fn build_local_init_command(
    android_version: &str,
    kernel_version: &str,
    branch_month: &str,
    force: bool,
    skip_deps: bool,
) -> Result<CommandSpec> {
    let script_root = local_build_root();
    let script = script_root.join("init.sh");
    let mut args = vec![
        script.to_string_lossy().to_string(),
        "--android".into(),
        android_version.trim().to_string(),
        "--kernel".into(),
        kernel_version.trim().to_string(),
        "--branch-month".into(),
        branch_month.trim().to_string(),
    ];
    if force {
        args.push("--force".into());
    }
    if skip_deps {
        args.push("--skip-deps".into());
    }
    Ok(CommandSpec {
        program: "bash".into(),
        args,
        cwd: script_root,
        env: local_build_command_envs(),
        stdin: None,
    })
}

pub fn build_local_rebuild_command(clean_out: bool, reseed: bool, no_package: bool) -> CommandSpec {
    let script_root = local_build_root();
    let script = script_root.join("rebuild.sh");
    let mut args = vec![script.to_string_lossy().to_string()];
    if clean_out {
        args.push("--clean-out".into());
    }
    if reseed {
        args.push("--reseed".into());
    }
    if no_package {
        args.push("--no-package".into());
    }
    CommandSpec {
        program: "bash".into(),
        args,
        cwd: script_root,
        env: local_build_command_envs(),
        stdin: None,
    }
}

pub fn run_command(spec: &CommandSpec) -> Result<String> {
    let mut child = Command::new(&spec.program)
        .args(&spec.args)
        .current_dir(&spec.cwd)
        .envs(spec.env.iter().map(|(key, value)| (key, value)))
        .stdin(if spec.stdin.is_some() {
            Stdio::piped()
        } else {
            Stdio::null()
        })
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .with_context(|| format!("failed to execute {}", spec.display()))?;
    if let Some(input) = spec.stdin.as_deref() {
        let stdin = child
            .stdin
            .as_mut()
            .ok_or_else(|| anyhow!("failed to open stdin for {}", spec.display()))?;
        stdin
            .write_all(input.as_bytes())
            .with_context(|| format!("failed to write stdin for {}", spec.display()))?;
    }
    let output = child
        .wait_with_output()
        .with_context(|| format!("failed to wait for {}", spec.display()))?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let combined = match (stdout.trim(), stderr.trim()) {
        ("", "") => String::new(),
        ("", err) => err.to_string(),
        (out, "") => out.to_string(),
        (out, err) => format!("{out}\n{err}"),
    };
    if output.status.success() {
        Ok(combined)
    } else {
        Err(anyhow!(
            "command failed ({})\n{}",
            output.status,
            combined.trim()
        ))
    }
}

pub fn wrap_command_with_sudo(spec: CommandSpec, password: &str) -> CommandSpec {
    let mut args = vec!["-S".into(), "--".into(), spec.program];
    args.extend(spec.args);
    CommandSpec {
        program: "sudo".into(),
        args,
        cwd: spec.cwd,
        env: spec.env,
        stdin: Some(format!("{}\n", password.trim_end())),
    }
}

fn adb_prefix(serial: &str) -> Vec<String> {
    let serial = serial.trim();
    if serial.is_empty() {
        Vec::new()
    } else {
        vec!["-s".into(), serial.into()]
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn builds_cli_command() {
        let spec = build_cli_command("status --run-id 42").unwrap();
        assert_eq!(
            spec.program,
            if cfg!(windows) { "python" } else { "python3" }
        );
        assert!(spec.args[0].ends_with("cli/abk.py"));
        assert_eq!(&spec.args[1..], ["status", "--run-id", "42"]);
    }

    #[test]
    fn builds_start_agent_command_with_serial() {
        let spec = build_adb_start_agent_command("ABC123", 48765);
        assert_eq!(spec.program, "adb");
        assert_eq!(spec.args[0], "-s");
        assert!(spec
            .args
            .contains(&"com.abk.kernel/.agent.AbkAgentService".into()));
        assert!(spec.args.contains(&"48765".into()));
    }

    #[test]
    fn builds_push_command() {
        let spec =
            build_adb_push_command("ABC123", "/tmp/module.zip", "/data/local/tmp/module.zip");
        assert_eq!(spec.args[0], "-s");
        assert_eq!(spec.args[2], "push");
        assert_eq!(spec.args[3], "/tmp/module.zip");
        assert_eq!(spec.args[4], "/data/local/tmp/module.zip");
    }

    #[test]
    fn resolves_bundled_app_root_from_executable_directory() {
        let temp_root = std::env::temp_dir().join(format!("abk-test-{}", Uuid::new_v4()));
        let bundle_root = temp_root.join("ABK-windows-x64");
        let app_root = bundle_root.join("resources").join("abk");
        std::fs::create_dir_all(app_root.join("cli")).unwrap();
        std::fs::write(app_root.join("cli").join("abk.py"), "print('ok')\n").unwrap();
        std::fs::write(app_root.join("hmbird_patch.c"), "/* ok */\n").unwrap();

        let resolved = resolve_bundled_app_root_from_base(&bundle_root).unwrap();
        assert_eq!(resolved, app_root);

        std::fs::remove_dir_all(temp_root).ok();
    }
}
