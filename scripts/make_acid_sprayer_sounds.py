"""Split the original HLM displacer shot into its wind-up and discharge cues."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server_resource/tacz/HLMPack1.5/assets/hlm/tacz_sounds/dis/shoot.ogg"
OUTPUT = ROOT / "src/main/resources/assets/gungang_acoustics/sounds/acid"
OUTPUT.mkdir(parents=True, exist_ok=True)
for name, start, length in [("warmup", "0", "0.6"), ("empty", "0.7", "1.187868")]:
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(SOURCE),
        "-ss", start, "-t", length, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
        str(OUTPUT / f"{name}.ogg"),
    ], check=True)
