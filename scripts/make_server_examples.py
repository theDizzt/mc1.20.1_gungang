"""Read the supplied server reference without changing it; export anonymous item/map examples."""
from pathlib import Path
import gzip
import io
import json
import struct

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server_resource"
OUT = ROOT / "docs/server-examples"


def read_nbt(path):
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number("H")).decode("utf-8")

    def payload(kind):
        if kind in (1, 2, 3, 4, 5, 6):
            value = number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        elif kind == 8:
            value = string()
        elif kind == 9:
            child, length = number("B"), number("i")
            value = [payload(child) for _ in range(length)]
        elif kind == 10:
            value = {}
            while (child := number("B")) != 0:
                name = string()
                value[name] = payload(child)
        elif kind in (7, 11, 12):
            value = [number({7: "b", 11: "i", 12: "q"}[kind]) for _ in range(number("i"))]
        else:
            raise ValueError(kind)
        return kind, value

    kind = number("B")
    string()
    return payload(kind)


def snbt(tag):
    kind, value = tag
    if kind == 10:
        return "{" + ",".join(json.dumps(k, ensure_ascii=False) + ":" + snbt(v) for k, v in value.items()) + "}"
    if kind == 9:
        return "[" + ",".join(map(snbt, value)) + "]"
    if kind == 8:
        return json.dumps(value, ensure_ascii=False)
    if kind in (7, 11, 12):
        prefix = {7: "B", 11: "I", 12: "L"}[kind]
        suffix = {7: "b", 11: "", 12: "L"}[kind]
        return "[" + prefix + ";" + ",".join(str(n) + suffix for n in value) + "]"
    return str(value) + {1: "b", 2: "s", 3: "", 4: "L", 5: "f", 6: "d"}[kind]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def examples():
    maps = [("lijang", "된장타워", (-205, 7, -237, 0), (-171, 7, -237, 0)),
            ("ilios", "일히오스", (98, 34, -13, 0), (-102, 34, -13, 0)),
            ("busan", "사찰", (175, 34, -66, 0), (175, 34, 112, 180))]
    for key, name, alpha, bravo in maps:
        zones = {"lijang": (-194,8,-139,-181,16,-126), "ilios": (-10,32,60,7,38,71), "busan": (187,32,18,198,39,29)}
        def spawn(values):
            x, y, z, yaw = values
            return dict(x=x + .5, y=y, z=z + .5, yaw=yaw, pitch=0)

        def area(values):
            x, y, z, _ = values
            return dict(minX=x-4, minY=y-1, minZ=z-4, maxX=x+5, maxY=y+5, maxZ=z+5)

        write(OUT / f"config/gungang/server/maps/{key}.json", dict(
            id=key, name=name, description="제공된 Gungame 데이터팩의 스폰 좌표. 스폰 구역 경계를 확인하고 enabled를 켜세요.",
            enabled=False, timeLimitSeconds=600, gameMode="adventure", dimension="minecraft:overworld",
            alphaSpawn=spawn(alpha), bravoSpawn=spawn(bravo), spectatorSpawn=dict(x=13.5,y=95,z=-25.5,yaw=90,pitch=0),
            alphaArea=area(alpha), bravoArea=area(bravo),
            capture=dict(name=name+" 거점",area=dict(zip(("minX","minY","minZ","maxX","maxY","maxZ"),zones[key])),
                         winningScore=100,captureStepTicks=2,scoreIntervalTicks=30),
            onPrepare=[], onStart=[], onEnd=[]))

    items = {}
    for path in sorted((SOURCE / "world/playerdata").glob("*.dat")):
        data = read_nbt(path)[1]
        for tag in data.get("Inventory", (9, []))[1]:
            item = tag[1]
            if item.get("id", (8,""))[1] != "tacz:modern_kinetic_gun":
                continue
            gun_id = item.get("tag", (10, {}))[1].get("GunId", (8, ""))[1]
            if gun_id and gun_id not in items:
                items[gun_id] = (10, {k: v for k,v in item.items() if k != "Slot"})
    weapons = []
    for index, (gun_id, tag) in enumerate(items.items()):
        weapons.append(dict(id=f"reference_{index+1}", itemNbt=snbt(tag), name=gun_id, category="미분류",
                            description="서버 참고 자료의 실제 총기 NBT. 부착물 정보 포함. 표시 이름과 설명을 편집하세요.",
                            specs=dict(damage="설정 필요",accuracy="설정 필요",stability="설정 필요",range="설정 필요",fireRate="설정 필요"),
                            usage="서버의 TaCZ 키 설정에 따라 사용합니다."))
    write(OUT / "reference-weapons.json", weapons)
    by_gun = {weapon["name"]: weapon for weapon in weapons}
    if "tacz:mk14" in by_gun and "bf1:sw_model3" in by_gun:
        primary = dict(by_gun["tacz:mk14"], id="mk14_hamr", name="MK14 / HAMR", category="지정사수소총")
        secondary = dict(by_gun["bf1:sw_model3"], id="sw_model3", name="S&W Model 3", category="권총")
        secondary.pop("specs", None)
        write(OUT / "config/gungang/server/weapons/01_mk14_hamr.json", primary)
        write(OUT / "config/gungang/server/sub_weapons/01_sw_model3.json", secondary)
        write(OUT / "config/gungang/server/slot9.json", dict(id="food", itemNbt='{id:"minecraft:cooked_beef",Count:16b}', name="전투 식량",
                         description="핫바 9번 기본 지급 예시", usage="우클릭으로 먹습니다."))
    print(f"Exported {len(maps)} maps and {len(weapons)} unique gun NBT examples; source files unchanged.")


if __name__ == "__main__":
    examples()
