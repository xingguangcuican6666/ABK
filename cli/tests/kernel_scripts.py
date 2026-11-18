"""把 workflow 的薄壳步骤映射回 platform-tools/kernel_build 下的脚本。

build.yml 现在只保留步骤名与单行脚本调用，具体逻辑位于
``platform-tools/kernel_build/<层>/<脚本>.sh`` 的 ``# ---- 步骤名 ----`` 分节里。
测试通过这里按步骤名取出对应的分节，沿用原先读取 workflow 正文的断言。
"""
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
KERNEL_BUILD = ROOT / "platform-tools" / "kernel_build"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


def step_commands(workflow_text):
    """步骤名 -> 该步骤执行的 Bash 脚本调用（不含行首的 bash）。

    retry 动作的步骤用 ``with.command``，普通步骤用 ``run``。
    """
    commands = {}
    for match in re.finditer(
        r"(?ms)^      - name: (?P<name>[^\n]+)\n(?P<body>(?:        [^\n]*\n)*)", workflow_text
    ):
        run = re.search(
            r"(?m)^ +?(?:run|command): bash (?P<command>[^\n]+)$", match.group("body")
        )
        if run:
            commands[match.group("name")] = run.group("command")
    return commands


class ScriptStep:
    """单个步骤对应的脚本、子命令与正文。"""

    def __init__(self, script, command, body, section):
        self.script = script
        self.command = command
        self.body = body
        self.section = section

    @property
    def path(self):
        return self.script

    @property
    def subcommand(self):
        return self.command.split()[-1]

    def python_code(self):
        """取出分节里的第一段 ``<<'PY'`` heredoc 正文。"""
        _, code = split_heredoc(self.body, "PY")
        return code

    def heredocs(self, tag="PY"):
        return split_all_heredocs(self.body, tag)


def split_heredoc(text, tag="PY"):
    """按 ``<<'TAG'`` 切分，返回 (heredoc 之前的 shell, heredoc 正文)。"""
    start = re.search(rf"<<'{re.escape(tag)}'\n", text)
    if start is None:
        raise AssertionError(f"script step has no <<'{tag}' heredoc")
    rest = text[start.end():]
    end = re.search(rf"(?m)^{re.escape(tag)}$", rest)
    if end is None:
        raise AssertionError(f"unterminated <<'{tag}' heredoc")
    return text[:start.start()], rest[:end.start()]


def split_all_heredocs(text, tag="PY"):
    """返回分节里的全部 heredoc 正文（按出现顺序）。"""
    codes = []
    position = 0
    while True:
        start = re.search(rf"<<'{re.escape(tag)}'\n", text[position:])
        if start is None:
            return codes
        begin = position + start.end()
        end = re.search(rf"(?m)^{re.escape(tag)}$", text[begin:])
        if end is None:
            raise AssertionError(f"unterminated <<'{tag}' heredoc")
        codes.append(text[begin:begin + end.start()])
        position = begin + end.start() + len(tag)


def step_section(script_path, name):
    """取脚本中 ``# ---- name ----`` 到下一个分节标记之间的函数体。"""
    text = Path(script_path).read_text(encoding="utf-8")
    header = f"# ---- {name} ----"
    matches = list(re.finditer(rf"(?m)^(# ---- .* ----)$", text))
    for index, match in enumerate(matches):
        if match.group(1) != header:
            continue
        end = matches[index + 1].start() if index + 1 < len(matches) else text.index(
            'case "${1:-}" in')
        body = text[match.end():end]
        opening = re.search(r"(?m)^ *\w[\w-]*\(\) \{\n", body)
        if opening is None:
            raise AssertionError(f"section without function body: {name}")
        # 去掉外层函数自身的收尾 ``}``，只保留函数体。
        inner = body[opening.end():].rstrip("\n")
        if inner.endswith("\n}"):
            inner = inner[:-2]
        return ScriptStep(Path(script_path), None, inner, name)
    raise AssertionError(f"section not found in {script_path}: {name}")


def step(name, workflow_text=None):
    """按 build.yml 的步骤名取出对应脚本分节。"""
    if workflow_text is None:
        workflow_text = WORKFLOW.read_text(encoding="utf-8")
    commands = step_commands(workflow_text)
    if name not in commands:
        raise AssertionError(f"workflow step not found: {name}")
    command = commands[name]
    result = step_section(script_path(command), name)
    result.command = command
    return result


def script_path(command):
    return ROOT / command.split()[0]
