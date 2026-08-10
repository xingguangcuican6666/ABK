#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys


MARKER = "ABK external late-load module support"


def replace_once(text: str, old: str, new: str, target: pathlib.Path) -> str:
    if old not in text:
        raise RuntimeError(f"expected block not found in {target}: {old[:80]!r}")
    return text.replace(old, new, 1)


def patch_cli(target: pathlib.Path) -> None:
    text = target.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"ksud external lkm cli patch already present: {target}")
        return

    text = replace_once(
        text,
        """        /// Specify kernel KMI version instead of auto-detection
        #[arg(long)]
        kmi: Option<String>,

        /// manager package name
""",
        f"""        /// Specify kernel KMI version instead of auto-detection
        #[arg(long)]
        kmi: Option<String>,

        /// {MARKER}: load kernelsu.ko from this external path instead of RustEmbed assets
        #[arg(long)]
        module: Option<PathBuf>,

        /// manager package name
""",
        target,
    )
    text = replace_once(
        text,
        """            kmi,
            package_name,
            spoof_release,
""",
        """            kmi,
            module,
            package_name,
            spoof_release,
""",
        target,
    )
    text = replace_once(
        text,
        """            if let Some(port) = magica {
                return crate::magica::run(port, &package_name, allow_shell).map_err(|e| {
                    error!("Error running magica: {e}");
                    e
                });
            }
            let result = crate::late_load::run(
                &package_name,
                kmi,
                allow_shell,
                spoof_release.as_ref(),
                spoof_version.as_ref(),
            );
""",
        """            if let Some(port) = magica {
                return crate::magica::run(port, &package_name, allow_shell, module.as_ref()).map_err(|e| {
                    error!("Error running magica: {e}");
                    e
                });
            }
            let result = crate::late_load::run(
                &package_name,
                kmi,
                module.as_ref(),
                allow_shell,
                spoof_release.as_ref(),
                spoof_version.as_ref(),
            );
""",
        target,
    )
    target.write_text(text, encoding="utf-8")
    print(f"patched ksud external lkm cli support: {target}")


def patch_late_load(target: pathlib.Path) -> None:
    text = target.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"ksud external lkm late-load patch already present: {target}")
        return

    text = replace_once(
        text,
        """    kmi: Option<String>,
    allow_shell: bool,
""",
        """    kmi: Option<String>,
    module: Option<&std::path::PathBuf>,
    allow_shell: bool,
""",
        target,
    )
    text = replace_once(
        text,
        """        // 3. Get kernelsu.ko from embedded assets
        let ko_name = format!("{kmi}_kernelsu.ko");
        let ko_data = assets::get_asset_data(&ko_name)
            .with_context(|| format!("Failed to get {ko_name} from assets"))?;

        // 4. Load kernelsu.ko from memory with manual relocation
        info!("Loading kernelsu.ko for KMI {kmi}...");
""",
        f"""        // 3. Get kernelsu.ko from an ABK supplied external path, or from embedded assets.
        // {MARKER}
        let (ko_data, ko_label) = if let Some(module_path) = module {{
            let data = std::fs::read(module_path)
                .with_context(|| format!("Failed to read external module {{}}", module_path.display()))?;
            (std::borrow::Cow::Owned(data), module_path.display().to_string())
        }} else {{
            let ko_name = format!("{{kmi}}_kernelsu.ko");
            let data = assets::get_asset_data(&ko_name)
                .with_context(|| format!("Failed to get {{ko_name}} from assets"))?;
            (data, ko_name)
        }};

        // 4. Load kernelsu.ko from memory with manual relocation
        info!("Loading kernelsu.ko for KMI {{kmi}} from {{ko_label}}...");
""",
        target,
    )
    text = replace_once(
        text,
        """        ksuinit::load_module(&ko_data, &params_cstr).context("Failed to load kernelsu.ko")?;
""",
        """        ksuinit::load_module(ko_data.as_ref(), &params_cstr).context("Failed to load kernelsu.ko")?;
""",
        target,
    )
    restart_old = '&format!("{package_name}/com.sukisu.ultra.ui.MainActivity"),'
    restart_new = '&format!("{package_name}/com.abk.kernel.MainActivity"),'
    if restart_old in text:
        text = text.replace(restart_old, restart_new, 1)
    elif restart_new not in text:
        raise RuntimeError(f"expected magica manager restart activity not found in {target}")
    target.write_text(text, encoding="utf-8")
    print(f"patched ksud external lkm late-load support: {target}")


def patch_magica(target: pathlib.Path) -> None:
    text = target.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"ksud external lkm magica patch already present: {target}")
        return

    text = replace_once(
        text,
        """fn connect_to_device(port: u16) -> Result<ADBTcpDevice> {
""",
        """fn shell_quote(value: &str) -> String {
    let escaped = value.replace("'", "'\\\\''");
    format!("'{escaped}'")
}

fn connect_to_device(port: u16) -> Result<ADBTcpDevice> {
""",
        target,
    )
    text = replace_once(
        text,
        """pub fn run(port: u16, package_name: &String, allow_shell: bool) -> Result<()> {
""",
        """pub fn run(
    port: u16,
    package_name: &String,
    allow_shell: bool,
    module: Option<&std::path::PathBuf>,
) -> Result<()> {
""",
        target,
    )
    text = replace_once(
        text,
        """    let allow_shell_arg = if allow_shell { " --allow-shell" } else { "" };
    let cmd = format!(
        "{} late-load --post-magica --package-name {}{}",
        self_path.display(),
        package_name,
        allow_shell_arg
    );
""",
        f"""    let allow_shell_arg = if allow_shell {{ " --allow-shell" }} else {{ "" }};
    let module_arg = module
        .map(|path| format!(" --module {{}}", shell_quote(&path.display().to_string())))
        .unwrap_or_default();
    // {MARKER}
    let cmd = format!(
        "{{}} late-load --post-magica --package-name {{}}{{}}{{}}",
        self_path.display(),
        package_name,
        allow_shell_arg,
        module_arg
    );
""",
        target,
    )
    target.write_text(text, encoding="utf-8")
    print(f"patched ksud external lkm magica support: {target}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-ksud-external-lkm.py <sukisu-source-dir>", file=sys.stderr)
        return 2

    source_dir = pathlib.Path(sys.argv[1]).resolve()
    targets = {
        "cli": source_dir / "userspace" / "ksud" / "src" / "cli.rs",
        "late_load": source_dir / "userspace" / "ksud" / "src" / "late_load.rs",
        "magica": source_dir / "userspace" / "ksud" / "src" / "magica.rs",
    }
    missing = [path for path in targets.values() if not path.is_file()]
    if missing:
        for path in missing:
            print(f"::error::{path} not found", file=sys.stderr)
        return 1

    try:
        patch_cli(targets["cli"])
        patch_late_load(targets["late_load"])
        patch_magica(targets["magica"])
    except RuntimeError as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
