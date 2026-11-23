#!/usr/bin/env python3
"""Derive the bundled ABK fonts from the upstream LXGW releases.

Yozai (悠哉字体) is the UI family: a softer hand-written Han face than WenKai's
brush-flavoured Kai. WenKai Mono stays the mono role because Yozai mono is thin.

The upstream release files are 15 MB each, so both families are subset here to
GB2312 level 1 plus every character the app and the component library actually
render, which keeps the AAR and APK sizes honest.

Requires fontTools. Inputs come from `build/font-src` or `--src`; nothing is
downloaded, so a rebuild stays deterministic. The mono source is a 15.9 MB face,
so only its Compose TTF is regenerated here (the design repository also derives
web woff2 slices; this repository does not ship a web catalog).
"""
import argparse
import glob
import json
from pathlib import Path

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
NATIVE_DIR = ROOT / "abk-ui/src/main/res/font"
FONTS_JSON = ROOT / "abk-ui/src/main/assets/abk/fonts.json"

# role -> (upstream file, Compose resource name, CSS family weight)
FAMILY = [
    ("Yozai-Regular.ttf", "abk_yozai_regular", 400),
    ("Yozai-Medium.ttf", "abk_yozai_medium", 500),
]
MONO = ("LXGWWenKaiMono-Regular.ttf", "abk_wenkai_mono_regular")
Yozai_VERSION = "v0.868"
Yozai_BASE = f"https://github.com/lxgw/yozai-font/releases/download/{Yozai_VERSION}"
Wenkai_VERSION = "v1.520"
Wenkai_BASE = f"https://github.com/lxgw/LxgwWenKai/releases/download/{Wenkai_VERSION}"

# GB2312 level 1 covers everyday Simplified Chinese; anything outside it in the
# app is picked up below, and the fallback chain still has the system font.
LEVEL1_ROWS = range(0xB0, 0xD8)


def gb2312_level1():
    chars = set()
    for high in LEVEL1_ROWS:
        for low in range(0xA1, 0xFF):
            try:
                chars.add(bytes([high, low]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return chars


def used_chars():
    chars = set()
    for pattern in ["app/src/main/**/*.kt", "app/src/main/res/values*/**/*.xml",
                    "abk-ui/src/main/kotlin/**/*.kt"]:
        for path in glob.glob(str(ROOT / pattern), recursive=True):
            chars |= set(Path(path).read_text(encoding="utf-8"))
    return chars


def latin_points():
    return (list(range(0x20, 0x7F)) + [0xA0, 0xB0, 0xB7, 0xD7, 0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D,
                                       0x2022, 0x2026, 0x2191, 0x2193, 0xFFFD])


def han_points():
    return [ord(c) for c in gb2312_level1() | used_chars() if ord(c) > 0x7F] + [0x3000, 0x3001, 0x3002]


def subset(source: Path, unicodes, flavor: str, dest: Path):
    options = Options()
    options.flavor = flavor
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.name_legacy = True
    options.notdef_outline = True
    options.recalc_bounds = True
    options.drop_tables += ["FFTM", "morx", "feat"]  # non-font tables fontTools cannot subset
    font = TTFont(source)
    subsetter = Subsetter(options=options)
    subsetter.populate(unicodes=unicodes)
    subsetter.subset(font)
    dest.parent.mkdir(parents=True, exist_ok=True)
    font.save(dest)
    font.close()
    return dest.stat().st_size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=ROOT / "build/font-src",
                        help="directory holding Yozai-Regular.ttf, Yozai-Medium.ttf "
                             "and LXGWWenKaiMono-Regular.ttf")
    args = parser.parse_args()

    latin, han = latin_points(), han_points()
    points = latin + han
    entries = {}
    for upstream, resource, weight in FAMILY:
        source = args.src / upstream
        ttf = NATIVE_DIR / f"{resource}.ttf"
        size = subset(source, points, "none", ttf)
        print(f"{ttf.relative_to(ROOT)}: {size} bytes")
        entries[f"{resource}.ttf"] = {
            "url": f"{Yozai_BASE}/{upstream}",
            "license": "OFL 1.1 · assets/abk/licenses/OFL-yozai.txt",
            "family-name": "Yozai (not renamed: SIL OFL reserved font name applies to the source)",
            "subset": "GB2312 level 1 + characters used by the app and the component library",
            "bytes": size,
        }
    upstream, resource = MONO
    ttf = NATIVE_DIR / f"{resource}.ttf"
    size = subset(args.src / upstream, points, "none", ttf)
    print(f"{ttf.relative_to(ROOT)}: {size} bytes")
    entries[f"{resource}.ttf"] = {
        "url": f"{Wenkai_BASE}/{upstream}",
        "license": "OFL 1.1 · assets/abk/licenses/OFL-wenkai.txt",
        "subset": "GB2312 level 1 + characters used by the app and the component library",
        "bytes": size,
    }
    FONTS_JSON.write_text(json.dumps({
        "version": {"yozai": Yozai_VERSION, "wenkai": Wenkai_VERSION},
        "files": entries,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote fonts.json")


if __name__ == "__main__":
    main()
