"""KernelSU 补丁模块编排脚本（.github/scripts/kernel-patcher.py）的单元测试。"""

from __future__ import annotations

import importlib.util
import shutil
import subprocess
import sys
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHER_PATH = ROOT / ".github" / "scripts" / "kernel-patcher.py"


def load_patcher():
    spec = importlib.util.spec_from_file_location("kernel_patcher", PATCHER_PATH)
    module = importlib.util.module_from_spec(spec)
    # dataclasses 需要能从 sys.modules 里找回模块（PEP 563 注解解析）。
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


patcher = load_patcher()


def git(*args, cwd):
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        check=True,
    ).stdout


def init_repo(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    git("init", "-q", "-b", "main", cwd=path)
    git("config", "user.email", "t@example.com", cwd=path)
    git("config", "user.name", "t", cwd=path)


class ModuleSpecTests(unittest.TestCase):
    def test_parses_urls_and_refs(self):
        specs = patcher.parse_module_spec(
            "https://example.com/a.git|https://example.com/b.git@v1.2|git@host:c.git@abc123"
        )
        self.assertEqual([s.url for s in specs], [
            "https://example.com/a.git",
            "https://example.com/b.git",
            "git@host:c.git",
        ])
        self.assertEqual([s.ref for s in specs], ["", "v1.2", "abc123"])

    def test_empty_input_yields_no_specs(self):
        self.assertEqual(patcher.parse_module_spec("   "), [])

    def test_rejects_unknown_protocol(self):
        with self.assertRaises(patcher.PatchError):
            patcher.parse_module_spec("file:///etc/passwd")

    def test_rejects_empty_url(self):
        with self.assertRaises(patcher.PatchError):
            patcher.parse_module_spec("@main")

    def test_name_is_sanitized(self):
        spec = patcher.RepoSpec(url="https://example.com/weird name!.git", ref="")
        self.assertEqual(spec.name, "weird_name_")


class PatchRoutingTests(unittest.TestCase):
    def _patch(self, path: str) -> str:
        return textwrap.dedent(
            f"""\
            diff --git a/{path} b/{path}
            --- a/{path}
            +++ b/{path}
            @@ -1 +1 @@
            -old
            +new
            """
        )

    def test_routes_by_first_path_component(self):
        text = self._patch("KernelSU/kernel/a.c") + self._patch("common/drivers/b.c")
        buckets = patcher.route_patch(text)
        self.assertEqual(sorted(buckets), ["KernelSU", "common"])
        self.assertIn("KernelSU/kernel/a.c", buckets["KernelSU"])
        self.assertIn("common/drivers/b.c", buckets["common"])

    def test_rejects_paths_outside_allowed_roots(self):
        for bad in ("drivers/foo.c", "/etc/passwd", "../escape.c"):
            with self.subTest(path=bad):
                with self.assertRaises(patcher.PatchError):
                    patcher.route_patch(self._patch(bad))

    def test_rejects_text_before_first_diff(self):
        with self.assertRaises(patcher.PatchError):
            patcher.route_patch("junk\n" + self._patch("KernelSU/a.c"))


class GroupingTests(unittest.TestCase):
    def _module(self, module_id, depends=(), priority=50):
        return patcher.ModuleInfo(
            root=Path("/tmp") / module_id,
            id=module_id,
            priority=priority,
            depends=tuple(depends),
        )

    def test_independent_modules_are_separate_groups(self):
        groups = patcher.group_modules([self._module("a"), self._module("b")])
        self.assertEqual([g.name for g in groups], ["a", "b"])

    def test_dependency_chain_is_one_group(self):
        modules = [
            self._module("a", depends=["b"]),
            self._module("b"),
            self._module("c"),
        ]
        groups = patcher.group_modules(modules)
        self.assertEqual([g.name for g in groups], ["a+b", "c"])

    def test_missing_dependency_fails(self):
        with self.assertRaises(patcher.PatchError) as ctx:
            patcher.group_modules([self._module("a", depends=["ghost"])])
        self.assertIn("ghost", str(ctx.exception))

    def test_cycle_is_detected(self):
        modules = [
            self._module("a", depends=["b"]),
            self._module("b", depends=["a"]),
        ]
        cycle = patcher.detect_cycle(modules)
        self.assertIsNotNone(cycle)
        self.assertEqual(cycle[0], cycle[-1])

    def test_no_cycle_returns_none(self):
        modules = [self._module("a", depends=["b"]), self._module("b")]
        self.assertIsNone(patcher.detect_cycle(modules))

    def test_patch_order_sorts_by_priority_then_id(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:

            def make(module_id, priority):
                root = Path(tmp) / module_id
                (root / "patches" / "android14-6.1").mkdir(parents=True)
                (root / "patches" / "android14-6.1" / "01-fix.patch").write_text("x", encoding="utf-8")
                return patcher.ModuleInfo(
                    root=root, id=module_id, priority=priority, depends=()
                )

            order = patcher.order_patches(
                [make("a", 10), make("b", 90)], "android14", "6.1"
            )
            self.assertEqual([m.id for m, _ in order], ["b", "a"])


class ManifestTests(unittest.TestCase):
    def test_round_trip(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            manifest = Path(tmp) / "m.tsv"
            clones = [
                patcher.Clone(
                    index=1,
                    spec=patcher.RepoSpec(url="https://x/a.git", ref="v1"),
                    path=Path(tmp) / "a",
                    head_sha="deadbeef",
                )
            ]
            patcher.write_manifest(clones, manifest)
            restored = patcher.read_manifest(manifest)
            self.assertEqual(len(restored), 1)
            self.assertEqual(restored[0].spec.ref, "v1")
            self.assertEqual(restored[0].head_sha, "deadbeef")

    def test_missing_manifest_is_empty(self):
        self.assertEqual(patcher.read_manifest(Path("/nonexistent/manifest.tsv")), [])


@unittest.skipUnless(shutil.which("git"), "需要 git")
class ApplyEndToEndTests(unittest.TestCase):
    """用真实 git 仓库跑完整分组 → 合并 → 回填链路（NyaSU SDK 用假实现）。"""

    def setUp(self):
        import tempfile

        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.kernel_root = self.tmp / "root"
        init_repo(self.kernel_root / "KernelSU")
        (self.kernel_root / "KernelSU" / "kernel").mkdir()
        (self.kernel_root / "KernelSU" / "kernel" / "a.c").write_text("orig\n", encoding="utf-8")
        git("add", "-A", cwd=self.kernel_root / "KernelSU")
        git("commit", "-qm", "init", cwd=self.kernel_root / "KernelSU")

        init_repo(self.kernel_root / "common")
        (self.kernel_root / "common" / "drivers").mkdir()
        (self.kernel_root / "common" / "drivers" / "b.c").write_text("core\n", encoding="utf-8")
        git("add", "-A", cwd=self.kernel_root / "common")
        git("commit", "-qm", "init", cwd=self.kernel_root / "common")

        self.sdk = self.tmp / "sdk"
        (self.sdk / "ksuhook").mkdir(parents=True)
        (self.sdk / "ksuhook" / "__init__.py").write_text("", encoding="utf-8")
        (self.sdk / "ksuhook" / "__main__.py").write_text(
            textwrap.dedent(
                """\
                import sys
                from pathlib import Path

                args = sys.argv[1:]
                if args and args[0] == "generate":
                    out = Path(args[args.index("--out") + 1])
                    repo = Path(args[args.index("--repo") + 1])
                    target = repo / "kernel" / "generated.c"
                    out.write_text(
                        "diff --git a/kernel/generated.c b/kernel/generated.c\\n"
                        "new file mode 100644\\n"
                        "--- /dev/null\\n"
                        "+++ b/kernel/generated.c\\n"
                        "@@ -0,0 +1 @@\\n"
                        "+generated\\n",
                        encoding="utf-8",
                    )
                    print(f"wrote {out}")
                sys.exit(0)
                """
            ),
            encoding="utf-8",
        )

    def tearDown(self):
        self._tmp.cleanup()

    def _module(self, name, module_id, depends="", patch_body=None, patch_path=None):
        root = self.tmp / "modules" / name
        (root / "src").mkdir(parents=True)
        (root / "module.prop").write_text(
            f"id={module_id}\nname={name}\nversion=1\n"
            + (f"depends={depends}\n" if depends else ""),
            encoding="utf-8",
        )
        (root / "hooks.toml").write_text("[[hook]]\nid=x\ntarget=t\nentry=e\n", encoding="utf-8")
        (root / "src" / f"{module_id}.c").write_text("int e(void){return 0;}\n", encoding="utf-8")
        if patch_body is not None:
            directory = root / "patches" / "android14-6.1"
            directory.mkdir(parents=True)
            (directory / "01.patch").write_text(patch_body, encoding="utf-8")
        return root

    def _patch(self, path, old="orig", new="patched"):
        return (
            f"diff --git a/{path} b/{path}\n"
            f"--- a/{path}\n"
            f"+++ b/{path}\n"
            "@@ -1 +1 @@\n"
            f"-{old}\n"
            f"+{new}\n"
        )

    def _run(self, modules):
        return patcher.apply_modules(
            module_dirs=modules,
            kernel_root=self.kernel_root,
            sdk_dir=self.sdk,
            android_version="android14",
            kernel_version="6.1",
            workroot=self.tmp / "work",
            log=lambda *_: None,
        )

    def test_applies_group_patches_and_sdk_output(self):
        m1 = self._module("m1", "mod_a", patch_body=self._patch("common/drivers/b.c", "core"))
        m2 = self._module("m2", "mod_b", patch_body=self._patch("KernelSU/kernel/a.c", "orig"))
        result = self._run([m1, m2])

        self.assertEqual(sorted(m.id for m in result.modules), ["mod_a", "mod_b"])
        self.assertEqual(len(result.groups), 2)
        self.assertEqual(
            (self.kernel_root / "common" / "drivers" / "b.c").read_text(encoding="utf-8"),
            "patched\n",
        )
        self.assertEqual(
            (self.kernel_root / "KernelSU" / "kernel" / "a.c").read_text(encoding="utf-8"),
            "patched\n",
        )
        self.assertTrue((self.kernel_root / "KernelSU" / "kernel" / "generated.c").is_file())

    def test_dependent_modules_share_one_group(self):
        m1 = self._module("m1", "mod_a", patch_body=self._patch("KernelSU/kernel/a.c"))
        m2 = self._module("m2", "mod_b", depends="mod_a")
        result = self._run([m1, m2])
        self.assertEqual([g.name for g in result.groups], ["mod_a+mod_b"])

    def test_conflicting_groups_fail_without_touching_live_tree(self):
        m1 = self._module("m1", "mod_a", patch_body=self._patch("common/drivers/b.c", "core", "A"))
        m2 = self._module("m2", "mod_b", patch_body=self._patch("common/drivers/b.c", "core", "B"))
        before = (self.kernel_root / "common" / "drivers" / "b.c").read_text(encoding="utf-8")
        with self.assertRaises(patcher.PatchError):
            self._run([m1, m2])
        after = (self.kernel_root / "common" / "drivers" / "b.c").read_text(encoding="utf-8")
        self.assertEqual(before, after)

    def test_bad_patch_root_is_rejected(self):
        m1 = self._module("m1", "mod_a", patch_body=self._patch("drivers/evil.c"))
        with self.assertRaises(patcher.PatchError):
            self._run([m1])
        self.assertFalse((self.kernel_root / "drivers" / "evil.c").exists())


if __name__ == "__main__":
    unittest.main()
