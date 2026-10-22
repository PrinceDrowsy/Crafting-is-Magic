"""Generates three Ponyville-style house shells (docs/building-guide.md) as structure files, for the user
to load with /place template and alter to their liking.

Style, from the show's backgrounds: German half-timbering (birch infill, stripped acacia frame), upper
storeys jettied out over the street, steep thick thatch (hay for now) with drooping eaves, a beam end
poking out at the gable peak, pink lattice windows, pink (cherry) doors. Every room has 3 clear blocks
of headroom; every room has a room marker; beds are in the shell; the doorway and villager-spawn
jigsaws are set up for plains villages.

    python ponyville_houses.py <output folder>
"""
import os
import sys

from nbt_structure import Structure

LOG = "minecraft:stripped_acacia_log"
WOOD = "minecraft:stripped_acacia_wood"
INFILL = "minecraft:birch_planks"
HAY = "minecraft:hay_block"
FLOOR = "minecraft:oak_planks"
GLASS = "minecraft:pink_stained_glass_pane"
POOL = "minecraft:village/plains/villagers"


class House:
    def __init__(self):
        self.s = Structure()
        self.columns = set()   # (x, z) inside the house - air is filled under the roof here
        self.top = 0

    # ---- Walls ----

    def storey(self, x0, z0, x1, z1, y, posts_x=(), posts_z=(), height=3):
        """A timber-framed storey: a beam band at floor level y (the floor inside), walls y+1..y+height,
        corners and the listed posts in acacia, the rest birch."""
        s = self.s
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge_x = x in (x0, x1)
                edge_z = z in (z0, z1)
                self.columns.add((x, z))
                if edge_x and edge_z:
                    s.fill(x, y, z, x, y + height, z, WOOD, axis="y")
                elif edge_z:
                    s.put(x, y, z, LOG, axis="x")
                    for h in range(1, height + 1):
                        s.put(x, y + h, z, *(LOG, ) if x in posts_x else (INFILL,), **({"axis": "y"} if x in posts_x else {}))
                elif edge_x:
                    s.put(x, y, z, LOG, axis="z")
                    for h in range(1, height + 1):
                        s.put(x, y + h, z, *(LOG, ) if z in posts_z else (INFILL,), **({"axis": "y"} if z in posts_z else {}))
                else:
                    s.put(x, y, z, FLOOR)
        self.top = max(self.top, y + height)

    def plate(self, x0, z0, x1, z1, y):
        """The beam band capping the top storey."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) and z in (z0, z1):
                    self.s.put(x, y, z, WOOD, axis="y")
                elif z in (z0, z1):
                    self.s.put(x, y, z, LOG, axis="x")
                elif x in (x0, x1):
                    self.s.put(x, y, z, LOG, axis="z")
                else:
                    self.s.put(x, y, z, FLOOR)

    def window(self, x, z, y, axis, tall=False, box=False, outward=(0, 0)):
        """A pink lattice window in a wall at floor level y: pane at y+2 (and y+3 if tall), acacia sill and
        lintel. axis is the wall's direction. box: a flowering window box outside, below the sill."""
        s = self.s
        s.put(x, y + 1, z, INFILL)     # birch under the sill keeps the walls as cream as the show's
        s.put(x, y + 2, z, GLASS)
        if tall:
            s.put(x, y + 3, z, GLASS)
        else:
            s.put(x, y + 3, z, LOG, axis=axis)
        if box:
            dx, dz = outward
            s.put(x + dx, y + 1, z + dz, "minecraft:flowering_azalea_leaves", distance=7, persistent="true", waterlogged="false")

    def door(self, x, z, y, facing, axis, name="minecraft:cherry_door", hinge="left"):
        s = self.s
        s.put(x, y + 1, z, name, facing=facing, half="lower", hinge=hinge, open="false", powered="false")
        s.put(x, y + 2, z, name, facing=facing, half="upper", hinge=hinge, open="false", powered="false")
        s.put(x, y + 3, z, LOG, axis=axis)

    def stairs(self, x, z, y_from, dx, dz, facing, steps=4):
        """Oak stairs rising one per step from floor level y_from, opening the floor above."""
        for i in range(steps):
            sx, sy, sz = x + dx * i, y_from + 1 + i, z + dz * i
            self.s.put(sx, sy, sz, "minecraft:oak_stairs", facing=facing, half="bottom", shape="straight", waterlogged="false")
            if i < steps - 1:
                self.s.put(sx, y_from + steps, sz, "minecraft:air")  # the stairwell
        return x + dx * (steps - 1), z + dz * (steps - 1)

    def inner_wall(self, x0, z0, x1, z1, y, door_at, door_facing):
        """A birch wall between rooms (with a birch door), so rooms stay separate."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                self.s.fill(x, y + 1, z, x, y + 3, z, INFILL)
        dx, dz = door_at
        axis = "x" if z0 == z1 else "z"
        self.door(dx, dz, y, door_facing, axis, name="minecraft:birch_door")
        self.s.put(dx, y + 3, dz, INFILL)

    # ---- Rooms ----

    def room(self, marker, x, z, y, lamp, lamp_y=None):
        """A room's marker on its floor, and a lantern hanging from its ceiling (lamp_y for a room under
        the roof, where the ceiling is higher)."""
        self.s.put(x, y + 1, z, "cim:" + marker + "_marker")
        lx, lz = lamp
        self.s.put(lx, lamp_y if lamp_y is not None else y + 3, lz, "minecraft:lantern", hanging="true", waterlogged="false")

    def bed(self, x, z, y, facing, color="pink"):
        """A bed on the floor at level y: foot at (x, z), head one step toward facing."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.s.put(x, y + 1, z, f"minecraft:{color}_bed", facing=facing, part="foot", occupied="false")
        self.s.put(x + dx, y + 1, z + dz, f"minecraft:{color}_bed", facing=facing, part="head", occupied="false")

    def spawn(self, x, z, y):
        """A villager-spawn jigsaw in the floor (it becomes floor again)."""
        self.s.put(x, y, z, "minecraft:jigsaw", orientation="up_north", nbt={
            "id": "minecraft:jigsaw", "name": "minecraft:bottom", "target": "minecraft:bottom", "pool": POOL,
            "final_state": FLOOR, "joint": "rollable", "placement_priority": 0, "selection_priority": 0})

    # ---- Roofs ----

    def roof_ridge_z(self, x0, x1, z0, z1, y_base, gables=(), beam=True, overhang_left=True):
        """Steep stepped thatch with the ridge running along z (gables facing front and back).
        x0..x1 are the outer walls; the roof overhangs one block past them and past z0/z1. Thatch is two
        blocks thick; a drooping lip hangs under each eave. gables: the z of each gable wall to fill.
        overhang_left=False for a wing against a taller wall: its roof starts at x0, no eave there."""
        s = self.s
        left, right, y, k = (x0 - 1 if overhang_left else x0), x1 + 1, y_base, 0
        heights = {}
        while left <= right:
            for x in {left, right}:
                heights[x] = y
                for z in range(z0 - 1, z1 + 2):
                    s.put(x, y, z, HAY, axis="y")
                    s.put(x, y + 1, z, HAY, axis="y")
            left, right, y, k = left + 1, right - 1, y + 1, k + 1
        for z in range(z0 - 1, z1 + 2):                       # the droop under the eaves
            if overhang_left:
                s.put(x0 - 1, y_base - 1, z, HAY, axis="y")
            s.put(x1 + 1, y_base - 1, z, HAY, axis="y")
        for zg in gables:
            self._gable_wall_x(x0, x1, zg, heights)
        peak_x = max(heights, key=lambda x: heights[x])
        if beam:                                             # the beam end poking out at each peak
            s.put(peak_x, heights[peak_x], z0 - 2, LOG, axis="z")
            s.put(peak_x, heights[peak_x], z1 + 2, LOG, axis="z")
        self.top = max(self.top, max(heights.values()) + 1)
        return heights

    def _gable_wall_x(self, x0, x1, z, heights):
        """Fills a gable end under the roof: birch, with acacia posts and a stepped brace each side."""
        s = self.s
        mid = (x0 + x1) // 2
        base = min(heights[x0], heights[x1]) if x0 in heights and x1 in heights else self.top
        for x in range(x0, x1 + 1):
            for y in range(base - 1, heights.get(x, base)):
                if s.name_at(x, y, z) in (None, "minecraft:air"):
                    post = x in (x0 + 2, x1 - 2) and x != mid
                    s.put(x, y, z, LOG if post else INFILL, **({"axis": "y"} if post else {}))

    # ---- Finishing ----

    def finish(self, front_z, door_x, ground_y=0):
        """Air inside the house under the roof, the doorstep path, the doorway jigsaw, pane joins."""
        s = self.s
        for (x, z) in self.columns:
            roof = [y for (cx, y, cz), (name, _, _) in s.blocks.items() if cx == x and cz == z and name == HAY]
            limit = min(roof) if roof else self.top
            for y in range(ground_y + 1, limit):
                if (x, y, z) not in s.blocks:
                    s.put(x, y, z, "minecraft:air")
        apron = min(z for (_, _, z) in s.blocks) - 1
        for z in range(apron + 1, front_z):
            s.put(door_x, ground_y, z, "minecraft:dirt_path")
            for y in range(ground_y + 1, ground_y + 3):
                if (door_x, y, z) not in s.blocks:
                    s.put(door_x, y, z, "minecraft:air")
        s.put(door_x, ground_y, apron, "minecraft:jigsaw", orientation="north_up", nbt={
            "id": "minecraft:jigsaw", "name": "minecraft:building_entrance", "target": "minecraft:building_entrance",
            "pool": "minecraft:empty", "final_state": "minecraft:dirt_path", "joint": "aligned",
            "placement_priority": 0, "selection_priority": 0})
        s.connect_panes()


# ---- The three houses ----

def cottage():
    """A small cottage for one pony: one and a half storeys, wide and low under a big droopy thatch.
    Ground: the kitchen. Under the roof: the bedroom, lit by the front gable window."""
    h = House()
    s = h.s
    # Ground storey: walls x 0..8, z 2..10 (front at z=2).
    h.storey(0, 2, 8, 10, 0, posts_x=(3, 5), posts_z=(6,))
    h.door(4, 2, 0, "south", "x")
    for x in (1, 2, 6, 7):
        h.window(x, 2, 0, "x", box=True, outward=(0, -1))
    h.window(4, 10, 0, "x")
    for z in (4, 8):
        h.window(0, z, 0, "z", tall=True)
        h.window(8, z, 0, "z", tall=True)
    # The attic storey: knee walls two high, under the roof.
    h.storey(0, 2, 8, 10, 4, posts_x=(2, 6), posts_z=(6,), height=2)
    for z in (4, 8):
        h.window(0, z, 3, "z")          # small windows in the knee walls
        h.window(8, z, 3, "z")
    h.roof_ridge_z(0, 8, 2, 10, 6, gables=(2, 10))
    s.put(4, 6, 2, GLASS)               # the front gable window, lighting the bedroom
    s.put(4, 7, 2, GLASS)
    s.put(4, 6, 10, GLASS)
    # Inside: stairs up the east side, kitchen below, bedroom above.
    h.stairs(7, 8, 0, 0, -1, "north")
    for z in (6, 7, 8):                 # a rail beside the stairwell
        s.put(6, 5, z, "minecraft:oak_fence")
    h.room("kitchen", 3, 6, 0, (3, 6))
    h.room("bedroom", 3, 5, 4, (4, 6), lamp_y=10)   # hung from the roof (its underside is at y11 here)
    h.bed(2, 8, 4, "south")
    h.spawn(2, 5, 0)
    h.finish(front_z=2, door_x=4)
    return s


def family_home():
    """A family home for three or four: two storeys, the upper one jettied out over the street, a front
    gable, and a lower one-storey kitchen wing to the side. Ground: living room (and the kitchen wing).
    Upstairs: the bedroom and the nursery."""
    h = House()
    s = h.s
    # Main block, ground: x 0..8, z 3..13.
    h.storey(0, 3, 8, 13, 0, posts_x=(3, 5), posts_z=(6, 10))
    h.door(4, 3, 0, "south", "x")
    for x in (1, 2, 6, 7):
        h.window(x, 3, 0, "x", box=True, outward=(0, -1))
    for z in (5, 8, 12):
        h.window(0, z, 0, "z", tall=True)
    # Upper storey jettied front and back: z 2..14.
    h.storey(0, 2, 8, 14, 4, posts_x=(2, 4, 6), posts_z=(5, 8, 11))
    for z in (2, 14):                   # beam ends under the jetty
        for x in (0, 2, 4, 6, 8):
            s.put(x, 3, z, LOG, axis="z")
    for x in (1, 3, 5, 7):
        h.window(x, 2, 4, "x", tall=True)
        h.window(x, 14, 4, "x")
    for z in (4, 7, 12):
        h.window(0, z, 4, "z")
    h.plate(0, 2, 8, 14, 8)
    h.roof_ridge_z(0, 8, 2, 14, 8, gables=(2, 14))
    s.put(4, 10, 2, GLASS)
    s.put(4, 11, 2, GLASS)
    # The kitchen wing: x 8..14, z 5..13, one storey, its own lower roof.
    h.storey(8, 5, 14, 13, 0, posts_x=(11,), posts_z=(9,))
    for x in (10, 12):
        h.window(x, 5, 0, "x", box=True, outward=(0, -1))
    for z in (7, 11):
        h.window(14, z, 0, "z", tall=True)
    h.inner_wall(8, 9, 8, 9, 0, (8, 9), "east")
    h.roof_ridge_z(9, 14, 5, 13, 4, gables=(5, 13), beam=True, overhang_left=False)
    # Inside, ground: the living room (with the stairs), the kitchen in the wing.
    h.stairs(1, 12, 0, 0, -1, "north")
    for z in (10, 11, 12):              # a rail beside the stairwell
        s.put(2, 5, z, "minecraft:oak_fence")
    h.room("living_room", 5, 7, 0, (4, 8))
    h.room("kitchen", 11, 8, 0, (11, 9))
    # Upstairs: bedroom at the front, nursery at the back (where the stairs arrive), a wall between.
    h.inner_wall(1, 7, 7, 7, 4, (5, 7), "south")
    h.room("bedroom", 4, 5, 4, (4, 5))
    h.room("nursery", 5, 11, 4, (5, 11))
    h.bed(6, 4, 4, "north")
    h.bed(7, 4, 4, "north")
    h.bed(6, 12, 4, "south")
    h.bed(7, 12, 4, "south")
    h.spawn(3, 6, 0)
    h.spawn(6, 10, 0)
    h.spawn(11, 11, 0)
    h.finish(front_z=3, door_x=4)
    return s


def work_home():
    """A work home: narrow and tall, three storeys, each jettied out over the one below, a shopfront
    below. Ground: the workroom (front) and kitchen (back). Second floor: the living room. Top: the
    bedroom, under the roof."""
    h = House()
    s = h.s
    # Ground: x 0..6, z 3..13.
    h.storey(0, 3, 6, 13, 0, posts_x=(3,), posts_z=(9,))
    h.door(3, 3, 0, "south", "x")
    for x in (1, 2, 4, 5):              # wide shop windows, full height
        s.put(x, 1, 3, GLASS)
        s.put(x, 2, 3, GLASS)
        s.put(x, 3, 3, GLASS)
    for i, x in enumerate((0, 1, 2, 4, 5, 6)):   # the pink-and-white awning
        s.put(x, 3, 2, "minecraft:pink_wool" if i % 2 == 0 else "minecraft:white_wool")
    for z in (6, 11):
        h.window(0, z, 0, "z", tall=True)
        h.window(6, z, 0, "z", tall=True)
    h.inner_wall(1, 9, 5, 9, 0, (4, 9), "south")
    # Second storey, jettied out front: z 2..13.
    h.storey(0, 2, 6, 13, 4, posts_x=(2, 4), posts_z=(6, 10))
    for x in (1, 3, 5):
        h.window(x, 2, 4, "x", tall=True, box=(x != 3), outward=(0, -1))
    for z in (4, 8, 12):
        h.window(0, z, 4, "z")
        h.window(6, z, 4, "z")
    # Third storey, jettied again: z 1..13, knee walls under the roof.
    h.storey(0, 1, 6, 13, 8, posts_x=(2, 4), posts_z=(5, 9), height=2)
    for z in (3, 7, 11):
        h.window(0, z, 7, "z")
        h.window(6, z, 7, "z")
    h.roof_ridge_z(0, 6, 1, 13, 10, gables=(1, 13))
    s.put(3, 11, 1, GLASS)
    s.put(3, 12, 1, GLASS)
    # Stairs: ground to second along the west wall, second to third along the east.
    h.stairs(1, 8, 0, 0, -1, "north")
    h.stairs(5, 6, 4, 0, 1, "south")
    h.room("workroom", 3, 6, 0, (3, 6))
    h.room("kitchen", 2, 11, 0, (3, 11))
    h.room("living_room", 2, 9, 4, (3, 8))
    h.room("bedroom", 2, 5, 8, (3, 7), lamp_y=13)   # hung from the roof (its underside is at y14 here)
    h.bed(2, 10, 8, "south")
    h.spawn(2, 5, 0)
    h.finish(front_z=3, door_x=3)
    return s


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for name, build in (("cottage", cottage), ("family_home", family_home), ("work_home", work_home)):
        size, _ = build().save(os.path.join(out, name + ".nbt"))
        print(f"{name}: {size[0]} x {size[1]} x {size[2]}")
