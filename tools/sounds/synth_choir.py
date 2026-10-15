"""Synthesizes the mod's choir "aah" sound - an original, keyboard-style choir pad for the princesses' voices.

    D:\\dev-tools\\audio-venv\\Scripts\\python.exe tools/sounds/synth_choir.py

Writes cim-1.21.1/src/main/resources/assets/cim/sounds/voice/choir_aah.ogg. The note is F#4, the same
reference note as the harp note block, so a choir layer at a given pitch lands exactly on the harp's note
and stays in harmony with every other layer.

How it sounds like a choir rather than one tone:
- an ensemble of voices, each slightly detuned and starting a hair apart (the shimmer of many singers);
- each voice is a full harmonic tone shaped by the formants of the vowel "aah";
- each has its own slow vibrato;
- a breath of air under it all;
- a soft swell in, and a long release, so notes overlap into a pad.
"""

import math
from pathlib import Path

import numpy as np
import soundfile

RATE = 44100
NOTE_HZ = 369.994  # F#4 - the harp note block's pitch 1.0
LENGTH = 1.6  # seconds
VOICES = 7
SEED = 7

# The vowel "aah": formant centre frequencies (Hz), bandwidths (Hz), and relative strengths.
FORMANTS = [(800, 90, 1.0), (1150, 110, 0.6), (2900, 160, 0.18), (3900, 200, 0.08)]


def vowel_envelope(freq: np.ndarray) -> np.ndarray:
    """How loud a harmonic at {freq} comes through the "aah" vowel: a sum of resonance peaks."""
    env = np.zeros_like(freq)
    for centre, width, gain in FORMANTS:
        env += gain / (1.0 + ((freq - centre) / (width / 2.0)) ** 2)
    return env + 0.02  # a little of everything, so it doesn't sound hollow


def amplitude_envelope(t: np.ndarray) -> np.ndarray:
    """A soft swell in (singers don't attack), a long gentle release."""
    attack = 0.14
    release_start = 0.55
    rise = np.clip(t / attack, 0, 1) ** 1.5
    fall = np.where(t < release_start, 1.0, np.exp(-(t - release_start) / 0.32))
    return rise * fall


def voice(t: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    detune_cents = rng.uniform(-9, 9)
    vibrato_rate = rng.uniform(4.6, 5.8)
    vibrato_cents = rng.uniform(8, 16)
    vibrato_phase = rng.uniform(0, 2 * math.pi)
    delay = rng.uniform(0, 0.035)

    # Vibrato grows in after the note settles, as a singer's does.
    vibrato_amount = np.clip((t - 0.15) / 0.3, 0, 1)
    cents = detune_cents + vibrato_cents * vibrato_amount * np.sin(2 * math.pi * vibrato_rate * t + vibrato_phase)
    f0 = NOTE_HZ * 2 ** (cents / 1200.0)
    phase = 2 * math.pi * np.cumsum(f0) / RATE

    out = np.zeros_like(t)
    harmonics = int(5000 // NOTE_HZ)
    shape = vowel_envelope(NOTE_HZ * np.arange(1, harmonics + 1))
    for n in range(1, harmonics + 1):
        out += shape[n - 1] / n ** 0.8 * np.sin(n * phase + rng.uniform(0, 2 * math.pi))
    return np.roll(out, int(delay * RATE)) * amplitude_envelope(np.clip(t - delay, 0, None))


def breath(t: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """Air: white noise through a soft band around the vowel, very quiet."""
    noise = rng.standard_normal(len(t))
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(len(t), 1 / RATE)
    spectrum *= vowel_envelope(freqs)
    return np.fft.irfft(spectrum, len(t)) * 0.02 * amplitude_envelope(t)


def main() -> None:
    rng = np.random.default_rng(SEED)
    t = np.arange(int(LENGTH * RATE)) / RATE
    mix = sum(voice(t, rng) for _ in range(VOICES)) + breath(t, rng)
    mix /= np.max(np.abs(mix))
    mix *= 0.7  # headroom, about -3 dB
    # A few milliseconds of fade at the very ends, so the cut is never a click.
    fade = int(0.005 * RATE)
    mix[:fade] *= np.linspace(0, 1, fade)
    mix[-fade:] *= np.linspace(1, 0, fade)

    out = Path(__file__).resolve().parents[2] / "cim-1.21.1/src/main/resources/assets/cim/sounds/voice/choir_aah.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(out, mix.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {out} ({LENGTH}s, {VOICES} voices, F#4)")


if __name__ == "__main__":
    main()
