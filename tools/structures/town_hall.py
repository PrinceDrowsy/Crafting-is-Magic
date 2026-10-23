"""Generates the Ponyville town hall (docs/building-guide.md) - a village's centerpiece (villages, towns
and cities; hamlets have other ones) - as a structure file, for the user to load with /place template and
alter to their liking. It follows the reviewed drawing set (Town Hall Blueprints).

Inside, one great hall. At the front it runs right out to the outer wall and the doors open straight into
it. Across the back it steps up and away like an upside-down wedding cake: each tier is a curved,
crescent-shaped wall sweeping from one side of the outer wall, in toward the stage, and out to the other
side, every floor standing on top of the tier below and set further back. The stage sits in a half-circle
built into the first tier, the mayor's balcony juts from it at the lower gallery's level, and its curtains
rise to a checked canopy hung in the open dome. The rooms are inside the crescents: the records room,
backstage and a side room on floor 1; the mayor's office, a landing with the stairs, and another room on
floor 2. The lower gallery is the top of the first tier, the upper gallery the top of the second.

Outside, bottom to top: a veranda with steps on all four sides; the tall cream lower drum (two rows of
lattice windows) with the pink doors; the first balcony; the upper drum with its big windows; the top
balcony with four flags round the red-brown dome; the lantern and the orange spire.

The front (the doors) faces north (-z). The floor is at y0 and the building sits on the ground, as
vanilla's village pieces do. All the sizes are set at the top. Changes made by hand in-game are kept in
town_hall_fixes.json and laid over the top (see hand_fixes).

    python town_hall.py <output folder>
"""
import json
import math
import os
import sys

from nbt_structure import Structure

CREAM = "minecraft:birch_planks"
FRAME = "minecraft:stripped_oak_wood"
PANEL = "minecraft:spruce_planks"          # the brown panelling inside
GOLD = "minecraft:oak"                      # _planks, _slab, _stairs, _fence: the warm gold trim
CURTAIN = "minecraft:red_wool"              # maroon: drapes, swags, tie-backs
PINK = "minecraft:pink_wool"                # the curtains themselves
FOLD = "minecraft:pink_terracotta"          # and the deeper pink in their folds
GLASS = "minecraft:pink_stained_glass"
DOME = "minecraft:mangrove_planks"
SPIRE = "minecraft:acacia_planks"

# ---- Sizes (blocks from the middle) ----
VERANDA_R = 23        # the veranda's edge (and the first balcony's)
LOW = (18.5, 19.5)    # the lower drum's outer wall (cream)
LOW_LINING = (17.5, 18.5)   # and its brown lining; inside it, the hall and the rooms
INSIDE = 17.5
HIGH = (16, 17)       # the upper drum's outer wall
HIGH_LINING = (15, 16)
UPPER = 15            # inside the upper drum
TOP_R = 20.5          # the top balcony's edge
# The dome is a tall bell: straight up from a wide base, round over at the shoulder, then a long inward curve
# to a slim neck under the lantern - a half cosine from the base radius to the neck's, layer by layer.
DOME_BASE, DOME_NECK, DOME_HEIGHT = 18, 3.5, 22
DOME_RADII = [DOME_NECK + (DOME_BASE - DOME_NECK) * (1 + math.cos(math.pi * i / (DOME_HEIGHT - 1))) / 2
              for i in range(DOME_HEIGHT)]
HALF = 3.5            # the half-circle built into the first tier
# Heights.
WALL_LOW = range(1, 21)      # the lower drum
FLOOR2 = 11                  # the lower gallery and floor 2's rooms (their floor)
FLOOR3 = 21                  # the upper gallery, the first balcony outside
WALL_HIGH = range(22, 38)    # the upper drum
TOP = 38                     # the top balcony (outside only: the dome is open)
DOME_TOP = TOP + 1 + len(DOME_RADII)

# The middle sits between x -1 and x 0, so the building is an even number of blocks across and the double
# doors (x -1 and 0) are centered: everything round is measured from x -0.5.
MID_X = -0.5
R = VERANDA_R + 4   # the template's middle (x and z)


def dist(x, z):
    return math.hypot(x - MID_X, z)


def angle(x, z):
    """Degrees round from east (+x), with south (+z, the back) at 90 and north (the front) at 270."""
    return math.degrees(math.atan2(z, x - MID_X)) % 360


def near(a, target, width):
    return abs((a - target + 180) % 360 - 180) <= width


def every(a, offset, width, step=45):
    return any(near(a, offset + step * k, width) for k in range(round(360 / step)))


def cells(reach):
    for x in range(-reach - 1, reach + 1):
        for z in range(-reach, reach + 1):
            yield x, z


def toward_middle(x, z):
    """The way from (x, z) to the middle, as the nearest of the four."""
    dx = x - MID_X
    if abs(dx) >= abs(z):
        return "west" if dx > 0 else "east"
    return "north" if z > 0 else "south"


OPPOSITE = {"west": "east", "east": "west", "north": "south", "south": "north"}
STEP = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}


class Tier:
    """A tier's face: an arc of the circle through its back point and the points where it meets the outer
    wall, either side (meet: the angle round from east, on the west side), then moved forward (toward the
    doors) by forward blocks."""

    def __init__(self, back, meet, forward=0):
        mx, mz = INSIDE * math.cos(math.radians(meet)), INSIDE * math.sin(math.radians(meet))
        c = (back * back - mx * mx - mz * mz) / (2 * (back - mz))
        self.rho = back - c
        self.forward = forward
        self.back = back - forward
        self.c = c - forward

    def d(self, x, z):
        return math.hypot(x - MID_X, z - self.c)


T1, T2 = Tier(10, 215, forward=3), Tier(13, 197)


def hall1(x, z):
    return dist(x, z) < INSIDE and T1.d(x, z) < T1.rho


def t1_wall(x, z):
    return dist(x, z) < INSIDE and T1.rho <= T1.d(x, z) < T1.rho + 1


def t1_room(x, z):
    return dist(x, z) < INSIDE and T1.d(x, z) >= T1.rho + 1


def open2(x, z):
    """Open air on floor 2: the hall, and above the lower gallery."""
    return dist(x, z) < INSIDE and T2.d(x, z) < T2.rho


def t2_wall(x, z):
    return dist(x, z) < INSIDE and T2.rho <= T2.d(x, z) < T2.rho + 1


def t2_room(x, z):
    return dist(x, z) < INSIDE and T2.d(x, z) >= T2.rho + 1


def half_d(x, z):
    return math.hypot(x - MID_X, z - T1.back)


def toward_half(x, z):
    """The way from (x, z) toward the half-circle's middle, as the nearest of the four."""
    dx, dz = MID_X - x, T1.back - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def on_tier(t, a, extra=0.0):
    """Where the ray from the middle at angle a meets a tier's face (extra: blocks behind it), as (x, z)."""
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    uc = uz * t.c
    r = uc + math.sqrt(uc * uc - t.c * t.c + (t.rho + extra) ** 2)
    return MID_X + r * ux, r * uz


def build():
    s = Structure()

    def put(x, y, z, name, **props):
        s.put(R + x, y, R + z, name, **props)

    def name_at(x, y, z):
        return s.name_at(R + x, y, R + z)

    def stairs(x, y, z, wood, facing, half="bottom"):
        put(x, y, z, wood + "_stairs", facing=facing, half=half, shape="straight", waterlogged="false")

    def slab(x, y, z, wood, kind="bottom"):
        put(x, y, z, wood + "_slab", type=kind, waterlogged="false")

    def lantern(x, y, z, hanging=True):
        put(x, y, z, "minecraft:lantern", hanging="true" if hanging else "false", waterlogged="false")

    def ray_cells(test, a, width_blocks=0.6, forward=0):
        """The cells passing `test` that lie along the ray from the middle (moved forward by forward) at angle a."""
        out = []
        for x, z in cells(VERANDA_R):
            d = dist(x, z + forward)
            if d > 0 and test(x, z) and near(angle(x, z + forward), a, math.degrees(width_blocks / d)):
                out.append((x, z))
        return out

    def stage_cells(reach):
        """cells(reach) round the middle, moved forward with the first tier, so all of it moves together."""
        for x, z in cells(reach):
            yield x, z - T1.forward

    # ---- Veranda and steps ----
    def opening(x, z):
        return abs(x - MID_X) <= 4.5 or abs(z) <= 4.5

    for x, z in cells(VERANDA_R + 1):
        d = dist(x, z)
        if d < VERANDA_R + 0.5:
            put(x, 0, z, CREAM)
        elif d < VERANDA_R + 1.5:
            if opening(x, z):
                stairs(x, 0, z, "minecraft:birch", toward_middle(x, z))
            elif every(angle(x, z), 45, 20, 90):
                put(x, 0, z, "minecraft:flowering_azalea_leaves", distance=7, persistent="true", waterlogged="false")
        if VERANDA_R - 0.5 <= d < VERANDA_R + 0.5:
            if not opening(x, z):
                put(x, 1, z, "minecraft:oak_fence")
            if every(angle(x, z), 22.5, 2):
                for y in range(1, FLOOR3):
                    put(x, y, z, "minecraft:oak_fence")              # posts up to the first balcony

    # ---- The lower drum: cream outside, panelled inside, two rows of lattice windows ----
    for x, z in cells(int(LOW[1]) + 1):
        a = angle(x, z)
        d = dist(x, z)
        window = every(a, 0, 6)
        post = every(a, 22.5, 3)
        rows = lambda y: 3 <= y <= 8 or 13 <= y <= 18                   # noqa: E731
        door = z < 0 and x in (-1, 0)
        if LOW[0] <= d < LOW[1]:
            for y in WALL_LOW:
                if y in (1, WALL_LOW[-1]) or post:
                    put(x, y, z, FRAME)
                elif window and rows(y) and not (door and y < 9):
                    put(x, y, z, GLASS)
                else:
                    put(x, y, z, CREAM)
        elif LOW_LINING[0] <= d < LOW_LINING[1]:
            for y in WALL_LOW:
                put(x, y, z, "minecraft:air" if window and rows(y) and not (door and y < 9) else PANEL)
    # The front doors: pink double doors in a red frame, a lattice window over them, straight into the hall.
    for x, z in cells(int(LOW[1]) + 1):
        d = dist(x, z)
        if z >= 0 or d < LOW_LINING[0] or d >= LOW[1]:
            continue
        outer = d >= LOW[0]
        if x in (-1, 0):
            for y in (1, 2):
                if outer:
                    # Hinged on their outer sides, so the pair opens apart down the middle.
                    put(x, y, z, "minecraft:cherry_door", facing="south", half="lower" if y == 1 else "upper",
                        hinge="right" if x == -1 else "left", open="false", powered="false")
                else:
                    put(x, y, z, "minecraft:air")
            for y in (3, 4, 5):
                put(x, y, z, GLASS if outer else "minecraft:air")
            if outer:
                put(x, 6, z, "minecraft:mangrove_planks")
        elif x in (-2, 1) and outer:
            for y in range(1, 7):
                put(x, y, z, "minecraft:mangrove_planks")

    # ---- Floor 1: the first tier's wall, and the rooms in its crescent ----
    for x, z in cells(int(INSIDE) + 1):
        if t1_wall(x, z):
            for y in range(1, FLOOR2):
                put(x, y, z, PANEL)
        elif t1_room(x, z):
            for y in range(1, FLOOR2):
                put(x, y, z, "minecraft:air")
    # Its rooms: backstage behind the stage, the records room to the west, a side room to the east.
    for a in (65, 115):
        for x, z in ray_cells(t1_room, a):
            for y in range(1, FLOOR2):
                put(x, y, z, PANEL)
    # Curtained doorways either side of the stage: the records room (west) and the side room (east).
    for a in (60, 120):
        door = ray_cells(t1_wall, a, 1.0, T1.forward)
        for x, z in door:
            for y in (1, 2, 3):
                put(x, y, z, "minecraft:air")
            put(x, 4, z, CURTAIN)                                        # the lintel
            dx, dz = STEP[toward_middle(x, z)]
            if hall1(x + dx, z + dz) and name_at(x + dx, 4, z + dz) is None:
                put(x + dx, 4, z + dz, "minecraft:red_wall_banner", facing=toward_middle(x, z))   # a drape over it

    # ---- Floor 2: the lower gallery on the first tier, the second tier's wall and its rooms ----
    for x, z in cells(int(INSIDE) + 1):
        if dist(x, z) < INSIDE and T1.d(x, z) >= T1.rho:
            put(x, FLOOR2, z, CREAM)                                     # the gallery, and the rooms' floor
        if t2_wall(x, z):
            for y in range(FLOOR2 + 1, FLOOR3):
                put(x, y, z, PANEL)
        elif t2_room(x, z):
            for y in range(FLOOR2 + 1, FLOOR3):
                put(x, y, z, "minecraft:air")
        if t1_wall(x, z) and half_d(x, z) >= HALF + 0.5:
            put(x, FLOOR2 + 1, z, GOLD + "_fence")                       # the gallery's railing, along the tier's edge
    # Its rooms: the mayor's office (west), a landing with the stairs (middle), another room (east).
    for a in (62, 114):
        for x, z in ray_cells(t2_room, a):
            for y in range(FLOOR2 + 1, FLOOR3):
                put(x, y, z, PANEL)

    # ---- Floor 3: the upper gallery on the second tier; the first balcony and the ceiling strip ----
    for x, z in cells(VERANDA_R):
        d = dist(x, z)
        if d < VERANDA_R + 0.5 and not (d < UPPER and T2.d(x, z) < T2.rho):
            put(x, FLOOR3, z, CREAM)
        if d < UPPER and T2.rho <= T2.d(x, z) < T2.rho + 1:
            put(x, FLOOR3 + 1, z, GOLD + "_fence")                       # the upper gallery's railing
        if VERANDA_R - 0.5 <= d < VERANDA_R + 0.5:
            put(x, FLOOR3 + 1, z, "minecraft:spruce_fence")              # the first balcony's
        if TOP_R - 1 <= d < TOP_R and every(angle(x, z), 22.5, 2):
            for y in range(FLOOR3 + 1, TOP):
                put(x, y, z, "minecraft:spruce_fence")                   # long posts up to the top balcony

    # ---- The upper drum: mostly window, ribs between ----
    def big_window(a, y):
        width = {WALL_HIGH[-2]: 2, WALL_HIGH[-3]: 5, WALL_HIGH[-4]: 8}.get(y, 11)
        return WALL_HIGH[0] + 2 <= y <= WALL_HIGH[-2] and every(a, 0, width)

    for x, z in cells(int(HIGH[1]) + 1):
        a = angle(x, z)
        d = dist(x, z)
        post = every(a, 22.5, 3)
        if HIGH[0] <= d < HIGH[1]:
            for y in WALL_HIGH:
                if y in (WALL_HIGH[0], WALL_HIGH[-1]) or post:
                    put(x, y, z, FRAME)
                elif big_window(a, y):
                    put(x, y, z, GLASS)
                else:
                    put(x, y, z, CREAM)
        elif HIGH_LINING[0] <= d < HIGH_LINING[1]:
            for y in WALL_HIGH:
                put(x, y, z, "minecraft:air" if big_window(a, y) else FRAME if post else PANEL)

    # ---- The top balcony, round the dome (outside only) ----
    for x, z in cells(int(TOP_R) + 1):
        d = dist(x, z)
        if UPPER <= d < TOP_R:
            put(x, TOP, z, PANEL)
        if TOP_R - 1 <= d < TOP_R:
            put(x, TOP + 1, z, "minecraft:cherry_fence")
    for k, color in enumerate(("lime", "purple", "light_blue", "pink")):
        a = math.radians(45 + 90 * k)
        x, z = round((TOP_R - 1) * math.cos(a) + MID_X), round((TOP_R - 1) * math.sin(a))
        for y in range(TOP + 1, TOP + 8):
            put(x, y, z, "minecraft:birch_fence")
        facing = OPPOSITE[toward_middle(x, z)]
        dx, dz = STEP[facing]
        put(x + dx, TOP + 7, z + dz, f"minecraft:{color}_wall_banner", facing=facing)

    # ---- The dome: open inside, its ribs and dormers seen from the hall ----
    big_dormers = range(1, 7)                                            # tall pointed windows low on the dome
    small_windows = range(10, 13)                                        # and four small ones higher up
    for i, radius in enumerate(DOME_RADII):
        y = TOP + 1 + i
        below = DOME_RADII[i - 1] if i else radius
        for x, z in cells(int(DOME_RADII[0]) + 2):
            d = dist(x, z)
            a = angle(x, z)
            # Each layer runs out to the edge of the one below it, so the surface stays closed where it steps in.
            if radius - 1.5 <= d < max(radius, below):               # thick enough to leave no corner gaps
                pointed = {big_dormers[-1]: 2, big_dormers[-2]: 3}.get(i, 4)
                dormer = i in big_dormers and every(a, 0, pointed)
                small = i in small_windows and every(a, 22.5, 2 if i == small_windows[-1] else 3, 90)
                rib = every(a, 22.5, 2) and not small
                put(x, y, z, GLASS if dormer or small else FRAME if rib else DOME)
            elif i == len(DOME_RADII) - 1 and d < radius - 1:
                put(x, y, z, DOME)                                      # the cap
            elif i < 6 and radius <= d < radius + 1 and every(a, 22.5, 3):
                put(x, y, z, "minecraft:dark_oak_planks")               # the dark fins between the dormers

    # ---- The lantern and spire ----
    for x, z in cells(4):
        d = dist(x, z)
        if 2 <= d < 3:
            put(x, DOME_TOP, z, "minecraft:cherry_fence")
            if every(angle(x, z), 0, 15, 90):
                for y in (DOME_TOP + 1, DOME_TOP + 2):
                    put(x, y, z, "minecraft:spruce_fence")
        if d < 3:
            put(x, DOME_TOP + 3, z, SPIRE)
        if d < 2:
            put(x, DOME_TOP + 4, z, SPIRE)
        if d < 1:
            put(x, DOME_TOP + 5, z, SPIRE)
            put(x, DOME_TOP + 6, z, "minecraft:end_rod", facing="up")
            put(x, DOME_TOP + 7, z, "minecraft:end_rod", facing="up")

    # ---- The half-circle in the first tier: stage, curtains, mayor's balcony, crown ----
    def drape(column, y, swags):
        """Curtain cloth: pink, a deeper pink fold every third column, and maroon swags scalloped across it
        at each height in swags (dipping a block between the folds)."""
        for top in swags:
            if y == top or (y == top - 1 and column % 3 != 0):
                return CURTAIN
        return FOLD if column % 3 == 0 else PINK

    half_swags = (FLOOR2 - 1, FLOOR3, WALL_HIGH[-1])                     # under the balcony, the crown, and the top
    for x, z in stage_cells(8):
        h = half_d(x, z)
        if z > T1.back:
            continue
        if HALF - 1 <= h < HALF:
            column = round(math.degrees(math.atan2(z - T1.back, x - MID_X)) / 14)   # vertical folds round the half-circle
            for y in range(1, WALL_HIGH[-1] + 1):
                put(x, y, z, drape(column, y, half_swags))
        elif h < HALF - 1:
            for y in range(1, WALL_HIGH[-1] + 1):
                put(x, y, z, "minecraft:air")
            slab(x, FLOOR2, z, GOLD, "top")                              # its floor at the gallery's level
        elif h < 6.5:
            put(x, 1, z, GOLD + "_planks")                               # the round stage
        elif h < 7.5:
            slab(x, 1, z, GOLD)                                          # its step
        if HALF <= h < 5:
            slab(x, FLOOR2, z, GOLD, "top")                              # the mayor's balcony
            if h >= 4:
                put(x, FLOOR2 + 1, z, GOLD + "_fence")
        if HALF <= h < HALF + 1:
            put(x, FLOOR3 + 1, z, GOLD + "_planks")                      # the gold crown, at the upper gallery:
            slab(x, FLOOR3 + 2, z, GOLD)                                 # a band, with points round its rim
            if round(math.degrees(math.atan2(z - T1.back, x - MID_X))) % 30 < 12:
                put(x, FLOOR3 + 2, z, GOLD + "_fence")
                put(x, FLOOR3 + 3, z, "minecraft:lantern", hanging="false", waterlogged="false")
        if HALF <= h < HALF + 1 and z < T1.back - 1:
            stairs(x, FLOOR3, z, GOLD, OPPOSITE[toward_half(x, z)], "top")   # its scalloped underside
    front_of_half = [(x, z) for x, z in stage_cells(8) if x in (-1, 0) and HALF - 1 <= half_d(x, z) < HALF and z <= T1.back]
    for x, z in front_of_half:
        for y in (1, 2, 3):
            put(x, y, z, "minecraft:air")                                # the arched doorway onto the stage
        stairs(x, 4, z, GOLD, "west" if x == -1 else "east", "top")      # a gold arch over it
    for x, z in stage_cells(8):                                          # maroon drapes tied back either side of it
        if x in (-2, 1) and HALF - 1 <= half_d(x, z) < HALF and z <= T1.back and z < T1.back - 1:
            for y in (1, 2, 3, 4):
                put(x, y, z, CURTAIN)
        for y in (FLOOR2 + 1, FLOOR2 + 2):
            put(x, y, z, "minecraft:air")                                # out onto the mayor's balcony
    for x, z in cells(int(INSIDE)):
        if x in (-1, 0) and t1_wall(x, z) and z > 0:
            for y in (1, 2, 3):
                put(x, y, z, "minecraft:air")                            # through the tier into backstage
    for x in (-1, 0):
        z = min(z for xx, z in stage_cells(8) if xx == x and HALF <= half_d(x, z) < 5 and z <= T1.back)
        put(x, FLOOR2 - 1, z, "minecraft:gold_block")                    # the sun under the balcony
    # Its curtains rise to the checked canopy, hung from the dome.
    canopy_z = T1.back - 2
    for x, z in stage_cells(6):
        d = math.hypot(x - MID_X, z - canopy_z)
        check = "minecraft:yellow_wool" if (x + z) % 2 == 0 else "minecraft:orange_wool"
        if d < 1.5:
            put(x, TOP + 7, z, check)
        if d < 2.5:
            put(x, TOP + 6, z, check)
        if d < 3.5:
            put(x, TOP + 5, z, check)
        if 3.5 <= d < 4.5:
            put(x, TOP + 4, z, CURTAIN if round(math.degrees(math.atan2(z - canopy_z, x - MID_X))) % 40 < 20 else PINK)  # the fringe
            a = math.degrees(math.atan2(z - canopy_z, x - MID_X)) % 360
            if any(near(a, 30 + 60 * k, 12) for k in range(6)):
                column = round(a / 14)
                for y in range(WALL_HIGH[-1] + 1, TOP + 4):
                    put(x, y, z, FOLD if column % 2 else PINK)           # six full drapes down to the half-circle
    for x in (-1, 0):
        for y in range(TOP + 8, DOME_TOP):
            if name_at(x, y, canopy_z) is not None and name_at(x, y, canopy_z) != "minecraft:air":
                break                                                    # up to the dome
            if name_at(x, y, canopy_z) is None or name_at(x, y, canopy_z) == "minecraft:air":
                put(x, y, canopy_z, "minecraft:chain", axis="y", waterlogged="false")   # hung from the dome

    # The red carpet, doors to stage.
    stage_front = min(z for x, z in stage_cells(8) if x == 0 and half_d(x, z) < 7.5 and z <= T1.back)
    for z in range(-int(INSIDE), stage_front):
        for x in (-2, -1, 0, 1):
            put(x, 1, z, "minecraft:red_carpet" if x in (-1, 0) else "minecraft:pink_carpet")

    # ---- Stairs: backstage up to the landing, the landing up to the upper gallery ----
    for k in range(FLOOR2):                                              # rising west along the back of backstage
        stairs(5 - k, 1 + k, 15, "minecraft:spruce", "west")
    for x in (-3, -4):
        put(x, FLOOR2, 15, "minecraft:air")                              # headroom over its top steps
    for k in range(FLOOR3 - FLOOR2):                                     # rising east along the landing
        stairs(-5 + k, FLOOR2 + 1 + k, 14, "minecraft:spruce", "east")
    for x in (2, 3):
        put(x, FLOOR3, 14, "minecraft:air")
    # The landing's door onto the lower gallery, and doors to the rooms either side.
    for x, z in cells(int(INSIDE)):
        if x in (-1, 0) and t2_wall(x, z) and z > 0:
            for y in (FLOOR2 + 1, FLOOR2 + 2):
                put(x, y, z, "minecraft:air")
    for a in (62, 114):
        door = [c for c in ray_cells(t2_room, a) if 15.5 <= dist(*c) < 17]
        for x, z in door[:1]:
            for y in (FLOOR2 + 1, FLOOR2 + 2):
                put(x, y, z, "minecraft:air")
    for a in (170, 10):                                                  # the side rooms open onto the gallery too
        for x, z in ray_cells(t2_wall, a, 0.8):
            for y in (FLOOR2 + 1, FLOOR2 + 2):
                put(x, y, z, "minecraft:air")

    # ---- Curtained backdrops: three either side on the second tier's wall, the middle one lower ----
    HIGH_BOX = FLOOR2 + 3

    def backdrop(at, low, high, tier):
        """A curtained backdrop on a tier's wall."""
        for x, z in cells(int(INSIDE)):
            if dist(x, z) < INSIDE and tier.rho <= tier.d(x, z) < tier.rho + 1 and near(angle(x, z), at, 9):
                column = round(angle(x, z) / 4)
                for y in range(low, high + 1):
                    if name_at(x, y, z) == PANEL:
                        put(x, y, z, drape(column, y, (high,)))

    for middle in (30, 150):
        for at, high in ((middle - 16, True), (middle, False), (middle + 16, True)):
            if high:
                backdrop(at, HIGH_BOX + 1, FLOOR3 - 1, T2)
            else:
                backdrop(at, FLOOR2 + 1, HIGH_BOX, T2)

    # ---- The rooms' dressing ----
    # The records room (west): swirl panelling at shoulder height, filing cabinets, the flower floor, a table.
    def records(x, z):
        return t1_room(x, z) and 115 < angle(x, z) < 215

    for x, z in cells(int(INSIDE) + 1):
        d = dist(x, z)
        if LOW_LINING[0] <= d < LOW_LINING[1] and 117 < angle(x, z) < 213 and name_at(x, 2, z) == PANEL:
            for y in (2, 3):
                put(x, y, z, "minecraft:blue_glazed_terracotta")
        if records(x, z) and INSIDE - 1 <= d < INSIDE and every(angle(x, z), 0, 2, 9) and not every(angle(x, z), 0, 6):
            for y in (1, 2, 3):
                put(x, y, z, "minecraft:warped_planks")
    rx, rz = 13.5 * math.cos(math.radians(160)) + MID_X, 13.5 * math.sin(math.radians(160))
    for x, z in cells(int(INSIDE)):
        if records(x, z):
            petal = any(abs(math.hypot(x - rx - 2.2 * math.cos(math.radians(60 * k)), z - rz - 2.2 * math.sin(math.radians(60 * k))) - 1.4) < 0.4
                        for k in range(6))
            if petal:
                put(x, 0, z, PANEL)                                      # the six-petal flower in the floor
    for x, z in ray_cells(records, 165):
        if 14.5 <= dist(x, z) < 15.5:
            slab(x, 1, z, "minecraft:dark_oak", "top")                   # the table
    # The mayor's office (floor 2, west): desk, chairs, bookshelves.
    office = lambda x, z: t2_room(x, z) and 114 < angle(x, z) < 197    # noqa: E731
    for x, z in ray_cells(office, 140):
        if 15.5 <= dist(x, z) < 16.5:
            slab(x, FLOOR2 + 1, z, "minecraft:dark_oak", "top")
    for x, z in ray_cells(office, 128):
        if INSIDE - 1 <= dist(x, z) < INSIDE:
            for y in range(FLOOR2 + 1, FLOOR2 + 4):
                put(x, y, z, "minecraft:bookshelf")

    # ---- Banners: pennants off the galleries' edges, pink swags between the big windows, maroon drapes ----
    colors = ("red", "blue", "green", "purple", "orange", "light_blue", "yellow", "magenta", "lime", "cyan")
    for x, z in cells(int(INSIDE)):
        a = angle(x, z)
        k = int(a // 24)
        dx, dz = STEP[toward_middle(x, z)]
        if T1.rho - 1 <= T1.d(x, z) < T1.rho and hall1(x, z) and every(angle(x, z + T1.forward), 12, 2, 24) \
                and name_at(x, FLOOR2, z) is None \
                and half_d(x, z) > 5.5:
            k1 = int(angle(x, z + T1.forward) // 24)
            put(x, FLOOR2, z, f"minecraft:{colors[k1 % len(colors)]}_wall_banner", facing=toward_middle(x, z))
        if T2.rho - 1 <= T2.d(x, z) < T2.rho and dist(x, z) < UPPER and every(a, 0, 2, 24) and name_at(x, FLOOR3, z) is None \
                and half_d(x, z) > 5.5:
            put(x, FLOOR3, z, f"minecraft:{colors[(k + 3) % len(colors)]}_wall_banner", facing=toward_middle(x, z))
        if HIGH_LINING[0] - 1 <= dist(x, z) < HIGH_LINING[0] and every(a, 22.5, 2) and name_at(x, WALL_HIGH[-4], z) is None:
            put(x, WALL_HIGH[-4], z, "minecraft:pink_wall_banner", facing=toward_middle(x, z))
        if LOW_LINING[0] - 1 <= dist(x, z) < LOW_LINING[0] and hall1(x, z) and every(a, 22.5, 2) and name_at(x, 9, z) is None:
            put(x, 9, z, "minecraft:red_wall_banner", facing=toward_middle(x, z))

    # ---- Light: lanterns to see by, hidden light blocks so nothing dark spawns inside ----
    for x, z in cells(int(INSIDE)):
        d = dist(x, z)
        if UPPER + 0.5 <= d < UPPER + 1.5 and every(angle(x, z), 0, 2, 30) and open2(x, z):
            lantern(x, FLOOR3 - 1, z)                                    # under the ceiling strip round the front
        if d < UPPER and T2.rho + 1 <= T2.d(x, z) < T2.rho + 2 and every(angle(x, z), 15, 3, 30) \
                and name_at(x, FLOOR3 + 1, z) is None:
            lantern(x, FLOOR3 + 1, z, hanging=False)                     # along the upper gallery
    for a in (90, 165, 15):                                              # the rooms
        for x, z in ray_cells(t1_room, a):
            if 13.5 <= dist(x, z) < 14.5 and name_at(x, FLOOR2 - 1, z) == "minecraft:air":
                lantern(x, FLOOR2 - 1, z)
        for x, z in ray_cells(t2_room, a):
            if 15.5 <= dist(x, z) < 16.5 and name_at(x, FLOOR3 - 1, z) == "minecraft:air":
                lantern(x, FLOOR3 - 1, z)
    for x, z in cells(VERANDA_R):
        d = dist(x, z)
        if VERANDA_R - 2 <= d < VERANDA_R - 1 and every(angle(x, z), 0, 2) and name_at(x, FLOOR3 - 1, z) is None:
            lantern(x, FLOOR3 - 1, z)                                    # the veranda, under the balcony
        if x % 5 or z % 5:
            continue
        for y in (2, FLOOR2 + 2, FLOOR3 + 2):
            here = name_at(x, y, z)
            indoors = d < INSIDE if y < FLOOR3 else d < UPPER
            if indoors and here in (None, "minecraft:air"):
                put(x, y, z, "minecraft:light", level=13, waterlogged="false")

    # ---- Air inside, so nothing grows in it ----
    for x, z in cells(VERANDA_R + 1):
        d = dist(x, z)
        for y in range(1, DOME_TOP + 3):
            i = y - TOP - 1
            inside = (d < INSIDE and y < FLOOR2 and (hall1(x, z) or t1_room(x, z))) \
                or (FLOOR2 < y < FLOOR3 and (open2(x, z) or t2_room(x, z))) or (y == FLOOR2 and hall1(x, z)) \
                or (y >= FLOOR3 and y < TOP and d < UPPER) or (y == TOP and d < UPPER) \
                or (0 <= i < len(DOME_RADII) - 1 and d < DOME_RADII[i] - 1) \
                or (LOW[1] <= d < VERANDA_R + 0.5 and y < FLOOR3) or (HIGH[1] <= d < VERANDA_R + 0.5 and y == FLOOR3 + 1) \
                or (d < 3 and DOME_TOP <= y <= DOME_TOP + 2)
            if inside and name_at(x, y, z) is None:
                put(x, y, z, "minecraft:air")

    hand_fixes(put, name_at)
    s.connect_panes()
    return s


# ---- Hand fixes: changes made to a placed town hall in-game, read back out of the world ----
# Each is a block, where it goes, and what the generator had put there (if that's changed since, the fix no
# longer fits and is skipped with a warning). Those "from" the first tier have z measured from its back. The east side's are the finished ones and are copied onto the
# west, mirrored, wherever the building itself is the same both sides; curtain cloth keeps each side's own.
FIXES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "town_hall_fixes.json")
CLOTH = ("pink_wool", "pink_terracotta", "red_wool")
MIRRORED = {"east": "west", "west": "east", "left": "right", "right": "left",
            "inner_left": "inner_right", "inner_right": "inner_left",
            "outer_left": "outer_right", "outer_right": "outer_left"}


def mirror_x(x):
    return round(2 * MID_X - x)


def hand_fixes(put, name_at):
    fixes = {}
    with open(FIXES) as f:
        for fix in json.load(f):
            x, y, z = fix["at"]
            if fix.get("from") == "first_tier":
                z += T1.back                                             # measured from its back, so they move with it
            fixes[(x, y, z)] = fix
    for (x, y, z), fix in list(fixes.items()):
        mirror = (mirror_x(x), y, z)
        theirs = fixes.get(mirror)
        if x < MID_X or (theirs and fix["block"] in CLOTH and theirs["block"] in CLOTH):
            continue
        if name_at(*mirror) == "minecraft:" + fix["was"]:
            props = {k: MIRRORED.get(v, v) for k, v in fix.get("props", {}).items()}
            if "rotation" in props:
                props["rotation"] = (16 - int(props["rotation"])) % 16
            fixes[mirror] = {"was": fix["was"], "block": fix["block"], "props": props}
    for (x, y, z), fix in fixes.items():
        if name_at(x, y, z) == "minecraft:" + fix["block"] and not fix.get("props"):
            continue                                                     # already so
        if name_at(x, y, z) != "minecraft:" + fix["was"]:
            print(f"hand fix at {x} {y} {z} skipped: expected {fix['was']}, found {name_at(x, y, z)}")
            continue
        put(x, y, z, "minecraft:" + fix["block"], **fix.get("props", {}))


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    size, _ = build().save(os.path.join(out, "town_hall.nbt"))
    print(f"town_hall: {size[0]} x {size[1]} x {size[2]}")
