# Unicorn magic: the visual profile

How unicorn magic looks, for every magic effect in the mod: the profile first, then the show and
movie footage it was built from (taken apart frame by frame, as in docs/princess-manes.md), then
how the mod's current levitation aura compares and the fixes it needs.

## The profile - how magic behaves on screen

Drawn together from every reference below (show and movie, magenta and cyan). This is what the
mod's magic visuals follow - **every unicorn magic effect, and nearly every Verse spell**, whose
verbs all draw in this style by default (see Every verse looks like magic, in the README) - and
the references are the evidence.

### The idea behind it

A unicorn doesn't make magic from nothing: the horn is a **lightning rod**, gathering the magic in
the air around it, holding it, directing it into what it touches, and casting it off when it's done
(the user's framing, and exactly what the footage shows). Every visual is one stage of that.

### 1. Magic is colored light, never paint

- **Translucent, always:** whatever magic touches stays visible inside it, tinted. Nothing is ever
  covered by a solid fill.
- **One color per caster** - their own, which can differ from their mane (Luna's is cyan; her mane
  is royal blue) and often sits near the eyes.
- **Everything else comes from that one color:**
  - the **rim** - the color at full strength, the most saturated part of any glow, a touch deeper
    at its outer edge (and running toward white only at a charge's peak);
  - the **tint** over a touched object or the horn - a pale wash of it (about a quarter strength:
    white paper under Twilight's magenta reads light pink);
  - the **haze** - the color fading out to nothing just beyond the rim;
  - **white** for the hottest parts - the wisps, the glints, the flash at a release, and the rim
    at its very brightest.

### 2. Three layers, everywhere

The same structure on a horn and on anything held: **the thing itself, tinted** -> **a saturated
rim** hugging its outline a short way outside, corners rounded (about a sixth of a small object's
width) -> **a soft haze** fading out. Each object gets its own; touching objects show both rims.

**The aura is attached to what it covers** (noted by the user, 2026-10-24): the horn and every
levitated object stay **wrapped** in their aura the whole time, as though it's part of them - it
covers them, moves and turns with them, and is never a glow floating behind them or left behind
in the air. (Only its loose outer parts - the flame, the smear, the droplets - trail and lag; the
covering itself stays put on the object.) In 3D this means a **shell around the object**, drawn
over it from every angle, rather than a flat glow behind it.

### 3. The life of a spell

1. **Gathering (lighting up):** a faint, wide, pale haze appears round the horn - barely there -
   then thickens, takes on the color, and **draws in and brightens** into the tight three-layer
   glow. The magic visibly collects at the rod.
2. **Holding:** the horn glows from inside; a translucent **flame** wraps it, tallest above the
   tip; **white wisps circulate** round the horn's spiral; the occasional **glint** jumps across it.
   Whatever is held wears its own three-layer aura.
3. **Releasing (ending or letting go):** a **white flash** at the horn (about 0.1 s in); the glow
   **breaks into fifteen to twenty shards** - the color with white cores, stretched along their
   flight - flung **upward and outward**; the horn is back to normal by about 0.3 s; the shards
   rise, spread, shrink and are **gone by about 0.8 s**. Light and graceful: the magic is cast
   aside, not blown up.

### 3a. Teleporting

The biggest spell in the footage, and still the same magic - the caster's color, translucent,
white at its hottest.

1. **Charged:** the horn is already gathered to a bright, near-white glow.
2. **The burst (about 0.1 s):** jagged spikes shoot out from the horn, then the magic swells into a
   **bubble** that swallows the caster - translucent, the color throughout, with the caster dimly
   visible inside, swirls in it, sparkles, and long **jagged rays** like lightning stabbing out
   well beyond it.
3. **Gone (a frame or two):** the caster vanishes; the spikes withdraw, leaving a smooth bubble
   with a lighter rim.
4. **The collapse (about 0.1 s):** the bubble falls in on itself - curving swirl streaks and lines
   converging to a point, which flares as a white star.
5. **Afterglow:** a few thin lines and glints, gone within about 0.2 s.

**Arriving** is smaller and almost instant: the caster simply **pops in** with a flash and short
burst lines, and white glints twinkle round them for about 0.3 s. It happens while the departure's
bubble is still collapsing - arrival doesn't wait for departure to finish.

**What's held comes along:** anything being levitated teleports with the caster, its aura intact.

**Again and again:** teleports can chain about 0.65 s apart, each with its own burst and
collapse; the horn stays charged in between.

### 4. How it moves

- **Edges wave:** the horn flame and every aura's edge are always moving - a **smooth, slow wave**
  rolling along the outline, like Celestia's mane (corrected by the user, 2026-10-24: the GIFs'
  low frame rates made it look like a stepped flicker; it isn't one).
- **It trails motion:** anything moving - a held object, a running caster's horn - stretches its
  rim and haze out behind it like a short flame.
- **It has weight:** magic behaves like a flame or a liquid, not a sticker. It **lags** behind
  sudden movement, **overshoots** and billows when the movement stops, then settles; fast moves
  **fling droplets** off in their direction, and it can **drip** from the horn's tip as it calms.
- **A swing makes a crescent:** a fast sweep of the horn - parrying or swatting a spell aside -
  stretches the glow into a long curved crescent along the path (three or four horn-lengths),
  ending in a hooked, curling tip; afterward the leftover glow can curl into a thin ring round the
  horn and fade. A swing takes about 0.25 s, and the glow is settled about 0.5 s later.
- **Calm by default:** outside those bursts nothing whips or strobes; even a release is a soft
  scatter.

### 5. Sparkles

Small **four-point stars**, white, scattered on and near the magic, each lit for only one or two
animated frames (about 80-160 ms) before another appears elsewhere. Brief glints, never steady
glitter. The exception is **sparkle dust** thrown off by a sudden move - a swing, a release - which
drifts slowly and lingers for about 0.6 s. (Luna's mane stars aren't magic sparkles - they belong
to her hair; see docs/princess-manes.md.)

### 6. Show and movie

The show draws it flatter and crisper; the movie softens it with more blur and adds the white
wisps and the release's shards. The mod goes **straight for show-accurate smoothness** - smooth
rims and haze with no pixel-art styling (decided by the user, 2026-10-24, after seeing a pixel
look in-game: "it looks bad") - plus the movie's wisps, shards and flash for life.

### 7. Comfort and cost

- **A reduced-effects setting:** releases and teleports flash white, which can bother
  photosensitive players, so a client option softens the flashes (and stills the edge wave), the
  same magic otherwise.
- **Cheap at every scale:** effects are drawn on each viewer's own game, simplified with distance
  (beyond a short range: the tinted shell and rim only, no flame, wisps or sparkles), and capped
  in number - weighed for singleplayer, small and large servers alike, where a crowd of unicorns
  can be casting at once.

### 8. Numbers to start from

| | Value |
| --- | --- |
| Tint over objects and the horn | about 25% of the magic color |
| Rim width | about 1/6 of a small object's width; a short way outside its outline |
| Haze | fading to nothing over about two-thirds of the rim's width |
| Horn flame | about a horn's width out on every side, tallest above the tip |
| Edge wave | smooth and slow, rolling along the outline (like Celestia's mane) |
| Sparkle life | 80-160 ms; sparkle dust from a sudden move ~0.6 s |
| A horn swing | ~0.25 s to the crescent (3-4 horn-lengths long), settled ~0.5 s later |
| Release | flash at ~0.1 s, horn normal by ~0.3 s, shards gone by ~0.8 s, 15-20 shards |
| Lighting up | order known (faint and wide -> tight and bright); timing still to measure |
| Teleport | burst ~0.1 s, bubble a frame or two, collapse ~0.1 s, afterglow ~0.2 s (all ~0.35 s); arrival a flash plus ~0.3 s of glints; chains ~0.65 s apart |

## Reference 1 - Twilight levitating note cards (2026-10-24)

**Source:** 418 x 498, 15 frames at 50 ms (0.75 s), not a loop. A ring of note cards circles
Twilight while she holds them, her horn lit. Twilight's magic is magenta-pink.

### The aura around an object

- **One aura per object.** Each card has its own aura hugging *its own* outline - not one shared
  blob round the whole group. Where two cards touch, both rims show.
- **It follows the outline closely,** a short way outside it, with the corners rounded off.
- **Three parts, from the inside out** (measured across a card's edge, at about 96 px wide):
  1. **The object, tinted:** the card stays fully visible, washed toward a pale tint of the aura
     color - white paper reads as `#e5c7db`, a light pink. Not covered by an opaque fill.
  2. **The rim - the strongest part:** a band of **saturated** aura color hugging the outline,
     `#d87aa9` on its inner side deepening to `#c86293` at its outer edge. About 12-20 px wide
     here, **roughly a sixth of the card's width**.
  3. **A soft outer haze:** fading from the rim through `#d98cb4` and `#e1b4ce` into the
     background over about 10 px.
- **It smears with motion:** the rim is wider on the side trailing the card's movement (about
  20 px against 12 on the leading side), like a short motion trail.
- **Sparkles:** small white four-point glints on and near the cards, each gone within a frame or
  two.

### The horn glow

- **The horn is lit, not hidden:** it stays visible with its outline, tinted pale lilac-pink
  (`#c98aca`) - glowing from inside.
- **A flame-shaped glow around it:** a translucent magenta blob (`#d76ea0` over a light
  background) wrapping the whole horn, about a horn's width out on every side and **tallest above
  the tip**, rising like a flame. Its edge is soft, lumpy, and **changes shape every frame** - a
  flicker. Things behind it (her mane) show through, tinted.
- **A glint:** a single small four-point sparkle on the horn, jumping to a new spot each frame.

## Reference 2 - Twilight letting a spell go (2026-10-24)

**Source:** *My Little Pony: The Movie* (2017) style, 640 x 499, 84 frames at 40-50 ms (3.5 s),
animated on twos. Frames 0-24 hold the lit horn, then a camera cut; frames 25-42 (about 0.8 s)
are the release - the ambient magic she'd gathered, with her horn as the lightning rod, cast
aside once it's no longer needed. The rest is her walking off.

### Holding a spell (movie style)

- The horn glows bright pink-white from inside, its spiral still visible.
- A soft, translucent **haze rises round it like a flame**, as in Reference 1, its edge shifting
  each frame.
- New here: **white energy wisps travel round the horn's spiral** - bright streaks that move every
  frame, spilling a little onto the mane at its base - so the gathered magic visibly **circulates**
  while it's held.
- A few faint motes drift in the air nearby.

### Letting it go - the release

- **A flash (about 0.1 s in):** the horn flares white at its core for two or three frames.
- **A burst of shards:** the gathered glow **breaks into fifteen to twenty small petal- or
  feather-shaped streaks** - magenta with white cores, each stretched along its direction of
  travel - that fan **upward and outward** from the horn, as if flung off it.
- **The horn settles** back to its normal color within about 0.3 s of the release starting.
- **The shards drift, shrink and fade:** they keep rising and spreading, dimming to tiny specks,
  and are gone about 0.7-0.8 s after the release began.
- No sound or shake is implied - it's light and graceful, a shrug of magic, not an explosion.

## Reference 3 - Luna gathering magic (2026-10-24)

**Source:** nine stills from *My Little Pony: The Movie* (2017), in order, of Luna channelling
magic before casting, mostly while running. Stills, so no timings - only the order.

**Her color isn't her mane's:** Luna's magic is a bright **cyan-blue**, measured `#6cccfc` rising to
`#6cfcfc` at its brightest - nothing like her royal-blue mane, and closest to her teal eyes. A
second color confirming the README's rule (an aura often matches the eyes or mane, never
necessarily the mane), and a check that the layering works away from Twilight's magenta.

**Lighting up, step by step:**
1. **Nothing** - a plain horn.
2. **A faint, wide haze:** a pale, almost grey-teal mist around the horn, very see-through and well
   out from it - more a hint than a glow.
3. **The haze thickens and takes color:** more opaque, light blue, still wide and soft.
4. **It brightens and draws in:** the color saturates, and the glow pulls in toward the horn.
5. **A defined, bright rim:** at full charge it's the same three layers as Reference 1, on the
   horn - the horn **visible inside, tinted** blue; a **saturated, bright cyan rim** hugging it;
   a soft haze outside - with **white wisps** streaking along the horn inside (as in Reference 2).
6. **At its peak** the rim runs brightest, cyan going toward white.

So charging up runs **faint, wide and pale -> tight, bright and saturated**: the glow gathers in
as it gathers strength.

**It smears with motion:** while she runs, the horn glow stretches out behind the horn into a
flame-like trail - the same smear as the cards in Reference 1.

## Reference 4 - Twilight swatting aside a magic attack (2026-10-24)

**Source:** a show-style clip cut out onto a transparent background, 434 x 321, 41 frames at 40 ms
(1.6 s). The cut-out's transparency is rough, so the glow's exact opacity can't be trusted here -
its shapes and timing can. Twilight blocks another pony's spell by **swinging her head**, horn lit,
twice (frames 2-8 and 21-25).

- **The swing leaves a crescent:** as the horn sweeps, its glow stretches into a long, curved
  **crescent or scythe-shaped smear** along the horn's path - three or four horn-lengths long -
  tapering to a **hooked, curling tip** at its trailing end. Pale inside, saturated pink at its
  edges: the same rim-and-tint layering, stretched along the motion.
- **The glow has weight:** it lags behind the horn during the swing, then **overshoots** and
  billows up into a big flame over the horn as the head stops (frames 6-7), before settling. Like a
  liquid or a flame, not a sticker.
- **It flings droplets:** small pink drops and streaks fly off the end of the swing in its
  direction (frames 5-7), and a few drip from the tip as it settles (frames 29-31).
- **It unwinds as it calms:** after the first swing the leftover glow curls into a **thin ring or
  loop** round the horn (frames 8-11) and fades, before the ordinary flame returns.
- **Sparkle dust lingers:** white specks scattered by the swing drift slowly and stay for about
  0.6 s (frames 5-20) - longer than the glints on a held object.
- **A swing takes about a quarter of a second** (six or seven frames) from start to crescent; the
  glow is settled again roughly half a second later.

## Reference 5 - Twilight teleporting while levitating (2026-10-24)

**Source:** show clip, 566 x 498, 25 frames at 40 ms (1 s). Twilight holds a scroll and quill in
her aura (frames 0-14), then teleports away (frames 15-22).

- **Frame 15:** a small bright burst forms at her, lines shooting off it.
- **Frames 16-18:** it swells fast into a translucent magenta bubble swallowing her, jagged spikes
  and lightning-like rays stabbing far out of it, sparkles inside; she's still faintly visible,
  tinted, within it.
- **Frame 19:** she's gone; a smooth bubble remains, lighter at its rim, sparkles in it.
- **Frame 20:** it collapses - curving swirl streaks, a spiral, lines converging on a point that
  flares as a white star.
- **Frames 21-22:** thin streaks and a white glint with a small ring round it, fading.
- **Frames 23-24:** an empty room with a last glint or two. The scroll and quill went with her.

## Reference 6 - Twilight teleporting again and again (2026-10-24)

**Source:** *The Crystal Empire* (season 3), 320 x 180, 219 frames at 30 ms (6.6 s). Twilight
teleports four times in a row across a park (bursts at frames 38-42, 59-63, 81-85, 103-107 -
about 0.65 s apart), then sits.

- **Charged in between:** her horn holds a big, bright, near-white glow the whole time, rays of
  light streaming from it.
- **Each departure starts at the horn:** spikes shoot from it first, then the bubble swells round
  her, as in Reference 5.
- **She arrives while the bubble is still there:** at the next spot she's already standing - with
  a flash, burst lines and twinkling glints round her for about 0.3 s - while the old bubble
  collapses behind. (She lands in a pond once, with a splash: arrival is physical.)

## What the mod draws today, against the reference

| | The show | The mod now |
| --- | --- | --- |
| Color | the caster's own aura color | always magenta, for everyone |
| How many auras | one per object | one shared blob round the whole group |
| Shape | hugs the object's outline | a simplified outline: no dents, at most 10 corners |
| The object inside | fully visible, tinted pale | covered by a nearly opaque magenta fill (85%) |
| Rim | the most **saturated**, deepest part | **pale** white-pink - the reverse |
| Outer edge | a soft haze fading out | a sharp cut-off after the rim |
| Motion | smears behind moving objects | rim and haze trail behind held things and horns, on a spring |
| Horn | lit, with a waving flame-shaped glow | nothing |
| While held | wisps circulating round the horn | white wisps up the horn's spiral, and glints jumping across it (within 24 blocks) |
| Letting go | a flash, then a fan of shards flung upward that fade in under a second | a white flash and star at the tip, 15-20 shards in the caster's color flung up and out, and sparkle dust |
| Lighting up | a faint, wide, pale haze that gathers into a tight, bright rim | nothing |
| Sudden moves | lags, overshoots, flings droplets; a swing leaves a crescent | lags, overshoots and flings droplets; a fast horn swing leaves a solid crescent along the tip's path that tapers and draws back in (no curled hook or ring yet) |
| Teleport | a spiky bubble that collapses to a white star; arrival pops in with glints | vanilla's purple Enderman portal particles at both ends |
| Sparkles | brief four-point glints | twinkling stars (keep, check timing) |

## Fixes, in order

1. **The caster's own color**, with the rest derived from it: the rim is the color itself (a
   little deeper at its outer edge), the tint over the object a pale wash of it, the haze a
   lighter fade of it.
2. **Flip the layering:** a light translucent tint over the object, the saturated rim outside it,
   then the soft haze - instead of an opaque fill with a pale rim.
3. **One aura per object, attached to it:** a shell wrapping each held block or item, following
   its real outline (dents and all) and moving with it - drawn by rendering the object again,
   tinted, plus a slightly enlarged copy for the rim and haze (the same idea as the manes'
   outline) - instead of today's flat cut-out behind the group. The horn gets the same shell.
4. **The horn glow:** tint the horn, wrap it in a translucent flame shape that rises above the tip
   and waves, with an occasional glint. Shown whenever a unicorn is casting or holding a spell.
   While a spell is held, add wisps circulating round the horn's spiral. When a spell starts,
   charge it up: a faint, wide, pale haze that thickens, takes on the color, and draws in to the
   tight, bright rim - the same three layers as an object's aura, on the horn.
5. **The release:** when a spell ends or is let go, a quick white flash at the horn, then a fan of
   fifteen to twenty stretched shards in the caster's color flung upward and outward, rising,
   shrinking and fading out within about 0.8 s (Quasar particles, with velocity-stretched
   sprites).
6. **Motion smear and weight:** stretch the rim and haze behind a moving object; give the horn
   glow lag and overshoot (a small spring on its shape) and fling droplets on fast moves; and draw
   a crescent along a fast horn swing - ready for a parry or deflect move when unicorn combat has
   one.
7. **Retune** the sparkles' size and lifetime against these references.
8. **Teleport:** replace the vanilla portal particles with the profile's departure (spikes from
   the horn, the bubble, its collapse to a white star) and arrival (a flash and glints), arrival
   starting at once rather than after the departure ends; held objects keep their auras across
   the jump.

## Still wanted

- The horn lighting up as a clip, to time the charge-up (Reference 3 gives only the order).
- The aura appearing as something is grabbed, and fading as it's let go.
- A third color, ideally a pale or warm one (Rarity's, Trixie's, Starlight's), to check the color
  rules hold beyond magenta and cyan.
- Something big or oddly shaped being lifted, and a pony being lifted.
