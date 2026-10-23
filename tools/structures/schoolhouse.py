"""Generates the Ponyville schoolhouse shell (docs/building-guide.md) as a structure file, for the user to
load with /place template and alter to their liking.

Style, from the show: an old-fashioned one-room schoolhouse - red walls (stripped mangrove) with cream
trim (stripped birch), a steep shingled roof (mangrove stairs) edged in birch, tall framed windows with a
pink (cherry) heart over each, a pillared front porch, a bell tower on the ridge with a real bell, and a
tall flagpole out front with a red banner, so a new player spots it from afar. Inside: the chalkboard
(the Teacher's job site), the teacher's desk, and rows of desks. Outside: the path, a fence, the sign,
and a swing.

Front faces north (-z). The ground is y0; the floor is raised one step, at y1.

    python schoolhouse.py <output folder>
"""
import os
import sys

from nbt_structure import Structure

WALL = "minecraft:stripped_mangrove_wood"
TRIM = "minecraft:stripped_birch_log"
TRIM_WOOD = "minecraft:stripped_birch_wood"
ROOF = "minecraft:mangrove"            # _stairs, _slab, _planks
EDGE = "minecraft:birch"               # the cream roof edging
HEART = "minecraft:cherry_planks"
FLOOR = "minecraft:oak_planks"
GLASS = "minecraft:glass_pane"
DESK = "minecraft:spruce"

X0, X1 = 0, 10       # outer walls (x)
Z0, Z1 = 4, 20       # outer walls (z): front, back
F = 1                # floor level
TOP = F + 7          # top of the walls
BAND = TOP + 1       # the cream band under the eaves
MID = (X0 + X1) // 2


def stairs(s, x, y, z, wood, facing, half="bottom", shape="straight"):
    s.put(x, y, z, wood + "_stairs", facing=facing, half=half, shape=shape, waterlogged="false")


def slab(s, x, y, z, wood, kind="bottom"):
    s.put(x, y, z, wood + "_slab", type=kind, waterlogged="false")


def roof_y(x):
    """The roof stair's height over column x: one up per step in from the eaves, the ridge at MID."""
    return BAND + (min(x - X0, X1 - x) + 1)


def heart(s, put):
    """A little heart, point down: put(row, offset) for rows 0 (the point), 1 (full), 2 (the lobes)."""
    put(0, 0)
    for o in (-1, 0, 1):
        put(1, o)
    put(2, -1)
    put(2, 1)


def build():
    s = Structure()

    # ---- Foundation, floor, walls ----
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            edge = x in (X0, X1) or z in (Z0, Z1)
            s.put(x, 0, z, TRIM_WOOD if edge else FLOOR)
            if not edge:
                s.put(x, F, z, FLOOR)
                continue
            corner = x in (X0, X1) and z in (Z0, Z1)
            axis = "x" if z in (Z0, Z1) else "z"
            s.put(x, F, z, TRIM_WOOD if corner else TRIM, **({} if corner else {"axis": axis}))
            for y in range(F + 1, TOP + 1):
                s.put(x, y, z, TRIM if corner else WALL, axis="y")
            s.put(x, BAND, z, TRIM_WOOD if corner else TRIM, **({} if corner else {"axis": axis}))

    def window(x, z, axis):
        """A tall framed window, three panes high, with a heart over it. axis: the wall's direction."""
        along = (1, 0) if axis == "x" else (0, 1)
        s.put(x, F + 1, z, TRIM, axis=axis)                         # the sill
        for y in range(F + 2, F + 5):
            s.put(x, y, z, GLASS)
            for side in (-1, 1):
                s.put(x + along[0] * side, y, z + along[1] * side, TRIM, axis="y")
        heart(s, lambda row, o: s.put(x + along[0] * o, F + 5 + row, z + along[1] * o, HEART))

    for z in (8, 12, 16):
        window(X0, z, "z")
        window(X1, z, "z")
    for x in (2, 8):
        window(x, Z0, "x")
        window(x, Z1, "x")

    # The front door, framed in birch.
    for y, half in ((F + 1, "lower"), (F + 2, "upper")):
        s.put(MID, y, Z0, "minecraft:cherry_door", facing="south", half=half, hinge="left", open="false", powered="false")
    s.put(MID, F + 3, Z0, TRIM, axis="x")
    for side in (-1, 1):
        for y in range(F + 1, F + 4):
            s.put(MID + side, y, Z0, TRIM, axis="y")

    # ---- The roof ----
    for x in range(X0 - 1, X1 + 2):
        y = roof_y(x)
        for z in range(Z0 - 1, Z1 + 2):
            wood = EDGE if z in (Z0 - 1, Z1 + 1) else ROOF       # birch along the gable edges
            if x == MID:
                slab(s, x, y, z, wood)
            else:
                stairs(s, x, y, z, wood, "east" if x < MID else "west")
            if Z0 <= z <= Z1 and X0 <= x <= X1 and s.name_at(x, y - 1, z) is None:
                s.put(x, y - 1, z, ROOF + "_planks")             # the roof's underside
    for z in range(Z0 - 1, Z1 + 2):
        wood = EDGE if z in (Z0 - 1, Z1 + 1) else ROOF
        slab(s, X0 - 2, roof_y(X0 - 1), z, wood)                 # the eaves flare out
        slab(s, X1 + 2, roof_y(X1 + 1), z, wood)
        if Z0 <= z <= Z1:                                        # a cream cornice under them
            stairs(s, X0 - 1, TOP, z, EDGE, "east", half="top")
            stairs(s, X1 + 1, TOP, z, EDGE, "west", half="top")

    # The gables: red, with a window up in each peak - the front one with a heart around it.
    for z in (Z0, Z1):
        for x in range(X0, X1 + 1):
            for y in range(BAND + 1, roof_y(x) - 1):
                if s.name_at(x, y, z) is None:
                    s.put(x, y, z, WALL, axis="y")
    heart(s, lambda row, o: s.put(MID + o, BAND + 1 + row, Z0, HEART))
    s.put(MID, BAND + 3, Z0, GLASS)
    s.put(MID, BAND + 4, Z0, GLASS)
    s.put(MID, BAND + 2, Z1, GLASS)
    s.put(MID, BAND + 3, Z1, GLASS)

    # ---- The bell tower, on the front of the ridge ----
    tz = Z0 + 2                                    # its middle
    base = roof_y(MID - 1)
    for x in range(MID - 1, MID + 2):
        for z in range(tz - 1, tz + 2):
            for y in range(base, base + 3):
                s.put(x, y, z, WALL, axis="y")
    s.put(MID, base + 1, tz - 1, HEART)            # a pink heart on its face
    floor = base + 2
    for x in (MID - 1, MID + 1):                   # the open belfry on four posts
        for z in (tz - 1, tz + 1):
            for y in (floor + 1, floor + 2):
                s.put(x, y, z, "minecraft:birch_fence")
    for x in range(MID - 1, MID + 2):
        for z in range(tz - 1, tz + 2):
            for y in (floor + 1, floor + 2):
                if s.name_at(x, y, z) is None:
                    s.put(x, y, z, "minecraft:air")
    s.put(MID, floor + 2, tz, "minecraft:bell", attachment="ceiling", facing="north", powered="false")
    cap = floor + 3                                # the spire: two stepped rings and a point
    for ring, r in ((cap, 2), (cap + 1, 1)):
        for x in range(MID - r, MID + r + 1):
            for z in range(tz - r, tz + r + 1):
                edge_x, edge_z = abs(x - MID) == r, abs(z - tz) == r
                if edge_x and edge_z:
                    slab(s, x, ring, z, ROOF)
                elif edge_x:
                    stairs(s, x, ring, z, ROOF, "east" if x < MID else "west")
                elif edge_z:
                    stairs(s, x, ring, z, ROOF, "south" if z < tz else "north")
                else:
                    s.put(x, ring, z, ROOF + "_planks")
    s.put(MID, cap + 2, tz, ROOF + "_fence")
    s.put(MID, cap + 3, tz, "minecraft:lightning_rod", facing="up", powered="false", waterlogged="false")  # the weathervane

    # ---- The porch ----
    px0, px1 = MID - 2, MID + 2
    for x in range(px0, px1 + 1):
        for z in range(Z0 - 3, Z0):
            s.put(x, 0, z, FLOOR)
            s.put(x, F, z, EDGE + "_planks")
        stairs(s, x, F, Z0 - 4, EDGE, "south")               # the steps
    for x in (px0, px1):                                     # columns
        for y in range(F + 1, F + 5):
            s.put(x, y, Z0 - 3, TRIM, axis="y")
    for x in range(px0 - 1, px1 + 2):                        # its little gabled roof
        y = F + 5 + min(x - (px0 - 1), (px1 + 1) - x) - 1
        for z in range(Z0 - 5, Z0):
            wood = EDGE if z == Z0 - 5 else ROOF
            if x in (px0 - 1, px1 + 1):
                slab(s, x, F + 5, z, wood)
            elif x == MID:
                s.put(x, y - 1, z, ROOF + "_planks" if wood == ROOF else EDGE + "_planks")
                slab(s, x, y, z, wood)
            else:
                stairs(s, x, y, z, wood, "east" if x < MID else "west")
    # Its gable over the steps: birch, a heart, and an arch between the columns.
    for x in (MID - 1, MID + 1):
        s.put(x, F + 5, Z0 - 3, EDGE + "_planks")
    s.put(MID, F + 5, Z0 - 3, HEART)
    stairs(s, MID - 1, F + 4, Z0 - 3, EDGE, "west", half="top")
    stairs(s, MID + 1, F + 4, Z0 - 3, EDGE, "east", half="top")
    for x in range(px0 + 1, px1):
        for z in range(Z0 - 3, Z0):
            for y in range(F + 1, F + 5):
                if s.name_at(x, y, z) is None:
                    s.put(x, y, z, "minecraft:air")

    # ---- Inside: one room ----
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            for y in range(F + 1, roof_y(x) - 1):
                if s.name_at(x, y, z) is None:
                    s.put(x, y, z, "minecraft:air")
    for x in range(MID - 1, MID + 2):                        # the chalkboard on the back wall
        for y in (F + 2, F + 3):
            s.put(x, y, Z1 - 1, "cim:chalkboard", facing="north")
    for x in range(MID - 1, MID + 2):                        # the teacher's desk, and chair
        slab(s, x, F + 1, Z1 - 3, DESK, "top")
    stairs(s, MID, F + 1, Z1 - 2, DESK, "south")
    for z in (Z0 + 4, Z0 + 7, Z0 + 10):                      # rows of desks, facing the board
        for x in list(range(X0 + 1, X0 + 4)) + list(range(X1 - 3, X1)):
            stairs(s, x, F + 1, z, "minecraft:oak", "north")
            slab(s, x, F + 1, z + 1, "minecraft:oak", "top")
    for z in (Z0 + 3, Z0 + 8, Z0 + 13):                      # lanterns on chains from the ridge
        for y in range(BAND + 1, roof_y(MID) - 1):
            if s.name_at(MID, y, z) == "minecraft:air":
                s.put(MID, y, z, "minecraft:chain", axis="y", waterlogged="false")
        s.put(MID, BAND, z, "minecraft:lantern", hanging="true", waterlogged="false")
    s.put(MID, F + 1, Z0 + 2, "cim:workroom_marker")
    s.put(MID, F, Z0 + 2, "minecraft:jigsaw", orientation="up_north", nbt={
        "id": "minecraft:jigsaw", "name": "minecraft:bottom", "target": "minecraft:bottom",
        "pool": "minecraft:village/plains/villagers", "final_state": FLOOR, "joint": "rollable",
        "placement_priority": 0, "selection_priority": 0})

    # ---- Grounds ----
    path_z = range(Z0 - 9, Z0 - 4)
    for z in path_z:                                         # the path, fenced
        for x in range(MID - 1, MID + 2):
            s.put(x, 0, z, "minecraft:dirt_path")
            for y in (1, 2):
                s.put(x, y, z, "minecraft:air")
        for x in (MID - 2, MID + 2):
            s.put(x, 1, z, "minecraft:birch_fence")
    # The doorway jigsaw sits on the path, a block up (as in ponyville_houses.py), so the ground layer lands in the ground.
    s.put(MID, 0, Z0 - 10, "minecraft:dirt_path")
    s.put(MID, 1, Z0 - 10, "minecraft:jigsaw", orientation="north_up", nbt={
        "id": "minecraft:jigsaw", "name": "minecraft:building_entrance", "target": "minecraft:building_entrance",
        "pool": "minecraft:empty", "final_state": "minecraft:air", "joint": "aligned",
        "placement_priority": 0, "selection_priority": 0})

    # The sign, beside the path: a board hung between two posts on a stone base.
    sz = Z0 - 7
    for x in range(MID - 5, MID - 2):
        s.put(x, 0, sz, "minecraft:stone_bricks")
        slab(s, x, 3, sz, EDGE)
    for x in (MID - 5, MID - 3):
        s.put(x, 1, sz, TRIM, axis="y")
        s.put(x, 2, sz, TRIM, axis="y")
    text = {"messages": ['""', '"School"', '""', '""'], "color": "black", "has_glowing_text": False}
    s.put(MID - 4, 2, sz, "minecraft:birch_hanging_sign", attached="false", rotation=8, waterlogged="false",
          nbt={"id": "minecraft:hanging_sign", "front_text": text, "back_text": text, "is_waxed": False})
    s.put(MID - 4, 1, sz, "minecraft:air")

    # The flagpole: tall, beside the path, a red banner each side so it catches the eye from either way.
    fx, fz, height = MID + 5, Z0 - 6, 20
    s.put(fx, 0, fz, TRIM_WOOD)
    for y in range(1, height + 1):
        s.put(fx, y, fz, "minecraft:acacia_fence")
    s.put(fx, height + 1, fz, "minecraft:lightning_rod", facing="up", powered="false", waterlogged="false")
    s.put(fx, height - 1, fz - 1, "minecraft:red_wall_banner", facing="north")
    s.put(fx, height - 1, fz + 1, "minecraft:red_wall_banner", facing="south")

    # A swing, off to the side.
    sx = X0 - 5
    for z in (Z0 + 3, Z0 + 7):
        for y in range(1, 5):
            s.put(sx, y, z, "minecraft:birch_fence")
    for z in range(Z0 + 4, Z0 + 7):
        s.put(sx, 4, z, "minecraft:birch_fence")
    for z in (Z0 + 4, Z0 + 6):
        for y in (2, 3):
            s.put(sx, y, z, "minecraft:chain", axis="y", waterlogged="false")
        slab(s, sx, 1, z, EDGE, "top")

    s.connect_panes()
    return s


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    size, _ = build().save(os.path.join(out, "schoolhouse.nbt"))
    print(f"schoolhouse: {size[0]} x {size[1]} x {size[2]}")
