# The princesses' manes

Reference for Celestia's and Luna's flowing manes and tails, for when they're built (with Veil:
shaders, Quasar particles, lights). Taken apart from reference GIFs the user supplied, 2026-10-24.

## Celestia

**Source:** a looping GIF of Celestia standing in a starry, misty landscape - 1278 x 720, 40 frames,
80 ms each (12.5 fps), a **3.2 second loop**. It loops cleanly: the last frame steps to the first
as smoothly as any other step.

**What moves:** only the mane, the tail, and the sparkles. A map of every pixel that changes across
the loop shows the body, crown, regalia and background perfectly still - so the effect is the hair
alone, laid over an ordinary pony.

**Shape:** the mane and tail are **ribbons**, not strands - broad, flat sheets that flow in the air:
- The **mane** sweeps back and down from the head and crest, ending in a curled tip behind her.
- The **tail** streams out behind her to the right, falls to the ground in loose S-bends, and its
  tip curls forward along the ground under her.
- Each ribbon has a **thin darker outline** along its edges (a deep teal), as in the show's style.

**Colors - four soft bands running the length of each ribbon** (approximate, read off the frames):

| Band | Color |
| --- | --- |
| Mint green | `#9ce4cc` |
| Cerulean | `#54b4cc` |
| Cornflower blue | `#849ce4` |
| Pink-lavender | `#e4b4fc` (deeper `#e49ce4` in shadow) |

A pale `#b4e4fc` shows where the ribbon thins at the tips. The bands blend softly into each other
rather than meeting at hard lines, and they **stay fixed to the ribbon** - they bend with it, they
don't scroll along it.

**The motion - a slow travelling wave:**
- A wave runs **from the root to the tip** of each ribbon, like a banner in a gentle wind; about one
  wavelength passes per loop (3.2 s).
- It's **small at the root and grows toward the tip**: the hair at the head barely moves, and the
  free ends swing and curl the most.
- The tips **curl and uncurl** as the wave reaches them.
- It's slow and calm - nothing whips or snaps.

**Sparkles:** small **four-point star twinkles** scattered inside the mane and tail. A few hundred
pixels' worth newly light up every frame, and each lasts only **1-4 frames (80-320 ms)** - quick
glints, appearing and fading all over the hair at random, never a steady glitter. Larger four-point
star glints flash on the ground nearby too (part of this scene's backdrop, but a nice touch for an
aura). There's **no strong glow or bloom** on the hair itself; the sparkles carry the magic.

## Luna

**Source:** two show clips the user supplied, neither a clean loop (Luna moves and blinks in both,
and the camera drifts in one): a Nightmare Night shot, 498 x 281, 37 frames at 40 ms (1.5 s, the
show's animation on twos, so most frames repeat); and a profile shot under a night sky, 498 x 280,
15 frames at 100 ms (1.5 s).

**Shape - two layers, not one:**
- An **inner core**: a broad ribbon of deep **royal blue** (`#0838a8`-`#1838a8`), nearly opaque,
  edged with a thin lighter periwinkle line.
- An **outer veil** around it: a wider, **translucent** ribbon of violet-periwinkle (`#5838b8`,
  blending toward the purple sky behind it) that reaches past the core's edge. The world **shows
  through** it - a dark tree is plainly visible through the veil in the profile shot.
- Both flow back and up behind her in big soft waves, and the veil's edge billows a little out of
  step with the core's, which is what makes it read as smoke or night mist rather than a solid
  shape.

**Stars inside the hair:** tiny, faint pale points scattered sparsely through the core, like a night
sky - a starfield *in* the mane, not glints *on* it. They mostly **hold their place in the hair**
and travel with it as it flows (most are still lit, within a pixel, the next frame), with a share
fading in and out - a gentle twinkle, far softer and slower than Celestia's sparkles.

**In the movie's style** (stills of Luna running, *My Little Pony: The Movie*, 2017): the same two
layers, more crisply drawn - the veil a clear lavender-violet with a **thin lighter lavender line
along its outer edge** - and the stars include **larger four-point sparkles with long cross
flares** among the small points, a few of them per ribbon. While she runs, both ribbons stream
straight back behind her, the veil trailing a little further than the core.

**Pace:** the same calm, slow travelling wave as Celestia's - nothing whips. Neither clip loops, so
the exact period isn't measurable; Celestia's 3.2 s is a sound starting point for both.

**Celestia and Luna side by side:** the same ribbon motion, opposite characters - Celestia's is
four bright opaque bands with quick bright glints; Luna's is a dark core in a see-through veil
with a slow, quiet starfield inside.

## Making it work in 3D (decided 2026-10-24)

The GIFs are flat 2D drawings seen from the side; the manes have to hold up from every angle, on a
blocky Minecraft pony.

- **Style - show-accurate and smooth:** smooth, flowing ribbon shapes with smooth color bands,
  edges and stars, exactly as the show draws them - no pixel-art texture (decided by the user,
  2026-10-24, replacing an earlier "middle ground" of a pixel-art texture, after a pixel look on
  the magic aura looked bad in-game; matches the magic profile, docs/magic-aura.md).
- **Never paper-thin:** the ribbon's spine moves freely in 3D, but its width **partly turns toward
  the camera** (about halfway between facing the camera and the hair's own orientation), and the
  ribbon has a little thickness - so edge-on it reads as a soft lock, not a line, and walking
  round it never looks like a cardboard cutout.
- **Toon-shaded:** the bands are drawn flat (at most a soft two-tone shade), never lit like
  plastic, so they read the same from every side.
- **Outlines from an inverted hull:** a slightly larger copy of the ribbon drawn in the edge color,
  showing only where it peeks out behind - the usual cel-shading outline, which holds from every
  angle (lines drawn on the texture vanish edge-on).
- **Follows her movement:** the spine is a short chain that trails behind, swings when she turns
  and settles when she stops; the GIFs' slow travelling wave is layered on top as a steady breeze.
  It's magic hair, so no gravity - Celestia's tail floats in its curl just above the ground.
- **No clipping:** the roots sit just off the body, and the chain has simple collision with the
  neck, back and wings, so the hair slides round them.
- **Luna's veil drawn in order:** the core drawn opaque first, then the veil as a single
  translucent layer, so there's no translucent-on-translucent sorting.
- **Shaped for every view:** the hair sweeps back *and* a little outward, checked from front, back
  and top, not just the side the GIFs show.
- **Cheap:** the wave runs on the GPU; only the chain ticks, and only for princesses nearby -
  beyond that, the plain wave with no chain.

## Building it with Veil (proposed)

- **A ribbon mesh:** each mane and tail is a strip of quads attached to the head/crest or the dock
  of the tail. A vertex shader moves it: offset = `sin(k * u - w * t) * amplitude(u)`, where `u`
  runs 0 at the root to 1 at the tip, `amplitude(u)` grows toward the tip, and `w` gives a 3.2 s
  period. Curls come from a second, smaller wave near the tip.
- **Bands from the texture coordinate across the ribbon:** the fragment shader maps `v` (0-1
  across) to the four band colors with soft blends, and darkens the last bit at each edge for the
  outline - so the colors live on the ribbon and bend with it, as in the reference.
- **Luna's version:** two ribbons with the same wave - the royal-blue core, and a wider,
  translucent violet veil drawn around it with a slight phase offset so its edge billows out of
  step. The core samples a sparse star texture in ribbon space (so the stars travel with the hair),
  with a slow per-star fade for the twinkle.
- **Sparkles:** a Quasar emitter spawning short-lived four-point star particles on the ribbon's
  surface - 80-320 ms lifetimes, random positions, a quick fade in and out. Short emitter lifetimes
  that get recreated, per the known emitter-removal quirk (see the Levitation aura effect).
- **Light:** Celestia might also carry a soft warm point light (she raises the sun) - optional, and
  only if it reads well; the reference itself doesn't glow.
- **Performance:** one ribbon per mane and tail, the wave done on the GPU, a capped number of
  sparkles, and everything skipped beyond a sensible view distance - weighed for singleplayer, small
  and large servers alike (several princesses and alicorn players on screen at once).
