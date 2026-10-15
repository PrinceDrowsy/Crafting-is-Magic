# Optional hums pack

Ponies in Crafting is Magic hum little tunes as they go about their day. The mod ships with
original tunes only. This datapack is the place for tunes **from the show and the fandom** — songs
you'd love to hear a pony hum on their way to work, or the Nightmare Night fan song on Nightmare
Night.

It ships **empty on purpose**: melodies are copyrighted works in their own right (the show's songs
belong to Hasbro, fan songs to their creators), so the mod doesn't include them. Add your own
transcriptions here for your own world.

## Installing

Copy this folder into your world's `datapacks/` folder (`saves/<world>/datapacks/cim-show-hums`) and
run `/reload` (or reopen the world).

## Adding a tune

Put a `.json` file in `data/cim_show_hums/hums/`, one tune per file:

```json
{
  "scale": "major",
  "tempo": 4,
  "notes": "0 2 3 2 4 3 2 - 0 2 3 5 4 3 - -",
  "weight": 2,
  "when": "personality(peppy)"
}
```

- **scale** — `major`, `minor` or `lydian`. Notes are steps on this scale.
- **notes** — scale steps from the humming pony's own pitch, one per beat, separated by spaces.
  `0` is the pony's root note. In `major` and `minor` (five notes) `5` is the root an octave up and
  `-1` the step below the root. In `lydian` (six notes) `6` is the octave. `-` is a rest. Up to 64 notes.
- **tempo** — game ticks per note: `4` is brisk, `7` slow.
- **weight** — how often it's picked against the other tunes that fit. Make it big (like `50`) to all
  but take over when it fits, e.g. on a holiday.
- **when** *(optional)* — conditions in the same grammar as `.tale` files, all of which must hold:
  `time_of_day(morning)`, `night`, `personality(peppy)`, `holiday(cim:nightmare_night)`,
  `not profession(none)`, … (see `docs/narrative-reference.md`).

Every note is played in the pony's own speaking voice, and the whole tune is moved up or down to fit
the game's two-octave pitch range, so it keeps its shape. Pentatonic scales can't play every note of
a song; pick the nearest step, and the tune will still be recognizable.
