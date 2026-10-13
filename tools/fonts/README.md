# Fonts

## Equestria Reader (the dyslexia-friendly font option)

A Modified Version of [OpenDyslexic](https://github.com/antijingoist/opendyslexic)
by Abbie Gonzalez, under the SIL Open Font License 1.1.

**Why it's modified:** Minecraft's font loader only accepts TrueType outlines and
rejects OpenType/CFF ("Font is not in TTF format, was CFF"), and OpenDyslexic only
ships as OpenType/CFF. The OFL treats a format change as a modification, and a
modified version may not use the Reserved Font Name "OpenDyslexic" — so the font
is converted *and* renamed.

**What changed:**
- Outlines converted from cubic (CFF) to quadratic (TrueType) curves with
  fontTools' cu2qu, maximum error 1 font unit. Glyph designs are otherwise
  unchanged.
- Every "OpenDyslexic" in the font's name table replaced with "Equestria Reader"
  ("EquestriaReader" in the PostScript name); the description records the
  modification and credits the original.
- The original copyright notice and license fields are kept.

**Shipped with the mod** (`cim-1.21.1/src/client/resources/assets/cim/font/`):
`equestria-reader-regular.ttf`, `equestria-reader-ofl.txt` (the license text,
unaltered), and `equestria-reader-notice.txt`.

**Rebuilding:** download `OpenDyslexic-Regular.otf` from the OpenDyslexic
repository's `compiled/` folder, then:

```
D:\dev-tools\fonttools-venv\Scripts\python.exe tools/fonts/convert_opendyslexic.py OpenDyslexic-Regular.otf
```

(fontTools is installed in that venv: `python -m venv D:\dev-tools\fonttools-venv`,
then `pip install fonttools`.)

**In-game naming:** the setting is labeled "Dyslexia-Friendly Font" and never
"OpenDyslexic", since the OFL also restricts using the original's name to promote
a modified version (acknowledging it as the source, as above, is fine).
