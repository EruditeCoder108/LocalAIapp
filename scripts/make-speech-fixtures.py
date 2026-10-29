"""Regenerate JVM golden features using the independent Windows/PyTorch frontend.

Run with the existing Rio Python environment; no model weights are loaded.
"""
from pathlib import Path
import numpy as np
import torch
from fermion._speech.engine_phonon2_cpu import Phonon2CpuSpeechModel, mel_filters

root = Path(__file__).resolve().parents[1] / "app/src/test/resources/speech"
root.mkdir(parents=True, exist_ok=True)
samples = np.arange(4800, dtype=np.float64)
rng = np.random.default_rng(811)
wave = (0.2*np.sin(2*np.pi*440*samples/16000) + 0.07*np.sin(2*np.pi*133*samples/16000) + rng.normal(0, 0.02, samples.size)).astype(np.float32)
model = Phonon2CpuSpeechModel.__new__(Phonon2CpuSpeechModel)
model._window = torch.hann_window(400, periodic=False)
model._melf = torch.from_numpy(mel_filters())
features, _ = model._log_mel(wave)
wave.astype("<f4").tofile(root / "input.f32")
features.numpy().astype("<f4").tofile(root / "expected.f32")
print("Golden frontend", tuple(features.shape))
