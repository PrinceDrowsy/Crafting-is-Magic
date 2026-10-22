# Building guide: houses, rooms, and the Schoolhouse

How to build the mod's buildings in-game so they plug straight into villages. You design; the mod
does the rest (README: Village identity — house exteriors and interiors generate separately).

Everything here is in the creative inventory: **room markers** and the **keepsake spot** are in the
*Operator Utilities* tab (next to structure blocks); the **chalkboard** is in *Functional Blocks*.
You'll need **structure blocks** (`/give @s structure_block`) and **jigsaw blocks** (`/give @s jigsaw`).

## The two kinds of build

1. **House shells** — walls, roof, windows, doors, floors, and **empty rooms**. Each empty room
   gets one **room marker** of its type. Shells generate as part of villages.
2. **Room sets** — a furnished room on its own (bed, shelves, rug, lamp...). When ponies move in,
   each marked room is furnished with a set that suits them.

A house's look comes from its shell; its *life* comes from who moves in.

## What every house needs

- **A bed per resident, in the shell itself.** Ponies move in by claiming a bed - a house with no
  bed never gets anyone, so it never gets furnished. Beds go in the shell, never only in a room set.
  (Room sets furnish *around* the bed; they only ever fill empty space, so they can't cover it.)
- **At least one bedroom marker** (in the room with the beds).
- **One shared room** - a living room *or* a kitchen; a small cottage can be one room that's both.

By house size:

| House | Rooms |
|---|---|
| Small cottage (1 pony) | bedroom + one shared room (living room or kitchen) |
| Family home (2-4) | bedroom(s) for the adults, a nursery, a kitchen, a living room |
| Work home | add a workroom (a studio, a workshop) - its sets can suit the resident's profession |

**Walls get painted for you:** when ponies move in, every wall face in each room gets a flat layer of
paint (a concrete color that suits the resident, or the one a room set names with `"walls"`). It
has no thickness, so build rooms at the size you want them; windows and doorways are never painted.
Paint follows the walls up to the roof (never onto it, or onto glass - see the `cim:unpaintable` block
tag), and beds carry the paint for the wall behind them.

Leave **job-site blocks** (a lectern, a smoker, the chalkboard) out of houses and room sets unless
you mean the house to come with that job.

## Building a house shell

1. Build the house. Leave the rooms empty, and **separate rooms with walls or doors** — the mod
   finds a room's size by filling its air from the marker, so an open archway lets it spill into
   the next room.
2. In each room, place **one room marker** on the floor: *Bedroom*, *Kitchen*, *Living Room*,
   *Workroom*, or *Nursery* (a foal's room — furnished as such only when there are foals).
   Markers are invisible unless you're holding one.
3. **The doorway jigsaw** connects the house to the village street. Put a jigsaw block in the
   ground, just outside the front door, **facing out toward the street** (its arrow points away
   from the house), with:
   - Name: `minecraft:building_entrance`
   - Target name: `minecraft:building_entrance`
   - Target pool: `minecraft:empty`
   - Turns into: the block that should be there (a path, a step, `minecraft:air`...)
4. **Villager spawn jigsaws** (optional — vanilla uses 1–2 per house): a jigsaw on the floor
   inside, **facing up**, with:
   - Name: `minecraft:bottom` · Target name: `minecraft:bottom`
   - Target pool: `minecraft:village/plains/villagers` (use the biome you're building for:
     `plains`, `desert`, `savanna`, `snowy`, `taiga`)
   - Turns into: the floor block
5. **Save it** with a structure block in *Save* mode. Name it like
   `cim:village/plains/houses/cottage_1`, size it to fit the house exactly, and press **Save**.

## Building a room set

1. Build the furnished room **on its own**, the size of the rooms it's meant for (it must fit
   inside the room; it can be turned to fit).
2. Air in a room set is ignored when placed, so it never carves into walls — build just the
   furniture and decor.
3. Put a few **keepsake spots** where a little item would look at home: a shelf, a table, a
   windowsill. Keepsakes appear there over time for the player's friends.
4. Save it with a structure block, named like `cim:rooms/bedroom/bookish_reading_nook`.

## Getting saved builds into the mod

Saved structures land in your test world's folder:
`cim-1.21.1/run/saves/<world>/generated/cim/structures/...`

Copy each `.nbt` to the same path under `cim-1.21.1/src/main/resources/data/cim/structure/...`
(e.g. `.../structure/village/plains/houses/cottage_1.nbt`). Then add a small JSON file — or ask
me to, and I'll write them.

**A house shell** joins a village pool —
`src/main/resources/data/cim/village_additions/plains_cottage_1.json`:

```json
{
  "pool": "minecraft:village/plains/houses",
  "structure": "cim:village/plains/houses/cottage_1",
  "weight": 4,
  "processors": "minecraft:mossify_10_percent"
}
```

`weight` is how common it is (vanilla houses are 1–10). `processors` is optional.

**A room set** says who it suits —
`src/main/resources/data/cim/room_sets/bookish_reading_nook.json`:

```json
{
  "structure": "cim:rooms/bedroom/bookish_reading_nook",
  "room": "bedroom",
  "personalities": ["bookish"],
  "weight": 1
}
```

Optional filters (leave them out for a set that suits anyone): `personalities` (`peppy`,
`steadfast`, `bookish`, `daring`, `gentle`, `refined`, `cranky`, `lazy`), `professions`
(`minecraft:librarian`, `cim:teacher`...), `quirks` (`collector`, `sweet_tooth`, `gossip`...),
`species` (`earth_pony`, `unicorn`, `pegasus`, `bat_pony`, `changeling`, `kirin`...), and `foals`
(`true`: only homes with foals). A specific match is picked over a plain set; every room type
needs **at least one plain set**, so no room ever stays bare.

## The Schoolhouse

Build it like a house shell, with a **chalkboard** inside — that's the Teacher's job site (a
pony nearby will take the job). Rooms in it can use markers too (a *Workroom* for the classroom).
Save it as `cim:village/plains/houses/schoolhouse` and add it to the pool the same way, with a
low weight (it should be rare — about one per village).

## Testing

- `/place template cim:village/plains/houses/cottage_1` places a build where you stand.
- `/cim debug home furnish <pony>` furnishes a pony's home right now, as if they'd just moved in.
- New villages only include new buildings in **chunks that haven't generated yet** — fly somewhere
  new, or make a new world.
