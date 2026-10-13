"""Builds the mod's dyslexia-friendly font from OpenDyslexic.

Minecraft's font loader only accepts TrueType outlines, and OpenDyslexic ships
only as OpenType/CFF. Under the SIL Open Font License, converting the format is
a modification, and the Reserved Font Name "OpenDyslexic" may not be used for a
modified version - so this script converts the outlines AND renames the font.
The result stays under the OFL, keeps the original copyright notice, and records
what was changed (see tools/fonts/README.md).

Usage (fontTools lives in a venv on D:, per the project's install rule):
    D:\\dev-tools\\fonttools-venv\\Scripts\\python.exe tools/fonts/convert_opendyslexic.py <OpenDyslexic-Regular.otf>

Writes cim-1.21.1/src/client/resources/assets/cim/font/equestria-reader-regular.ttf
"""

import sys
from pathlib import Path

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable

NEW_FAMILY = "Equestria Reader"
NEW_POSTSCRIPT_FAMILY = "EquestriaReader"
RESERVED_NAME = "OpenDyslexic"
MODIFICATION_NOTE = (
    "Equestria Reader is a Modified Version of OpenDyslexic by Abbie Gonzalez, "
    "converted from CFF to TrueType outlines and renamed (as the SIL Open Font "
    "License requires for modified versions) so it can be used in Minecraft. "
    "Glyph designs are unchanged apart from the curve conversion."
)

# Same settings as fontTools' own otf2ttf example.
MAX_ERR = 1.0
REVERSE_DIRECTION = True

OUTPUT = Path(__file__).resolve().parents[2] / "cim-1.21.1/src/client/resources/assets/cim/font/equestria-reader-regular.ttf"


def to_quadratic(glyph_set):
    glyphs = {}
    for name in glyph_set.keys():
        tt_pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(tt_pen, MAX_ERR, reverse_direction=REVERSE_DIRECTION))
        glyphs[name] = tt_pen.glyph()
    return glyphs


def convert_outlines(font):
    assert font.sfntVersion == "OTTO" and "CFF " in font, "expected an OpenType/CFF font"
    glyph_order = font.getGlyphOrder()

    font["loca"] = newTable("loca")
    font["glyf"] = glyf = newTable("glyf")
    glyf.glyphOrder = glyph_order
    glyf.glyphs = to_quadratic(font.getGlyphSet())
    del font["CFF "]
    if "VORG" in font:
        del font["VORG"]
    glyf.compile(font)

    hmtx = font["hmtx"]
    for name, glyph in glyf.glyphs.items():
        if hasattr(glyph, "xMin"):
            hmtx[name] = (hmtx[name][0], glyph.xMin)

    font["maxp"] = maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = max(
        (len(getattr(g, "components", [])) for g in glyf.glyphs.values()), default=0)
    maxp.compile(font)

    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = glyph_order
    try:
        post.compile(font)
    except OverflowError:
        post.formatType = 3.0

    font.sfntVersion = "\000\001\000\000"


def rename(font):
    name_table = font["name"]
    for record in name_table.names:
        text = record.toUnicode()
        if record.nameID == 6:  # PostScript name: no spaces allowed
            text = text.replace(RESERVED_NAME, NEW_POSTSCRIPT_FAMILY)
        else:
            text = text.replace(RESERVED_NAME, NEW_FAMILY)
        record.string = text
    # Description (nameID 10): record the modification and credit the original.
    for platform_id, encoding_id, language_id in {(r.platformID, r.platEncID, r.langID) for r in name_table.names}:
        name_table.setName(MODIFICATION_NOTE, 10, platform_id, encoding_id, language_id)

    leftovers = [r.nameID for r in name_table.names if RESERVED_NAME in r.toUnicode() and r.nameID not in (0, 10, 13)]
    assert not leftovers, f"Reserved Font Name still present in name IDs {leftovers}"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    font = TTFont(sys.argv[1])
    convert_outlines(font)
    rename(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
