"""CPU-only baseline: manual demonstration BC, then optional conservative offline SARSA.

Writes a candidate, never installs it. Python standard library only.
See docs/BOT_LEARNING.md for the observation/action contract and limitations.
"""
import argparse
import collections
import copy
import json
import math
from pathlib import Path
import random
import re

MODES = {"CONTROL", "NORMANDY", "ESCORT", "BOMB", "DEATHMATCH", "FREE_FOR_ALL"}
ACTIONS = {"ENGAGE", "PUSH", "COVER", "RETREAT", "REGROUP", "SUPPORT", "HEAL", "INVESTIGATE", "RALLY", "OBJECTIVE", "PATROL"}
NUMERIC = {"bias", "health", "visible", "distance", "underFire", "hazard", "objective", "inside", "take", "contest", "hold"}


def features(row):
    result = row.get("features", {})
    if not isinstance(result, dict) or len(result) > 256:
        raise ValueError("Invalid features")
    for key, value in result.items():
        if key not in NUMERIC and not re.fullmatch(r"(primary|secondary|team):[A-Za-z0-9_.:/-]{1,100}", key):
            raise ValueError(f"Unknown feature: {key}")
        if not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1:
            raise ValueError(f"Invalid feature value: {key}")
    if result.get("bias") != 1:
        raise ValueError("Missing bias")
    return result


def load(paths, max_rows=50000):
    rows = []
    for path in paths:
        with path.open(encoding="utf-8") as source:
            last_sequence = -1
            for number, line in enumerate(source, 1):
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                    if row.get("schema") != 1:
                        raise ValueError("Unknown schema")
                    sequence = row["sequence"]
                    if sequence <= last_sequence:
                        raise ValueError("Sequence must increase")
                    # Standalone transition rows carry both endpoints; dropped other rows do not join episodes.
                    last_sequence = sequence
                    row["_session"] = str(path.resolve())
                    rows.append(row)
                    if len(rows) > max_rows:
                        raise ValueError("Row budget exceeded; select fewer recordings or raise --max-rows")
                except (ValueError, KeyError, TypeError) as error:
                    raise ValueError(f"{path}:{number}: {error}") from error
    return rows


def dot(weights, x):
    return sum(weights.get(key, 0.0) * value for key, value in x.items())


def probabilities(head, x):
    scores = {action: dot(weights, x) for action, weights in head.items()}
    maximum = max(scores.values())
    scores = {action: math.exp(value - maximum) for action, value in scores.items()}
    total = sum(scores.values())
    return {action: value / total for action, value in scores.items()}


def evaluate(model, rows):
    correct = count = 0
    for row in rows:
        head = model["modes"].get(row["mode"])
        if not head or row["action"] not in head:
            continue
        probability = probabilities(head, features(row))
        prediction = max(probability, key=probability.get)
        correct += prediction == row["action"]
        count += 1
    return {"rows": count, "label_accuracy": correct / count if count else None}


def behavioral_clone(rows, epochs, rng):
    labelled = [r for r in rows if r.get("type") == "human" and r.get("labelSource") == "manual"
                and r.get("action") in ACTIONS and r.get("mode") in MODES]
    if not labelled:
        raise ValueError("No manually labelled human samples; unlabelled and bot samples are never imitation targets")
    sessions = sorted({r["_session"] for r in labelled})
    rng.shuffle(sessions)
    # Whole recordings stay together to avoid adjacent-frame validation leakage.
    held_out = set(sessions[:max(1, len(sessions) // 5)]) if len(sessions) > 1 else set()
    train = [r for r in labelled if r["_session"] not in held_out]
    validation = [r for r in labelled if r["_session"] in held_out]
    model = {"schema": 1, "version": "bc-candidate", "modes": {}}
    for mode in sorted(MODES):
        samples = [r for r in train if r["mode"] == mode]
        counts = collections.Counter(r["action"] for r in samples)
        if len(counts) < 2 or len(samples) < 40:
            continue
        frequency = collections.Counter(key for row in samples for key in features(row))
        keys = set(NUMERIC) | {key for key, _ in frequency.most_common(240)}
        head = {action: dict.fromkeys(sorted(keys), 0.0) for action in sorted(counts)}
        for _ in range(epochs):
            rng.shuffle(samples)
            for row in samples:
                x = features(row)
                probability = probabilities(head, x)
                # Limit the influence of long repeated labels; balance intent classes.
                balance = min(3, len(samples) / (len(counts) * counts[row["action"]]))
                for action, weights in head.items():
                    error = (int(action == row["action"]) - probability[action]) * balance
                    for key, value in x.items():
                        if key in weights:
                            weights[key] = max(-4, min(4, weights[key] + .025 * (error * value - .01 * weights[key])))
        model["modes"][mode] = head
    if not model["modes"]:
        raise ValueError("Each training mode needs at least 40 labelled rows and two distinct actions (excluding held-out sessions)")
    return model, {"method": "behavioral_cloning", "train": evaluate(model, train),
                   "held_out": evaluate(model, validation), "held_out_sessions": sorted(held_out),
                   "warning": "Label accuracy is not match performance. One session provides no holdout."}


def sarsa(rows, base, epochs, rng):
    model = copy.deepcopy(base)
    validate_model(model)
    original = copy.deepcopy(model["modes"])
    transitions = []
    for row in rows:
        if row.get("type") != "transition" or row.get("reason") not in {"step", "death", "match_end"}:
            continue  # Never bootstrap through gaps, removals, controller changes or match boundaries.
        state, next_state = row.get("state"), row.get("next")
        if not state or state.get("type") != "bot":
            continue
        head = model["modes"].get(state.get("mode"), {})
        if state.get("action") not in head:
            continue
        features(state)
        reward = row["reward"]
        if not isinstance(reward, (int, float)) or not math.isfinite(reward):
            raise ValueError("Invalid reward")
        dt = 10
        if not row["terminal"]:
            if not next_state or next_state.get("action") not in head:
                continue
            if any(state.get(k) != next_state.get(k) for k in ("mode", "map", "episode", "policy", "controller")):
                continue
            dt = next_state["tick"] - state["tick"]
            if not 1 <= dt <= 40:
                continue
            features(next_state)
        transitions.append((state, next_state, max(-2, min(2, reward)), row["terminal"], dt))
    if len(transitions) < 40:
        raise ValueError("Need at least 40 usable bot transitions supported by the base policy")
    for _ in range(epochs):
        rng.shuffle(transitions)
        for state, next_state, reward, terminal, dt in transitions:
            head = model["modes"][state["mode"]]
            weights = head[state["action"]]
            x = features(state)
            target = reward if terminal else reward + .95 ** (dt / 10) * dot(head[next_state["action"]], features(next_state))
            error = max(-1, min(1, target - dot(weights, x)))
            anchor = original[state["mode"]][state["action"]]
            for key, value in x.items():
                if key not in weights:
                    continue
                update = .005 * (error * value - .1 * (weights[key] - anchor[key]))
                # A hard trust region around the imitation model; no unseen actions or exploration.
                weights[key] = max(anchor[key] - .15, min(anchor[key] + .15, weights[key] + update))
    model["version"] = "sarsa-candidate"
    return model, {"method": "experimental_offline_sarsa", "transitions": len(transitions),
                   "warning": "Damage/death and final match result reward only. This is a bounded offline experiment, not evidence of improved play or a calibrated Q model."}


def validate_model(model):
    if model.get("schema") != 1 or not model.get("modes"):
        raise ValueError("Invalid model schema or empty modes")
    if not isinstance(model.get("version"), str) or not 1 <= len(model["version"]) <= 80:
        raise ValueError("Invalid version")
    for mode, head in model["modes"].items():
        if mode not in MODES or not head:
            raise ValueError("Invalid mode")
        for action, weights in head.items():
            if action not in ACTIONS or len(weights) > 256:
                raise ValueError("Invalid action or feature count")
            for key, value in weights.items():
                if key not in NUMERIC and not re.fullmatch(r"(primary|secondary|team):[A-Za-z0-9_.:/-]{1,100}", key):
                    raise ValueError("Invalid feature")
                if not math.isfinite(value) or abs(value) > 100:
                    raise ValueError("Invalid weight")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recordings", nargs="+", type=Path)
    parser.add_argument("--method", choices=["bc", "sarsa"], default="bc")
    parser.add_argument("--base", type=Path, help="BC candidate required for SARSA")
    parser.add_argument("--output", type=Path, required=True, help="New candidate path, never the active policy.json")
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--version", default=None)
    parser.add_argument("--max-rows", type=int, default=50000)
    args = parser.parse_args()
    if not 1 <= args.epochs <= 200:
        parser.error("epochs must be 1..200")
    if args.output.name == "policy.json" or args.output.exists():
        parser.error("Choose a new candidate filename; installing/replacing the live policy is a separate step")
    paths = sorted({p for source in args.recordings for p in (source.glob("*.jsonl") if source.is_dir() else [source])})
    if not 1 <= args.max_rows <= 1000000:
        parser.error("max-rows must be 1..1000000")
    rows = load(paths, args.max_rows)
    rng = random.Random(args.seed)
    if args.method == "bc":
        model, report = behavioral_clone(rows, args.epochs, rng)
    else:
        if args.base is None:
            parser.error("--base is required for sarsa")
        model, report = sarsa(rows, json.loads(args.base.read_text(encoding="utf-8")), args.epochs, rng)
    if args.version:
        model["version"] = args.version
    validate_model(model)
    encoded = json.dumps(model, ensure_ascii=False, indent=2, allow_nan=False)
    if len(encoded.encode("utf-8")) > 262144:
        raise ValueError("Candidate exceeds runtime 256 KiB limit")
    report.update({"seed": args.seed, "epochs": args.epochs, "recordings": [str(p) for p in paths]})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        output.write(encoded + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
