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
