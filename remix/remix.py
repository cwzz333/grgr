#!/usr/bin/env python3
"""Slowed + pitched-down + reverb + bass-boost remix generator.

Defaults reproduce the processing measured between orig.mp3 and edited.mp3
(see README.md). Every stage can be tuned or disabled from the command line.

    python remix.py input.mp3 output.mp3
    python remix.py input.mp3 output.mp3 --speed 0.9 --semitones -2 --reverb-mix 0.2
"""
import argparse

import numpy as np
from scipy.ndimage import minimum_filter1d, uniform_filter1d
from scipy.signal import butter, sosfilt, sosfiltfilt
from pedalboard import (
    Compressor,
    HighpassFilter,
    HighShelfFilter,
    LowShelfFilter,
    PeakFilter,
    Pedalboard,
    Reverb,
    time_stretch,
)
from pedalboard.io import AudioFile


def db_to_gain(db):
    return 10.0 ** (db / 20.0)


def rms_db(audio):
    return 20.0 * np.log10(np.sqrt(np.mean(audio**2)) + 1e-12)


def set_rms(audio, target_db):
    return audio * db_to_gain(target_db - rms_db(audio))


def widen(audio, width):
    """Scale the side (L-R) channel; 1.0 leaves the stereo image unchanged."""
    mid = (audio[0] + audio[1]) / 2
    side = (audio[0] - audio[1]) / 2 * width
    return np.stack([mid + side, mid - side])


def limit(audio, sr, ceiling_db, lookahead_ms=5.0):
    """Look-ahead peak limiter: gain reduction only, no make-up gain."""
    ceiling = db_to_gain(ceiling_db)
    peak = np.max(np.abs(audio), axis=0)
    gain = np.minimum(1.0, ceiling / np.maximum(peak, 1e-9))
    n = max(1, int(lookahead_ms * sr / 1000))
    # Min-filter then average over the same window: smooth, never above target.
    gain = uniform_filter1d(minimum_filter1d(gain, n), n)
    return audio * gain


def remix(audio, sr, p):
    if audio.shape[0] == 1:
        audio = np.repeat(audio, 2, axis=0)

    # 1. Tempo and pitch are changed independently (Rubber Band).
    if p.speed != 1.0 or p.semitones != 0.0:
        audio = time_stretch(
            audio, sr, stretch_factor=p.speed, pitch_shift_in_semitones=p.semitones
        )

    # 2. Tone: steep sub cut (the pitch shift pushes bass fundamentals below
    #    what speakers reproduce), bass boost, presence/air boost.
    if p.highpass > 0:
        sos = butter(6, p.highpass, "highpass", fs=sr, output="sos")
        audio = sosfilt(sos, audio, axis=1).astype(np.float32)
    audio = Pedalboard(
        [
            LowShelfFilter(cutoff_frequency_hz=p.bass_freq, gain_db=p.bass, q=0.7),
            PeakFilter(cutoff_frequency_hz=p.presence_freq, gain_db=p.presence, q=0.6),
            HighShelfFilter(cutoff_frequency_hz=p.air_freq, gain_db=p.air, q=0.7),
        ]
    )(audio, sr)

    # 3. Soft saturation: thickens the bass and restores the top octave that
    #    the downward pitch shift leaves empty.
    if p.drive > 0:
        audio = set_rms(audio, -20.0)
        g = db_to_gain(p.drive)
        audio = np.tanh(audio * g) / np.tanh(g)

    # 4. Reverb, mixed in parallel; stereo width applies to the wet signal only.
    if p.reverb_mix > 0:
        tail = np.zeros((2, int(p.reverb_tail * sr)), dtype=audio.dtype)
        padded = np.concatenate([audio, tail], axis=1)
        wet = Pedalboard(
            [
                HighpassFilter(cutoff_frequency_hz=p.reverb_lowcut),
                Reverb(
                    room_size=p.reverb_size,
                    damping=p.reverb_damping,
                    wet_level=1.0,
                    dry_level=0.0,
                    width=1.0,
                ),
            ]
        )(padded, sr)
        wet = widen(wet, p.reverb_width)
        if p.mono_below > 0:
            # Keep the wet low end centred so the bass stays solid.
            sos = butter(4, p.mono_below, "lowpass", fs=sr, output="sos")
            side_low = sosfiltfilt(sos, (wet[0] - wet[1]) / 2)
            wet = np.stack([wet[0] - side_low, wet[1] + side_low]).astype(np.float32)
        audio = (1.0 - p.reverb_mix) * padded + p.reverb_mix * wet

    # 5. Dynamics: glue compression, then loudness target and peak ceiling.
    audio = set_rms(audio, -20.0)
    audio = Compressor(
        threshold_db=p.comp_threshold, ratio=p.comp_ratio, attack_ms=10, release_ms=250
    )(audio, sr)
    audio = set_rms(audio, p.loudness)
    return limit(audio, sr, p.ceiling)


def build_parser():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("input")
    ap.add_argument("output", help="output file (.mp3, .wav, .flac, .ogg)")
    g = ap.add_argument_group("tempo / pitch")
    g.add_argument("--speed", type=float, default=0.842, help="tempo multiplier (default: %(default)s)")
    g.add_argument("--semitones", type=float, default=-5.67, help="pitch shift (default: %(default)s)")
    g = ap.add_argument_group("tone")
    g.add_argument("--highpass", type=float, default=50.0, help="steep sub cut, Hz (0 = off)")
    g.add_argument("--bass", type=float, default=15.0, help="low-shelf gain, dB")
    g.add_argument("--bass-freq", type=float, default=110.0, help="low-shelf corner, Hz")
    g.add_argument("--presence", type=float, default=6.5, help="presence bell gain, dB")
    g.add_argument("--presence-freq", type=float, default=2500.0, help="presence bell centre, Hz")
    g.add_argument("--air", type=float, default=8.0, help="high-shelf gain, dB")
    g.add_argument("--air-freq", type=float, default=13000.0, help="high-shelf corner, Hz")
    g.add_argument("--drive", type=float, default=3.0, help="saturation drive, dB (0 = off)")
    g = ap.add_argument_group("reverb")
    g.add_argument("--reverb-mix", type=float, default=0.4, help="wet share 0..1 (0 = off)")
    g.add_argument("--reverb-size", type=float, default=0.8, help="room size 0..1")
    g.add_argument("--reverb-damping", type=float, default=0.3, help="high-frequency damping 0..1")
    g.add_argument("--reverb-width", type=float, default=0.4, help="stereo width of the wet signal")
    g.add_argument("--reverb-lowcut", type=float, default=40.0, help="low cut on the wet signal, Hz")
    g.add_argument("--mono-below", type=float, default=250.0, help="wet signal is mono below this, Hz (0 = off)")
    g.add_argument("--reverb-tail", type=float, default=0.0, help="seconds of tail kept after the end")
    g = ap.add_argument_group("dynamics")
    g.add_argument("--comp-threshold", type=float, default=-28.0, help="compressor threshold, dB")
    g.add_argument("--comp-ratio", type=float, default=4.0, help="compressor ratio")
    g.add_argument("--loudness", type=float, default=-13.8, help="output RMS level, dBFS")
    g.add_argument("--ceiling", type=float, default=-3.5, help="peak ceiling, dBFS")
    g.add_argument("--bitrate", type=int, default=192, help="kbps for lossy output")
    return ap


def main():
    p = build_parser().parse_args()
    with AudioFile(p.input) as f:
        sr = int(f.samplerate)
        audio = f.read(f.frames)
    out = remix(audio, sr, p)
    kwargs = {"quality": p.bitrate} if p.output.lower().endswith((".mp3", ".ogg")) else {}
    with AudioFile(p.output, "w", sr, out.shape[0], **kwargs) as f:
        f.write(out)
    print(f"{p.input} ({audio.shape[1] / sr:.2f}s) -> {p.output} ({out.shape[1] / sr:.2f}s)")


if __name__ == "__main__":
    main()
