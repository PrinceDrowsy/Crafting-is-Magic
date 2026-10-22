"""Builds Minecraft structure files (.nbt) block by block - the format structure blocks save and
/place template loads. Used by the house generators in this folder.

    b = Structure()
    b.put(0, 0, 0, "minecraft:oak_planks")
    b.put(1, 0, 0, "minecraft:oak_stairs", facing="east", half="bottom", shape="straight", waterlogged="false")
    b.save("house.nbt")

Positions can be negative; the file is shifted so the lowest corner is 0,0,0. Cells never set are
left out of the file entirely (structure void: placing it leaves whatever was there).
"""
import gzip
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1

PANES = ("_stained_glass_pane", "minecraft:glass_pane", "minecraft:iron_bars")
FENCES = ("_fence",)
NOT_SOLID = ("air", "_pane", "_fence", "lantern", "_door", "_trapdoor", "_bed", "leaves", "jigsaw", "_marker",
             "keepsake_spot", "flower", "poppy", "dandelion", "_carpet", "torch", "dirt_path", "banner", "chain",
             "bell", "_sign", "lightning_rod", "chalkboard")


class Structure:
    def __init__(self):
        self.blocks = {}

    def put(self, x, y, z, name, nbt=None, **props):
        self.blocks[(x, y, z)] = (name, {k: str(v).lower() for k, v in props.items()}, nbt)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def name_at(self, x, y, z):
        cell = self.blocks.get((x, y, z))
        return cell[0] if cell else None

    def fill(self, x0, y0, z0, x1, y1, z1, name, nbt=None, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, name, nbt, **props)

    def fill_air_if_unset(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if (x, y, z) not in self.blocks:
                        self.put(x, y, z, "minecraft:air")

    def solid(self, x, y, z):
        name = self.name_at(x, y, z)
        return name is not None and not any(part in name for part in NOT_SOLID)

    def connect_panes(self):
        """Panes and fences join whatever's beside them, as they would once placed by hand."""
        sides = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
        for (x, y, z), (name, props, nbt) in list(self.blocks.items()):
            if not any(name.endswith(p) or name == p for p in PANES + FENCES):
                continue
            for side, (dx, dy, dz) in sides.items():
                other = self.name_at(x + dx, y, z + dz) or ""
                joins = self.solid(x + dx, y, z + dz) or "_pane" in other or other == "minecraft:iron_bars" \
                    or ("_fence" in other and "_fence" in name)
                props[side] = "true" if joins else "false"
            props.setdefault("waterlogged", "false")

    def save(self, path):
        xs = [p[0] for p in self.blocks]; ys = [p[1] for p in self.blocks]; zs = [p[2] for p in self.blocks]
        mx, my, mz = min(xs), min(ys), min(zs)
        size = [max(xs) - mx + 1, max(ys) - my + 1, max(zs) - mz + 1]
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                palette.append({"Name": name, **({"Properties": dict(sorted(props.items()))} if props else {})})
            entry = {"pos": [x - mx, y - my, z - mz], "state": index[key]}
            if nbt:
                entry["nbt"] = nbt
            blocks.append(entry)
        root = {"DataVersion": DATA_VERSION, "size": size, "palette": palette, "blocks": blocks, "entities": []}
        with gzip.open(path, "wb") as f:
            f.write(_compound_tag("", root))
        return size, (mx, my, mz)


# ---- NBT writing ----

def _name(s):
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _payload(value):
    """(tag type, payload bytes) for a Python value: dict -> compound, list of ints -> list of ints, str, int, bool."""
    if isinstance(value, bool):
        return 1, struct.pack(">b", 1 if value else 0)
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        return 8, _name(value)
    if isinstance(value, dict):
        body = b"".join(_tagged(k, v) for k, v in value.items())
        return 10, body + b"\x00"
    if isinstance(value, list):
        if not value:
            return 9, struct.pack(">bi", 0, 0)
        kinds = [_payload(v) for v in value]
        tag = kinds[0][0]
        return 9, struct.pack(">bi", tag, len(value)) + b"".join(p for _, p in kinds)
    raise TypeError(value)


def _tagged(name, value):
    tag, payload = _payload(value)
    return struct.pack(">b", tag) + _name(name) + payload


def _compound_tag(name, value):
    return _tagged(name, value)
