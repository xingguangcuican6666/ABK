import os
import re
import base64
import time
import http.client
import urllib.request
import urllib.error
from datetime import datetime

BASE_URL = "https://android.googlesource.com/kernel/common/+/refs/heads"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "data")

# (android版本, 内核版本): (起始日期, 结束日期, deprecated截止日期)
# 结束日期为 None 表示活跃版本，运行时自动使用当前月份
TARGETS = {
    ("android12", "5.10"): ("2021-08", "2025-12", "2024-08"),
    ("android13", "5.15"): ("2022-06", "2025-12", "2024-09"),
    ("android14", "6.1"):  ("2023-06", None,       "2024-09"),
    ("android15", "6.6"):  ("2024-10", None,       ""),
    ("android16", "6.12"): ("2025-06", None,       ""),
    ("android17", "6.18"): ("2026-04", None,       ""),
}

import binascii

ERRORS = (urllib.error.HTTPError, urllib.error.URLError,
          TimeoutError, http.client.RemoteDisconnected,
          ConnectionResetError, OSError, binascii.Error)


def get_end_date(end: str | None) -> str:
    """返回结束日期：如果为 None 则使用当前月份"""
    if end is not None:
        return end
    return datetime.now().strftime("%Y-%m")


def make_date_range(start: str, end: str) -> list[str]:
    """生成从 start 到 end 的 YYYY-MM 列表"""
    sy, sm = map(int, start.split("-"))
    ey, em = map(int, end.split("-"))
    dates = []
    y, m = sy, sm
    while (y, m) <= (ey, em):
        dates.append(f"{y}-{m:02d}")
        m += 1
        if m > 12:
            m = 1
            y += 1
    return dates


def try_fetch(url: str) -> str | None:
    """尝试请求一个 URL，失败返回 None"""
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:
            return base64.b64decode(resp.read()).decode("utf-8", errors="replace")
    except ERRORS:
        return None


def fetch_makefile(android_ver: str, kernel_ver: str, date: str,
                   dep_cutoff: str) -> str | None:
    """获取日期分支 Makefile，优先尝试预期路径，失败则回退"""
    branch = f"{android_ver}-{kernel_ver}-{date}"
    if dep_cutoff and date <= dep_cutoff:
        paths = [f"deprecated/{branch}", branch]
    else:
        paths = [branch, f"deprecated/{branch}"]

    for p in paths:
        url = f"{BASE_URL}/{p}/Makefile?format=TEXT"
        text = try_fetch(url)
        if text is not None:
            return text
        time.sleep(0.3)
    return None


def fetch_lts(android_ver: str, kernel_ver: str) -> str | None:
    """获取 LTS 分支 Makefile"""
    lts_branch = f"{android_ver}-{kernel_ver}-lts"
    url = f"{BASE_URL}/{lts_branch}/Makefile?format=TEXT"
    return try_fetch(url)


def parse_version(makefile_text: str) -> tuple[str, str, str] | None:
    """从 Makefile 提取 VERSION, PATCHLEVEL, SUBLEVEL"""
    vals = {}
    for key in ("VERSION", "PATCHLEVEL", "SUBLEVEL"):
        m = re.search(rf"^{key}\s*=\s*(\d+)", makefile_text, re.MULTILINE)
        if not m:
            return None
        vals[key] = m.group(1)
    return vals["VERSION"], vals["PATCHLEVEL"], vals["SUBLEVEL"]


def json_path(android_ver: str, kernel_ver: str) -> str:
    """返回对应的 JSON 文件路径"""
    return os.path.join(DATA_DIR, android_ver, f"{kernel_ver}.json")
