import collections
import json
import os
import string
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import i18n


I18N_DIR = CLI_DIR / "i18n"
REFERENCE_LOCALE = "en-us"
EXPECTED_LANGUAGE_CATALOGS = {
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
EXPECTED_LANGUAGE_STORAGE_IDS = {
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
    "ja-JP-x-neko": "jp-neko",
    "zh-CN-x-neko": "zh-neko",
    "zh-CN-x-zako": "zh-zako",
}


def placeholders(template):
    """Return a multiset so translations may reorder, but not drop, fields."""
    return collections.Counter(
        field_name
        for _, field_name, _, _ in string.Formatter().parse(template)
        if field_name is not None
    )


class TranslationCatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogs = {}
        for path in sorted(I18N_DIR.glob("*.json")):
            cls.catalogs[path.stem] = json.loads(path.read_text(encoding="utf-8"))

    def test_every_locale_has_the_same_keys(self):
        reference_keys = set(self.catalogs[REFERENCE_LOCALE])
        for locale, catalog in self.catalogs.items():
            with self.subTest(locale=locale):
                self.assertEqual(reference_keys, set(catalog))

    def test_every_translation_preserves_placeholders(self):
        reference = self.catalogs[REFERENCE_LOCALE]
        for locale, catalog in self.catalogs.items():
            for key in reference.keys() & catalog.keys():
                with self.subTest(locale=locale, key=key):
                    self.assertIsInstance(catalog[key], str)
                    self.assertEqual(
                        placeholders(reference[key]),
                        placeholders(catalog[key]),
                    )

    def test_output_help_uses_a_platform_path_placeholder(self):
        for locale, catalog in self.catalogs.items():
            with self.subTest(locale=locale):
                self.assertEqual(
                    collections.Counter({"dir": 1}),
                    placeholders(catalog["arg_output"]),
                )
                self.assertNotIn("~/Downloads", catalog["arg_output"])

    def test_language_registry_is_canonical_and_covers_every_catalog(self):
        self.assertEqual(EXPECTED_LANGUAGE_CATALOGS, i18n.LANGUAGE_CATALOGS)
        self.assertEqual(
            tuple(EXPECTED_LANGUAGE_CATALOGS),
            i18n.SUPPORTED_LANGUAGES,
        )
        self.assertEqual(set(self.catalogs), set(i18n.LANGUAGE_CATALOGS.values()))
        self.assertEqual(
            EXPECTED_LANGUAGE_STORAGE_IDS,
            i18n.LANGUAGE_STORAGE_IDS,
        )

    def test_language_tags_are_normalized_from_every_supported_format(self):
        cases = {
            "en-US": "en-US",
            "EN_us.UTF-8": "en-US",
            " fr_FR@euro ": "fr-FR",
            "JA_jp_X_NEKO.UTF-8": "ja-JP-x-neko",
            "zh_cn_x_zako": "zh-CN-x-zako",
            "eo": "eo",
        }
        for value, expected in cases.items():
            with self.subTest(value=value):
                self.assertEqual(expected, i18n.normalize_language_tag(value))

    def test_legacy_style_ids_resolve_to_canonical_private_use_tags(self):
        aliases = {
            "jp-neko": "ja-JP-x-neko",
            "zh-neko": "zh-CN-x-neko",
            "zh-zako": "zh-CN-x-zako",
        }
        for value, expected in aliases.items():
            with self.subTest(value=value):
                self.assertEqual(expected, i18n.normalize_language_tag(value))

    def test_language_fallback_uses_the_available_regional_catalog(self):
        cases = {
            "en-GB": "en-US",
            "de-AT.UTF-8": "de-DE",
            "fr_CA": "fr-FR",
            "ja": "ja-JP",
            "zz-ZZ:ko_KR.UTF-8": "ko-KR",
        }
        for value, expected in cases.items():
            with self.subTest(value=value):
                self.assertEqual(expected, i18n.normalize_language_tag(value))
        self.assertIsNone(
            i18n.normalize_language_tag("en-GB", allow_fallback=False)
        )
        self.assertIsNone(i18n.normalize_language_tag(None))

    def test_detect_language_normalizes_env_config_and_system_locales(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            config_file = Path(temp_dir) / "config.json"
            with (
                mock.patch.object(i18n, "CONFIG_FILE", config_file),
                mock.patch.dict(
                    os.environ,
                    {"ABK_LANG": "EN_us.UTF-8@custom"},
                    clear=True,
                ),
            ):
                self.assertEqual("en-US", i18n.detect_language())

            config_file.write_text(
                json.dumps({"lang": "JA_jp_X_NEKO.UTF-8"}),
                encoding="utf-8",
            )
            with (
                mock.patch.object(i18n, "CONFIG_FILE", config_file),
                mock.patch.dict(os.environ, {}, clear=True),
            ):
                self.assertEqual("ja-JP-x-neko", i18n.detect_language())

            config_file.unlink()
            with (
                mock.patch.object(i18n, "CONFIG_FILE", config_file),
                mock.patch.dict(
                    os.environ,
                    {"LANG": "en_GB.UTF-8"},
                    clear=True,
                ),
            ):
                self.assertEqual("en-US", i18n.detect_language())

    def test_load_translations_maps_canonical_and_legacy_tags_to_catalogs(self):
        expected = json.loads(
            (I18N_DIR / "ja-jp-x-neko.json").read_text(encoding="utf-8")
        )
        self.addCleanup(i18n.load_translations, "zh-CN")

        for value in ("ja-JP-x-neko", "jp-neko"):
            with self.subTest(value=value):
                i18n.load_translations(value)
                self.assertEqual("ja-JP-x-neko", i18n._lang)
                self.assertEqual(expected, i18n._translations)

    def test_config_storage_ids_remain_compatible_with_older_cli_versions(self):
        cases = {
            "en-US": "en-us",
            "en-us": "en-us",
            "ja-JP-x-neko": "jp-neko",
            "jp-neko": "jp-neko",
            "zh-CN-x-zako": "zh-zako",
        }
        for value, expected in cases.items():
            with self.subTest(value=value):
                self.assertEqual(expected, i18n.language_storage_id(value))
        self.assertIsNone(i18n.language_storage_id("en-GB"))


if __name__ == "__main__":
    unittest.main()
