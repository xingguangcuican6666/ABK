#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

use anyhow::{anyhow, Context, Result};
use std::env;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

const SIDECAR_HOST: &str = "127.0.0.1";
const SIDECAR_START_TIMEOUT: Duration = Duration::from_secs(15);

pub fn run() -> Result<()> {
    let launcher_path = env::current_exe().context("failed to resolve launcher path")?;
    let launcher_dir = launcher_path
        .parent()
        .ok_or_else(|| anyhow!("launcher path has no parent"))?;

    let sidecar_path = resolve_sidecar_path(launcher_dir)?;
    let frontend_path = resolve_frontend_path(launcher_dir)?;
    let app_root = resolve_app_root(launcher_dir)?;
    let port = reserve_local_port()?;

    let mut sidecar = spawn_sidecar(&sidecar_path, &app_root, port)?;
    wait_for_sidecar(port).with_context(|| {
        format!("sidecar did not start listening on http://{SIDECAR_HOST}:{port}")
    })?;

    let status = spawn_frontend(&frontend_path, port)?
        .wait()
        .context("failed while waiting for frontend process")?;

    terminate_child(&mut sidecar);

    if status.success() {
        Ok(())
    } else {
        Err(anyhow!("frontend exited with status {status}"))
    }
}

fn reserve_local_port() -> Result<u16> {
    let listener =
        TcpListener::bind((SIDECAR_HOST, 0)).context("failed to reserve sidecar port")?;
    let port = listener
        .local_addr()
        .context("failed to read reserved sidecar port")?
        .port();
    drop(listener);
    Ok(port)
}

fn spawn_sidecar(sidecar_path: &Path, app_root: &Path, port: u16) -> Result<Child> {
    let mut command = Command::new(sidecar_path);
    command
        .arg("--port")
        .arg(port.to_string())
        .env("ABK_DESKTOP_HOST", SIDECAR_HOST)
        .env("ABK_DESKTOP_APP_ROOT", app_root)
        .stdin(Stdio::null());
    if let Some(python_path) =
        resolve_optional_python_path(sidecar_path.parent().unwrap_or_else(|| Path::new(".")))
    {
        command.env("ABK_DESKTOP_PYTHON", python_path);
    }
    configure_child_stdio(&mut command);
    spawn_child(&mut command, "sidecar")
}

fn spawn_frontend(frontend_path: &Path, port: u16) -> Result<Child> {
    let mut args = env::args_os().skip(1).collect::<Vec<_>>();
    args.push("--abk-base-url".into());
    args.push(format!("http://{SIDECAR_HOST}:{port}").into());
    let mut command = Command::new(frontend_path);
    command
        .args(args.drain(..))
        .env(
            "ABK_DESKTOP_BASE_URL",
            format!("http://{SIDECAR_HOST}:{port}"),
        )
        .stdin(Stdio::null());
    configure_child_stdio(&mut command);
    spawn_child(&mut command, "frontend")
}

fn configure_child_stdio(command: &mut Command) {
    #[cfg(debug_assertions)]
    {
        command.stdout(Stdio::inherit()).stderr(Stdio::inherit());
    }
    #[cfg(not(debug_assertions))]
    {
        command.stdout(Stdio::null()).stderr(Stdio::null());
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        command.creation_flags(CREATE_NO_WINDOW);
    }
}

fn spawn_child(command: &mut Command, label: &str) -> Result<Child> {
    command
        .spawn()
        .with_context(|| format!("failed to start {label} process"))
}

fn wait_for_sidecar(port: u16) -> Result<()> {
    let deadline = Instant::now() + SIDECAR_START_TIMEOUT;
    loop {
        match sidecar_healthy(port) {
            Ok(true) => return Ok(()),
            Ok(false) if Instant::now() < deadline => thread::sleep(Duration::from_millis(250)),
            Err(_) if Instant::now() < deadline => thread::sleep(Duration::from_millis(250)),
            Err(error) => return Err(anyhow!(error)),
            Ok(false) => return Err(anyhow!("health probe did not return success")),
        }
    }
}

fn sidecar_healthy(port: u16) -> Result<bool> {
    let mut stream = TcpStream::connect((SIDECAR_HOST, port))?;
    stream.write_all(
        format!(
            "GET /api/v1/health HTTP/1.1\r\nHost: {SIDECAR_HOST}\r\nConnection: close\r\n\r\n"
        )
        .as_bytes(),
    )?;
    let mut buffer = String::new();
    stream.read_to_string(&mut buffer)?;
    Ok(buffer.contains("200 OK") || buffer.contains("\"status\":\"ok\""))
}

fn terminate_child(child: &mut Child) {
    let _ = child.kill();
    let _ = child.wait();
}

fn resolve_sidecar_path(launcher_dir: &Path) -> Result<PathBuf> {
    resolve_existing_path(
        "ABK_DESKTOP_SIDECAR_PATH",
        launcher_dir,
        &[
            executable_name("abk_sidecar"),
            executable_name("abk-desktop"),
        ],
    )
}

fn resolve_frontend_path(launcher_dir: &Path) -> Result<PathBuf> {
    let frontend_name = executable_name("abk_desktop");
    resolve_existing_path(
        "ABK_DESKTOP_FRONTEND_PATH",
        launcher_dir,
        &[
            frontend_name.clone(),
            PathBuf::from("flutter").join(&frontend_name),
            PathBuf::from("../lib/abk_desktop").join(&frontend_name),
            PathBuf::from("../../flutter_app/build/linux/x64/debug/bundle").join(&frontend_name),
        ],
    )
}

fn resolve_app_root(launcher_dir: &Path) -> Result<PathBuf> {
    if let Some(path) = env::var_os("ABK_DESKTOP_APP_ROOT")
        .map(PathBuf::from)
        .filter(|path| looks_like_app_root(path))
    {
        return Ok(path);
    }
    for relative in [
        PathBuf::from("../share/abk"),
        PathBuf::from("resources/abk"),
        PathBuf::from("../resources/abk"),
        PathBuf::from("../../.."),
    ] {
        let candidate = normalize_path(launcher_dir.join(relative));
        if looks_like_app_root(&candidate) {
            return Ok(candidate);
        }
    }
    Err(anyhow!("failed to locate packaged ABK app root"))
}

fn looks_like_app_root(path: &Path) -> bool {
    path.is_dir()
        && path.join("cli").join("abk.py").is_file()
        && path.join("hmbird_patch.c").is_file()
}

fn resolve_optional_python_path(launcher_dir: &Path) -> Option<PathBuf> {
    if let Some(path) = env::var_os("ABK_DESKTOP_PYTHON")
        .map(PathBuf::from)
        .filter(|path| path.is_file())
    {
        return Some(path);
    }
    for relative in [
        PathBuf::from("runtime")
            .join("python")
            .join(executable_name("python")),
        PathBuf::from("../runtime")
            .join("python")
            .join(executable_name("python")),
    ] {
        let candidate = normalize_path(launcher_dir.join(relative));
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    None
}

fn resolve_existing_path(
    env_key: &str,
    launcher_dir: &Path,
    candidates: &[PathBuf],
) -> Result<PathBuf> {
    if let Some(path) = env::var_os(env_key)
        .map(PathBuf::from)
        .filter(|path| path.is_file())
    {
        return Ok(path);
    }
    for candidate in candidates {
        let resolved = normalize_path(launcher_dir.join(candidate));
        if resolved.is_file() {
            return Ok(resolved);
        }
    }
    Err(anyhow!("failed to locate executable for {env_key}"))
}

fn executable_name(stem: &str) -> PathBuf {
    if cfg!(windows) {
        PathBuf::from(format!("{stem}.exe"))
    } else {
        PathBuf::from(stem)
    }
}

fn normalize_path(path: PathBuf) -> PathBuf {
    path.canonicalize().unwrap_or(path)
}
