"""Deterministic, tintable hologram scan texture. No external image dependencies."""
import json
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/gungang"


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


def main():
    size, frames = 32, 8
    pixels = bytearray()
    for frame in range(frames):
        for y in range(size):
            pixels.append(0)
            for x in range(size):
                edge = min(x, y, size - 1 - x, size - 1 - y)
                alpha = 25
                if x % 8 == 0 or y % 8 == 0:
                    alpha = 75
                if edge <= 1:
                    alpha = 180
                if (y - frame * 4) % size < 2:
                    alpha = max(alpha, 145)
                if abs(x - 16) + abs(y - 16) in (5, 6):
                    alpha = max(alpha, 150)
                pixels.extend((255, 255, 255, alpha))
    texture = ROOT / "textures/block/spawn_gate.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    texture.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size * frames, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b""))
    texture.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3, "interpolate": True}}), encoding="utf-8")
    faces = {face: {"texture": "#all", "cullface": face, "tintindex": 0} for face in ("north", "south", "east", "west", "up", "down")}
    model = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False, "textures": {"all": "gungang:block/spawn_gate", "particle": "#all"}, "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces}]}
    for team in ("alpha", "bravo"):
        name = f"{team}_spawn_gate"
        for relative, value in ((f"models/block/{name}.json", model), (f"models/item/{name}.json", {"parent": f"gungang:block/{name}"}), (f"blockstates/{name}.json", {"variants": {"": {"model": f"gungang:block/{name}"}}})):
            path = ROOT / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    print("Generated two team gate models and animated RGBA hologram texture")


if __name__ == "__main__":
    main()
