"""Synthesize the desk clock alarm and the money cart's cash machine sounds (numpy + ffmpeg, mono Ogg Vorbis)."""
from pathlib import Path
import subprocess, tempfile, wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/gungang/sounds"
RATE = 44100


def tone(freq, seconds, volume=.5, square=0.0):
    t = np.arange(int(RATE * seconds)) / RATE
    wave_ = np.sin(2 * np.pi * freq * t)
    if square:
        wave_ = (1 - square) * wave_ + square * np.tanh(6 * wave_)
    # Short fades so no beep clicks.
    edge = min(len(t) // 4, int(RATE * .004))
    env = np.ones(len(t))
    env[:edge] = np.linspace(0, 1, edge)
    env[-edge:] = np.linspace(1, 0, edge)
    return volume * wave_ * env


def silence(seconds):
    return np.zeros(int(RATE * seconds))


def alarm():
    # A digital alarm: four quick beeps, then a pause (the clock repeats it while the alarm is on).
    beep = tone(2730, .07, .45, square=.6)
    gap = silence(.06)
    return np.concatenate([beep, gap, beep, gap, beep, gap, beep, silence(.35)])


def atm():
    rng = np.random.default_rng(7)
    parts = []
    # Keypad: two confirmation beeps.
    for _ in range(2):
        parts += [tone(1480, .09, .35, square=.3), silence(.07)]
    # Dispenser motor: a low hum with a rising whine.
    n = int(RATE * .55)
    t = np.arange(n) / RATE
    freq = 95 + 40 * t / t[-1]
    motor = .22 * np.sin(2 * np.pi * np.cumsum(freq) / RATE) + .08 * np.sin(2 * np.pi * np.cumsum(freq * 3.1) / RATE)
    motor *= np.minimum(1, t / .05) * np.minimum(1, (t[-1] - t) / .05)
    # Bills riffling out on top of the motor: rapid filtered noise flicks.
    flicks = np.zeros(n)
    for start in np.arange(.08, .5, .028):
        i = int(start * RATE)
        k = int(.012 * RATE)
        burst = rng.standard_normal(k) * np.exp(-np.linspace(0, 6, k))
        flicks[i:i + k] += .35 * np.convolve(burst, np.ones(6) / 6, "same")
    parts.append(motor + flicks)
    parts.append(silence(.05))
    # Done: a bright two-note chime.
    chime = np.concatenate([tone(1318.5, .12, .3), tone(1760, .28, .3)])
    chime *= np.concatenate([np.ones(int(RATE * .12)), np.exp(-np.linspace(0, 4, len(chime) - int(RATE * .12)))])
    parts.append(chime)
    return np.concatenate(parts)


def save(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / (name + ".wav")
        with wave.open(str(wav), "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(RATE)
            f.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(OUT / (name + ".ogg"))], check=True)


if __name__ == "__main__":
    save("clock_alarm", alarm())
    save("cash_machine", atm())
