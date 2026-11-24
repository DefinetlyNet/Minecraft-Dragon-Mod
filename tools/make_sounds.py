"""Synthesises the Red Dragon's sounds (roars, growl, hurt, death, bite, fire breath,
wing flap, footstep) and writes them as mono .ogg files.

Run from the project root:  python3 tools/make_sounds.py   (needs numpy and ffmpeg)
Every sound is built from scratch: a buzzing vocal source shaped by throat-like
resonances, a growl tremor, breath noise and some distortion.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = "src/main/resources/assets/wildcreatures/sounds/red_dragon"
rng = np.random.default_rng(11)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def smooth_noise(n, rate_hz):
    """Slowly wandering random values in -1..1 (rate_hz new values per second)."""
    k = max(2, int(n / SR * rate_hz) + 2)
    pts = rng.uniform(-1, 1, k)
    return np.interp(np.linspace(0, k - 1, n), np.arange(k), pts)


def spectral(x, gain_fn):
    """Filter by shaping the spectrum: gain_fn(freqs) -> gain."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * gain_fn(f), len(x))


def lowpass(x, fc, order=2):
    return spectral(x, lambda f: 1 / (1 + (f / fc) ** (2 * order)))


def bandpass(x, lo, hi):
    return spectral(x, lambda f: 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4))


def formants(x, peaks):
    """Throat resonances: sum of gaussian bumps (centre Hz, width Hz, gain)."""
    def g(f):
        out = np.full_like(f, 0.08)
        for c, w, a in peaks:
            out += a * np.exp(-((f - c) / w) ** 2)
        return out
    return spectral(x, g)


def envelope(n, attack, release, curve=1.0):
    env = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a:
        env[:a] = np.linspace(0, 1, a) ** curve
    if r:
        env[-r:] *= np.linspace(1, 0, r) ** 1.5
    return env


def roar(dur, f0, rise=0.35, fall=0.4, growl_hz=27, breath=0.45, attack=0.12, release=0.7,
         throat=((260, 120, 1.0), (620, 220, 1.3), (1250, 350, 0.7), (2600, 600, 0.25))):
    t = t_axis(dur)
    n = len(t)
    p = t / dur
    contour = 1 + rise * np.sin(np.pi * np.minimum(p * 1.3, 1.0)) - fall * np.maximum(0, p - 0.6) / 0.4 * 0.5
    f = f0 * contour * (1 + 0.04 * smooth_noise(n, 9))
    phase = np.cumsum(2 * np.pi * f / SR)
    saw = 2 * ((phase / (2 * np.pi)) % 1.0) - 1
    sub = 0.5 * np.sin(phase / 2)                                  # rumbling sub-octave
    tremor = 1 + 0.55 * np.sin(2 * np.pi * growl_hz * t + 3 * smooth_noise(n, 4))
    voice = (saw + sub) * tremor
    air = bandpass(rng.standard_normal(n), 250, 3500) * breath * (0.6 + 0.4 * tremor)
    x = formants(voice + air, throat)
    x = np.tanh(2.2 * x / (np.max(np.abs(x)) + 1e-9))              # throaty distortion
    x = lowpass(x, 5200)
    return x * envelope(n, attack, release)


def growl(dur, f0):
    return roar(dur, f0, rise=0.12, fall=0.1, growl_hz=17, breath=0.6, attack=0.25, release=0.5,
                throat=((180, 90, 1.2), (480, 160, 1.0), (1000, 250, 0.4)))


def hurt(dur, f0):
    t = t_axis(dur)
    x = roar(dur, f0, rise=0.25, fall=0.9, growl_hz=35, breath=0.3, attack=0.02, release=0.35,
             throat=((420, 180, 1.0), (900, 300, 1.3), (1900, 450, 0.7)))
    return x * (1 + 0.6 * np.exp(-t / 0.05))                     # sharp yelp at the start


def death(dur):
    t = t_axis(dur)
    x = roar(dur, 105, rise=0.2, fall=1.4, growl_hz=22, breath=0.6, attack=0.08, release=1.6)
    gasp = lowpass(rng.standard_normal(len(t)), 900) * np.exp(-((t - dur * 0.85) / 0.25) ** 2) * 0.6
    sag = np.clip(1.15 - t / dur * 0.6, 0.4, 1)
    return x * sag + gasp


def bite():
    dur = 0.45
    t = t_axis(dur)
    n = len(t)
    snap = bandpass(rng.standard_normal(n), 1800, 7000) * np.exp(-np.maximum(t - 0.06, 0) / 0.012) * (t >= 0.06)
    thump = np.sin(2 * np.pi * (85 - 40 * t) * t) * np.exp(-np.maximum(t - 0.06, 0) / 0.09) * (t >= 0.06)
    crunch = np.zeros(n)
    for start in (0.085, 0.11, 0.14, 0.16):
        g = (t >= start) * np.exp(-np.maximum(t - start, 0) / 0.01)
        crunch += bandpass(rng.standard_normal(n), 700, 4000) * g * rng.uniform(0.4, 0.8)
    snarl = roar(dur, 70, rise=0.1, fall=0.1, growl_hz=17, breath=0.6, attack=0.05, release=0.2) * 0.35
    return snap * 1.3 + thump * 0.9 + crunch + snarl


def breath():
    dur = 2.7
    t = t_axis(dur)
    n = len(t)
    roar_noise = lowpass(rng.standard_normal(n), 1400, order=1) * (0.8 + 0.3 * smooth_noise(n, 12))
    rumble = lowpass(rng.standard_normal(n), 160) * 3.0
    hiss = bandpass(rng.standard_normal(n), 2500, 9000) * 0.18
    crackle = np.zeros(n)
    idx = rng.choice(n, 260, replace=False)
    crackle[idx] = rng.uniform(-1, 1, len(idx))
    crackle = bandpass(crackle, 1500, 8000) * 6
    voice = roar(dur, 62, rise=0.15, fall=0.2, breath=0.9, attack=0.2, release=0.6) * 0.35
    x = roar_noise + rumble + hiss + crackle + voice
    whoosh = 1 + 0.8 * np.exp(-t / 0.18)                           # burst as the fire comes out
    return x * whoosh * envelope(n, 0.06, 0.55)


def flap():
    dur = 0.55
    t = t_axis(dur)
    n = len(t)
    shape = np.exp(-((t - 0.16) / 0.08) ** 2) + 0.35 * np.exp(-((t - 0.33) / 0.07) ** 2)
    air = lowpass(rng.standard_normal(n), 550) * shape
    thump = np.sin(2 * np.pi * 55 * t) * np.exp(-((t - 0.17) / 0.05) ** 2) * 0.6
    leather = bandpass(rng.standard_normal(n), 900, 2500) * np.exp(-((t - 0.15) / 0.02) ** 2) * 0.25
    return air + thump + leather


def step():
    dur = 0.4
    t = t_axis(dur)
    n = len(t)
    boom = np.sin(2 * np.pi * (50 - 25 * t) * t) * np.exp(-t / 0.09)
    dirt = lowpass(rng.standard_normal(n), 380) * np.exp(-t / 0.05) * 0.7
    scrape = bandpass(rng.standard_normal(n), 800, 3000) * np.exp(-t / 0.02) * 0.15
    return boom + dirt + scrape


def save(name, x, peak=0.92):
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = min(len(x) // 10, int(0.01 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "5",
                        f"{OUT}/{name}.ogg"], check=True)
        os.unlink(tmp.name)
    print("wrote", name, f"{len(x) / SR:.2f}s")


os.makedirs(OUT, exist_ok=True)
save("roar1", roar(2.6, 78))
save("roar2", roar(2.1, 92, rise=0.5, fall=0.6, growl_hz=31))
save("growl1", growl(1.6, 52))
save("growl2", growl(1.2, 60))
save("hurt1", hurt(0.6, 150))
save("hurt2", hurt(0.75, 125))
save("death", death(4.2))
save("bite", bite())
save("breath", breath())
save("flap", flap())
save("step", step(), peak=0.8)
