import collections
import json
import string
import unittest
from pathlib import Path


I18N_DIR = Path(__file__).resolve().parents[1] / "i18n"
REFERENCE_LOCALE = "en-us"


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


if __name__ == "__main__":
    unittest.main()
