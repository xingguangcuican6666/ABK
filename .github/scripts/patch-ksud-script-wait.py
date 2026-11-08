#!/usr/bin/env python3
"""Carry ABK's local fix for an upstream ksud compile break until upstream lands it.

Two upstream commits have broken the same line in `run_stage`, in different ways, and
both are invisible to upstream's own CI:

* `932d9bd2` ("ksud: add timeout for boot stage scripts", #3797) replaced run_stage's
  `block: bool` with `wait: ScriptWait` but left the call site naming `block`:

      exec_stage_lua(stage, block, "kernelsu")        // E0425: cannot find `block`

* `1c56b5e9` ("kusd: fix build") swapped that call site to pass `wait`, but did not
  widen exec_stage_lua, which still takes a plain bool:

      exec_stage_lua(stage, wait, "kernelsu")         // E0308: expected bool, found ScriptWait

The line is gated on `cfg(all(target_os = "android", target_arch = "aarch64"))`, so only
an Android cross-build compiles it. Upstream checks on a host target, where the whole
`init_event` module is excluded -- an upstream refactor their CI accepts breaks us.

exec_stage_lua still takes a plain bool and forwards it to run_lua, whose parameter is
named `_wait` and never read, so any bool preserves current behaviour. NoWait is what
both non-blocking callers pass, so mapping NoWait -> false and everything else -> true
reproduces the pre-932d9bd2 calls exactly.

When none of the known-broken forms match, the script is a no-op: upstream may have fixed
the line outright, and failing then would break the build at exactly the moment this patch
stops being needed.

Delete this patch (and its call sites in the two app workflows) once upstream main
compiles the path.
"""
from __future__ import annotations

import pathlib
import sys


MARKER = "ABK upstream fix: exec_stage_lua call left out of step with run_stage"
CFG_GATE = '    #[cfg(all(target_os = "android", target_arch = "aarch64"))]\n'

# (label, broken call) for each shape upstream has shipped. The label names the compiler
# error that shape produces, so the log says which upstream revision was found.
BROKEN_CALLS = [
    ("E0425: run_stage still names block", 'crate::module::exec_stage_lua(stage, block, "kernelsu")'),
    ("E0308: exec_stage_lua takes a bool", 'crate::module::exec_stage_lua(stage, wait, "kernelsu")'),
]

FIXED_CALL = f'crate::module::exec_stage_lua(stage, !matches!(wait, ScriptWait::NoWait), "kernelsu")'


def _broken_block(call: str) -> str:
    """Upstream's comment, cfg gate and call, as they appear verbatim.

    Anchoring on all three together means a reformat that keeps the call but moves the
    comment is reported rather than silently patched.
    """
    return (
        "    // run lua stage script\n"
        f"{CFG_GATE}"
        f"    if let Err(e) = {call} {{\n"
    )


def _patched_block() -> str:
    return (
        f"    // {MARKER}\n"
        f"{CFG_GATE}"
        f"    if let Err(e) = {FIXED_CALL} {{\n"
    )


BROKEN_BLOCKS = [
    (label, _broken_block(call)) for label, call in BROKEN_CALLS
]
PATCHED_BLOCK = _patched_block()


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-ksud-script-wait.py <sukisu-source-dir>", file=sys.stderr)
        return 2

    source_dir = pathlib.Path(sys.argv[1]).resolve()
    target = source_dir / "userspace" / "ksud" / "src" / "init_event.rs"
    if not target.is_file():
        # A moved or renamed file is an incompatibility worth failing on, unlike the
        # already-fixed case below.
        print(f"::error::{target} not found", file=sys.stderr)
        return 1

    text = target.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"ksud script-wait patch already present: {target}")
        return 0

    for label, broken in BROKEN_BLOCKS:
        if broken in text:
            target.write_text(text.replace(broken, PATCHED_BLOCK), encoding="utf-8")
            print(f"patched ksud run_stage lua call ({label}): {target}")
            return 0

    for label, call in BROKEN_CALLS:
        if call in text:
            # The call is there but its surroundings moved, so the block match above did
            # not fire. Patching anyway risks rewriting a line whose context this script
            # has not read; failing is the honest outcome.
            print(
                f"::error::{label} found in {target} but its surrounding block does not match",
                file=sys.stderr,
            )
            return 1

    print(f"::warning::no known-broken exec_stage_lua call in {target}; nothing to do")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())