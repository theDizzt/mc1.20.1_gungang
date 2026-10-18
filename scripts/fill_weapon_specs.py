"""Fill weapon descriptions and loadout specs from the TaCZ/LR Tactical pack data in server_resource/tacz.

Only placeholder values are replaced (`"id: 01"`-style descriptions, `설정 필요`/`서버 설정값` specs) unless --force is given.
Spec values are "number+unit" strings so the loadout screen can draw comparison bars:
  damage    close-range damage per shot (TaCZ splits `damage` across pellets; explosive rounds add explosion damage)
  accuracy  100 / (1 + 0.25*hip spread + 0.75*aim spread)
  stability 100 / (1 + max pitch recoil + max yaw recoil)
  range     distance (blocks) until damage falls below 75% of close-range damage, capped by bullet speed * life
  fireRate  RPM; bolt-action time and single-round reloads are included
"""
import argparse
import glob
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS = ROOT / 'server_resource/tacz'
DEFAULT_DIRS = [ROOT / 'server_resource/config/gungang/server/weapons', ROOT / 'server_resource/config/gungang/server/sub_weapons']
PLACEHOLDER_SPECS = {'', '설정 필요', '서버 설정값'}
TYPES = {'rifle': '소총', 'sniper': '저격소총', 'pistol': '권총', 'smg': '기관단총', 'shotgun': '산탄총', 'mg': '기관총', 'rpg': '발사기'}
MODES = {'auto': '자동', 'semi': '반자동', 'burst': '점사'}
RANGE_CAP = 200


def jsonc(path):
    """Parse pack JSON, which allows // and /* */ comments and trailing commas."""
    text = Path(path).read_text(encoding='utf-8-sig')
    out, i, in_string = [], 0, False
    while i < len(text):
        c = text[i]
        if in_string:
            out.append(c)
            if c == '\\':
                out.append(text[i + 1]); i += 1
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True; out.append(c)
        elif text.startswith('//', i):
            while i < len(text) and text[i] != '\n':
                i += 1
            continue
        elif text.startswith('/*', i):
            i = text.index('*/', i) + 2; continue
        else:
            out.append(c)
        i += 1
    return json.loads(re.sub(r',(\s*[}\]])', r'\1', ''.join(out)))


def pack_file(resource, kind, any_namespace=False):
    namespace, name = resource.split(':')
    hits = sorted(glob.glob(str(PACKS / '*' / 'data' / namespace / kind / f'{name}.json')))
    if not hits and any_namespace:
        # Built-in LR Tactical throwables (e.g. lrtactical:smoke_grenade) ship inside other packs' namespaces.
        hits = sorted(glob.glob(str(PACKS / '*' / 'data' / '*' / kind / f'{name}.json')))
    return hits[0] if hits else None


def num(value, digits=1):
    value = round(value, digits)
    return str(int(value)) if value == int(value) else str(value)


def falloff(adjust):
    return [(float('inf') if e['distance'] == 'infinite' else float(e['distance']), float(e['damage'])) for e in adjust or []]


def gun(gun_id):
    index_path = pack_file(gun_id, 'index/guns')
    if not index_path:
        return None
    index = jsonc(index_path)
    data_path = pack_file(index['data'], 'data/guns')
    if not data_path:
        return None
    d = jsonc(data_path)
    bullet = d.get('bullet', {})
    extra = bullet.get('extra_damage') or {}
    table = falloff(extra.get('damage_adjust'))
    modes = d.get('fire_mode') or ['semi']
    mode_adjust = (d.get('fire_mode_adjust') or {}).get(modes[0]) or {}
    damage = (table[0][1] if table else float(bullet.get('damage') or 0)) + mode_adjust.get('damage', 0)
    explosion = bullet.get('explosion') or {}
    explodes = explosion.get('explode') is True
    shot = damage + (explosion.get('damage', 0) if explodes else 0)

    inaccuracy = d.get('inaccuracy') or {}
    spread = .25 * inaccuracy.get('stand', 0) + .75 * inaccuracy.get('aim', 0)
    recoil = d.get('recoil') or {}
    kick = sum(max([abs(v) for key in recoil.get(axis) or [] for v in key['value']] or [0]) for axis in ('pitch', 'yaw'))

    travel = float(bullet.get('speed', 0)) * float(bullet.get('life', 0))
    effective = travel
    if table:
        effective = None
        for distance, value in table:
            if value < damage * .75:
                break
            effective = distance
        effective = 0 if effective is None else min(effective, travel)
    effective = min(effective, RANGE_CAP)

    rpm = float(d.get('rpm', 60)) + mode_adjust.get('rpm', 0)
    interval = 60 / rpm
    if d.get('bolt') == 'manual_action':
        interval += float(d.get('bolt_action_time', 0))
    magazine = int(d.get('ammo_amount', 1))
    reload = d.get('reload') or {}
    if magazine == 1 and reload.get('type') != 'inventory':
        interval += float((reload.get('cooldown') or {}).get('empty', 0))
    specs = {
        'damage': num(shot),
        'accuracy': num(round(100 / (1 + spread))),
        'stability': num(round(100 / (1 + kick))),
        'range': num(round(effective)) + '블록',
        'fireRate': num(round(60 / interval)) + 'RPM',
    }

    pellets = int(bullet.get('bullet_amount', 1))
    lines = []
    kind = TYPES.get(index.get('type'), '화기')
    if magazine == 1:
        lines.append(f'단발 {kind}.')
    else:
        mag_text = '탄약 무제한' if magazine >= 10000 else f'{magazine}발'
        action = '볼트액션' if d.get('bolt') == 'manual_action' else '·'.join(MODES.get(m, m) for m in modes)
        lines.append(f"{mag_text} {action} {kind}.")
    traits = []
    if pellets > 1:
        traits.append(f'한 발에 {pellets}갈래로 퍼짐')
    if explodes:
        traits.append(f"폭발 반경 {num(explosion.get('radius', 0))}블록·폭발 피해 {num(explosion.get('damage', 0))}")
    ignite = bullet.get('ignite')
    if ignite is True or isinstance(ignite, dict) and ignite.get('entity'):
        traits.append('맞은 대상에 불을 붙임')
    if int(bullet.get('pierce', 1)) > 1:
        traits.append(f"최대 {bullet['pierce']}명 관통")
    if extra.get('armor_ignore'):
        traits.append(f"방어 무시 {num(extra['armor_ignore'] * 100)}%")
    if 'heat' in d:
        traits.append('연사하면 과열됨')
    if reload.get('type') == 'inventory' and magazine < 10000:
        traits.append('인벤토리 탄약을 바로 사용')
    if traits:
        lines.append(', '.join(traits) + '.')
    if table and table[-1][1] < damage:
        # Each entry covers distances up to its own value, so damage starts dropping after the last full-damage entry.
        full = [dist for dist, value in table if value >= damage]
        start = full[-1] if full and full[-1] != float('inf') else 0
        lines.append(f'{num(start)}블록 이후 피해 감소(최소 {num(table[-1][1])}).' if start else f'멀어질수록 피해 감소(최소 {num(table[-1][1])}).')
    headshot = extra.get('head_shot_multiplier')
    if headshot and headshot != 1:
        lines.append(f'헤드샷 피해 {num(headshot, 2)}배.')
    return '\n'.join(line for line in lines if line), specs


def melee(melee_id):
    path = pack_file(melee_id, 'index/melee')
    if not path:
        return None
    d = jsonc(path).get('data', {})
    attributes = d.get('attributes', {})
    damage = float(attributes.get('generic.attack_damage', attributes.get('minecraft:generic.attack_damage', 1)))
    attacks = d.get('attack', {})
    left, right = attacks.get('attack_left', {}), attacks.get('attack_right', {})
    reach = max((a.get('hitbox', {}).get('max_range', 3) for a in (left, right) if a), default=3)
    speed = attributes.get('minecraft:generic.movement_speed', {})
    lines = [f"근접 무기. 좌클릭 {num(damage * left.get('factor', 1))}, 우클릭 강타 {num(damage * right.get('factor', 1))} 피해."]
    if isinstance(speed, dict) and speed.get('amount'):
        lines.append(f"들고 있으면 이동 속도 {num(speed['amount'] * 100)}% 증가.")
    specs = {
        'damage': num(damage * left.get('factor', 1)),
        'accuracy': '근접', 'stability': '근접',
        'range': num(reach) + '블록',
        'fireRate': num(round(1200 / max(1, left.get('cooldown', 20)))) + 'RPM',
    }
    return '\n'.join(lines), specs


def throwable(throwable_id):
    path = pack_file(throwable_id, 'index/throwable', any_namespace=True)
    if not path:
        return None
    t = jsonc(path)
    d = t.get('data', {})
    fuse = d.get('entity', {}).get('life_time', 0) / 20
    if t.get('type') == 'lrtactical:explode':
        blast = d.get('explode', {})
        text = f"던진 뒤 {num(fuse)}초 후 폭발. 반경 {num(blast.get('radius', 0))}블록, 피해 {num(blast.get('damage', 0))}."
    elif t.get('type') == 'lrtactical:smoke':
        text = '던진 위치에 연막을 퍼뜨려 시야를 가립니다.'
    else:
        return None
    return text, None


def source(weapon):
    nbt = weapon.get('itemNbt', '')
    for pattern, reader in ((r'GunId:"([^"]+)"', gun), (r'MeleeWeaponId:"([^"]+)"', melee), (r'ThrowableId:"([^"]+)"', throwable)):
        match = re.search(pattern, nbt)
        if match:
            return match.group(1), reader(match.group(1))
    return None, None


def placeholder_description(weapon):
    text = (weapon.get('description') or '').strip()
    return not text or re.fullmatch(r'id:\s*\d+', text) is not None


def fill(path, force, dry_run):
    raw = path.read_bytes()
    newline = '\r\n' if b'\r\n' in raw else '\n'
    weapon = json.loads(raw.decode('utf-8-sig'))
    resource, result = source(weapon)
    if result is None:
        return f"skip  {path.name}: {'데이터 없음 ' + resource if resource else '팩 무기가 아님'}"
    description, specs = result
    changed = []
    if force or placeholder_description(weapon):
        if weapon.get('description') != description:
            weapon['description'] = description; changed.append('description')
    if specs and 'specs' in weapon:
        current = weapon.get('specs') or {}
        for key, value in specs.items():
            if (force or current.get(key, '') in PLACEHOLDER_SPECS) and current.get(key) != value:
                current[key] = value; changed.append(key)
        weapon['specs'] = current
    if changed and not dry_run:
        text = json.dumps(weapon, ensure_ascii=False, indent=2) + '\n'
        path.write_bytes(text.replace('\n', newline).encode('utf-8'))
    shown = ' '.join(f'{k}={v}' for k, v in (specs or {}).items())
    return f"{'fill' if changed else 'keep'}  {path.name} ({resource}) {shown}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('dirs', nargs='*', type=Path, default=DEFAULT_DIRS, help='weapon JSON folders')
    parser.add_argument('--force', action='store_true', help='overwrite values that are already filled in')
    parser.add_argument('--dry-run', action='store_true', help='print results without writing files')
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    for folder in args.dirs:
        for path in sorted(folder.glob('*.json')):
            print(fill(path, args.force, args.dry_run))


if __name__ == '__main__':
    main()
