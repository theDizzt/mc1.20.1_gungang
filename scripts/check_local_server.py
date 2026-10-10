"""RCON smoke checks against this repository's isolated run-server only."""
import json
from pathlib import Path
import socket
import struct

ROOT = Path(__file__).resolve().parents[1] / "run-server"
properties = dict(line.split("=", 1) for line in (ROOT / "server.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
assert properties["server-ip"] == "127.0.0.1"


def receive(sock):
    def exact(length):
        result = b""
        while len(result) < length:
            part = sock.recv(length - len(result))
            if not part:
                raise EOFError()
            result += part
        return result
    data = exact(struct.unpack("<i", exact(4))[0])
    return struct.unpack("<ii", data[:8]), data[8:-2].decode("utf-8")


def send(sock, kind, body, ident=1):
    packet = struct.pack("<ii", ident, kind) + body.encode() + b"\0\0"
    sock.sendall(struct.pack("<i", len(packet)) + packet)
    return receive(sock)


with socket.create_connection(("127.0.0.1", int(properties["rcon.port"])), timeout=15) as sock:
    header, _ = send(sock, 3, properties["rcon.password"])
    assert header[0] == 1, "RCON authentication failed"
    for command in ["gungang reload", "gungang lobby open", "gungang random", "gungang lobby close"]:
        _, result = send(sock, 2, command)
        print(command + ": " + (result or "OK"))
        assert "Unknown" not in result and "오류" not in result
    config = ROOT / "config/gungang/acoustics.json"
    original = config.read_text(encoding="utf-8")
    try:
        settings = json.loads(original)
        settings["weaponVolume"] = 999
        config.write_text(json.dumps(settings), encoding="utf-8")
        _, result = send(sock, 2, "gungang reload")
        assert "Invalid acoustic option" in result, result
        print("Invalid server audio settings rejected without overwriting: OK")
    finally:
        config.write_text(original, encoding="utf-8")
    _, result = send(sock, 2, "gungang reload")
    print("Restored config reload: " + result)
    layout = ROOT / "config/gungang/server"
    assert list((layout / "maps").glob("*.json"))
    legacy = json.loads((ROOT / "config/gungang/loadouts.json").read_text(encoding="utf-8"))
    for folder, category in [("weapons", "primary"), ("sub_weapons", "secondary")]:
        weapons = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((layout / folder).glob("*.json"))]
        assert weapons == legacy[category], "Migration must preserve complete weapon data and order"
    print("Individual map/weapon layout and complete legacy NBT migration: OK")
    duplicate = layout / "weapons/__smoke_duplicate.json"
    assert not duplicate.exists()
    try:
        duplicate.write_text(json.dumps(legacy["primary"][0]), encoding="utf-8")
        _, result = send(sock, 2, "gungang reload")
        assert "Duplicate weapon id" in result, result
        print("Duplicate weapon files rejected: OK")
    finally:
        duplicate.unlink(missing_ok=True)
    _, result = send(sock, 2, "gungang reload")
    assert "Duplicate" not in result
    send(sock, 2, "stop")
