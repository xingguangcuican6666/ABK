#!/usr/bin/env python3
"""ABK CLI i18n - Internationalization support"""

import json
import os
from pathlib import Path

I18N_DIR = Path(__file__).parent
CONFIG_DIR = Path.home() / ".config" / "abk"
CONFIG_FILE = CONFIG_DIR / "config.json"

_translations = {}
_lang = "zh-cn"


def detect_language():
    lang = os.environ.get("ABK_LANG", "")
    if not lang:
        config = {}
        if CONFIG_FILE.exists():
            try:
                config = json.loads(CONFIG_FILE.read_text())
            except json.JSONDecodeError:
                pass
        lang = config.get("lang", "")
    if not lang:
        for env in ("LANGUAGE", "LC_ALL", "LC_MESSAGES", "LANG"):
            v = os.environ.get(env, "")
            if v:
                lang = v.split(".")[0].split(":")[0].replace("_", "-").lower()
                break
    if not lang or lang not in ("zh-cn", "en-us", "ru-ru", "ja-jp", "ko-kr", "hi-in", "de-de", "fr-fr", "es-es", "pt-br", "jp-neko", "zh-neko", "eo", "zh-zako"):
        lang = "zh-cn"
    return lang


def load_translations(lang=None):
    global _translations, _lang
    if lang is None:
        lang = detect_language()
    _lang = lang
    
    path = I18N_DIR / f"{lang}.json"
    try:
        _translations = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        _translations = {}


def t(key, **kwargs):
    if not _translations:
        load_translations()
    text = _translations.get(key, key)
    if kwargs:
        return text.format(**kwargs)
    return text


load_translations()
