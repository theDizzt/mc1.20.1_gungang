"""Offline trainer contract checks; run explicitly with python scripts/test_bot_policy.py."""
import random
import unittest
import train_bot_policy as trainer


def sample(index, session="one", kind="human"):
    return {"schema": 1, "type": kind, "_session": session, "mode": "CONTROL",
            "labelSource": "manual", "action": "OBJECTIVE" if index % 2 else "COVER",
            "features": {"bias": 1, "health": .25 if index % 2 == 0 else 1},
            "tick": index * 10, "episode": 1, "map": "test", "controller": "off", "policy": "none"}


class PolicyTests(unittest.TestCase):
    @staticmethod
    def automatic_rows(session="one"):
        rows = []
        for i in range(60):
            rows.append({"schema": 1, "type": "demonstration", "observationVersion": 2, "actor": "human",
                         "_session": session, "mode": "CONTROL", "tick": i * 10, "endTick": i * 10 + 10,
                         "loadout": "rifle|molotov", "features": {"bias": 1, "visible": i % 2,
                         "primary:rifle": 1, "secondary:molotov": 1},
                         "targets": {"movement": "APPROACH" if i % 2 else "HOLD", "fire": "YES" if i % 2 else "NO"}})
        rows.append({"schema": 1, "type": "recording_end", "_session": session, "dropped": 0})
        return rows

    def test_automatic_training_needs_no_manual_labels(self):
        model, report = trainer.automatic_clone(self.automatic_rows(), 1, random.Random(7))
        trainer.validate_model(model)
        self.assertEqual(2, model["schema"])
        self.assertIn("movement", model["controls"]["CONTROL"])
        self.assertIn("fire", model["controls"]["CONTROL"])
        self.assertNotIn("secondary", model["controls"]["CONTROL"])
        self.assertEqual([], report["held_out_sessions"])

    def test_automatic_holdout_is_a_whole_match(self):
        model, report = trainer.automatic_clone(self.automatic_rows("one") + self.automatic_rows("two"), 1, random.Random(7))
        self.assertEqual(1, len(report["held_out_sessions"]))
        self.assertEqual(60, report["heads"]["CONTROL/movement"]["held_out"]["rows"])

    def test_open_or_dropped_recordings_are_not_training_data(self):
        with self.assertRaises(ValueError):
            trainer.automatic_clone(self.automatic_rows()[:-1], 1, random.Random(7))
        rows = self.automatic_rows()
        rows[-1]["dropped"] = 1
        with self.assertRaises(ValueError):
            trainer.automatic_clone(rows, 1, random.Random(7))

    def test_unknown_observation_is_not_an_always_no_head(self):
        rows = self.automatic_rows()
        for row in rows[:-1]:
            row["targets"]["reload"] = "NO"
        model, _ = trainer.automatic_clone(rows, 1, random.Random(7))
        self.assertNotIn("reload", model["controls"]["CONTROL"])

    def test_legacy_sarsa_does_not_silently_ignore_auto_policy(self):
        model, _ = trainer.automatic_clone(self.automatic_rows(), 1, random.Random(7))
        with self.assertRaisesRegex(ValueError, "Legacy SARSA"):
            trainer.sarsa([], model, 1, random.Random(7))

    def test_bot_data_cannot_be_silently_treated_as_human(self):
        with self.assertRaises(ValueError):
            trainer.behavioral_clone([sample(i, kind="bot") for i in range(50)], 1, random.Random(7))

    def test_training_never_splits_adjacent_frames_across_validation(self):
        rows = [sample(i, session) for session in ("one", "two") for i in range(50)]
        model, report = trainer.behavioral_clone(rows, 1, random.Random(7))
        trainer.validate_model(model)
        self.assertEqual(1, len(report["held_out_sessions"]))
        self.assertEqual(50, report["train"]["rows"])
        self.assertEqual(50, report["held_out"]["rows"])

    def test_truncated_episodes_do_not_bootstrap(self):
        model = {"schema": 1, "version": "test", "modes": {"CONTROL": {"COVER": {"bias": 0}}}}
        rows = [{"type": "transition", "reason": "truncated", "state": sample(i, kind="bot"),
                 "next": None, "reward": 1, "terminal": False} for i in range(50)]
        with self.assertRaises(ValueError):
            trainer.sarsa(rows, model, 1, random.Random(7))

    def test_nonfinite_or_hidden_observations_rejected(self):
        for values in ({"bias": 1, "health": float("nan")}, {"bias": 1, "hidden_enemy": 1}):
            with self.assertRaises(ValueError):
                trainer.features({"features": values})


if __name__ == "__main__":
    unittest.main()
