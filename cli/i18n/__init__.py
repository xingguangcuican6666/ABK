#!/usr/bin/env python3
"""ABK CLI i18n - Internationalization support"""

import json
import os
from pathlib import Path

I18N_DIR = Path(__file__).parent
if os.name == "nt" and os.environ.get("APPDATA"):
    CONFIG_DIR = Path(os.environ["APPDATA"]) / "abk"
else:
    CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "abk"
CONFIG_FILE = CONFIG_DIR / "config.json"

LANGUAGE_CATALOGS = {
    "zh-CN": "zh-cn",
    "en-US": "en-us",
    "ru-RU": "ru-ru",
    "ja-JP": "ja-jp",
    "ko-KR": "ko-kr",
    "hi-IN": "hi-in",
    "de-DE": "de-de",
    "fr-FR": "fr-fr",
    "es-ES": "es-es",
    "pt-BR": "pt-br",
    "eo": "eo",
    "ja-JP-x-neko": "ja-jp-x-neko",
    "zh-CN-x-neko": "zh-cn-x-neko",
    "zh-CN-x-zako": "zh-cn-x-zako",
}
SUPPORTED_LANGUAGES = tuple(LANGUAGE_CATALOGS)
DEFAULT_LANGUAGE = "zh-CN"

_LEGACY_LANGUAGE_ALIASES = {
    "jp-neko": "ja-JP-x-neko",
    "zh-neko": "zh-CN-x-neko",
    "zh-zako": "zh-CN-x-zako",
}
LANGUAGE_STORAGE_IDS = {
    **{
        tag: catalog
        for tag, catalog in LANGUAGE_CATALOGS.items()
        if "-x-" not in tag.casefold()
    },
    "ja-JP-x-neko": "jp-neko",
    "zh-CN-x-neko": "zh-neko",
    "zh-CN-x-zako": "zh-zako",
}
_CANONICAL_LANGUAGE_TAGS = {
    tag.casefold(): tag for tag in SUPPORTED_LANGUAGES
}
_CANONICAL_LANGUAGE_TAGS.update(_LEGACY_LANGUAGE_ALIASES)
_LANGUAGE_FALLBACKS = {}
for _tag in SUPPORTED_LANGUAGES:
    if "-x-" not in _tag.casefold():
        _LANGUAGE_FALLBACKS.setdefault(_tag.split("-", 1)[0].casefold(), _tag)

_translations = {}
_lang = DEFAULT_LANGUAGE


def _normalized_language_candidates(value):
    if not isinstance(value, str):
        return []

    candidates = []
    for candidate in value.split(":"):
        candidate = candidate.strip()
        for separator in (".", "@"):
            candidate = candidate.split(separator, 1)[0]
        candidate = candidate.replace("_", "-").strip().casefold()
        if candidate:
            candidates.append(candidate)
    return candidates


def normalize_language_tag(value, allow_fallback=True):
    """Return a supported canonical language tag for a locale-like value."""
    for candidate in _normalized_language_candidates(value):
        canonical = _CANONICAL_LANGUAGE_TAGS.get(candidate)
        if canonical:
            return canonical
        if allow_fallback:
            canonical = _LANGUAGE_FALLBACKS.get(candidate.split("-", 1)[0])
            if canonical:
                return canonical
    return None


def catalog_name(value, allow_fallback=True):
    """Return the existing JSON catalog stem for a supported language tag."""
    canonical = normalize_language_tag(value, allow_fallback=allow_fallback)
    if canonical is None:
        return None
    return LANGUAGE_CATALOGS[canonical]


def language_storage_id(value):
    """Return the config value understood by both current and older CLIs."""
    canonical = normalize_language_tag(value, allow_fallback=False)
    if canonical is None:
        return None
    return LANGUAGE_STORAGE_IDS[canonical]


def detect_language():
    lang = os.environ.get("ABK_LANG", "")
    if not lang:
        config = {}
        if CONFIG_FILE.exists():
            try:
                loaded = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
                config = loaded if isinstance(loaded, dict) else {}
            except (OSError, UnicodeError, json.JSONDecodeError):
                pass
        lang = config.get("lang", "")
    if not lang:
        for env in ("LANGUAGE", "LC_ALL", "LC_MESSAGES", "LANG"):
            v = os.environ.get(env, "")
            if v:
                lang = v
                break
    return normalize_language_tag(lang) or DEFAULT_LANGUAGE


def load_translations(lang=None):
    global _translations, _lang
    if lang is None:
        lang = detect_language()
    canonical = normalize_language_tag(lang) or DEFAULT_LANGUAGE
    _lang = canonical

    path = I18N_DIR / f"{LANGUAGE_CATALOGS[canonical]}.json"
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
