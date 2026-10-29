# Voice in Ember

Dictation uses **Phonon-2.5 English** locally. Audio becomes an editable message draft; it is never sent automatically. The microphone is opened only after a user action and Android permission. A recording is limited to 30 seconds. The dot matrix follows measured microphone energy, rather than a simulated speaking state.

The first setup downloads the 163,693,770-byte official `phonon-2.5-en.bps.tar.zst` bundle. It expands to 177,757,399 bytes of model/configuration files. Files are pinned to publisher revision `da200358d638886bc96c758c672ad5568828b119`; both the archive and its three output files are SHA-256 checked. Extraction accepts only those files. The download can resume, and setup pauses when Ember leaves the foreground.

Recordings are temporary PCM files in app-private cache. A private bound service in `:dictation` reads them; they never enter the network or saved conversations. The service process isolates native allocation failures from chat. The chat model is unloaded before recording and restored after successful insertion or closing the voice panel when it was previously loaded. Cancellation during native work terminates the speech process; ordinary completion frees the model and purges unused native heap pages. Temporary audio is deleted on completion or cancellation. This is foreground dictation, not background listening.

The official Android runtime is `android-arm64-v8a-0.2.11`, archive SHA-256 `06d75b2d0e88f4e2194400e9c6c1b5987056702b36a5f95720e56ce8a11d220b`. `setup.ps1` downloads it and verifies individual binaries/header. Native kernels are publisher-supplied Apache-2.0 binaries; their C sources are not included in this package. The JNI wrapper selects dotprod/LSE or baseline libraries before loading, validates the CPU/ABI, bounds tensor shapes, and serializes calls. Libraries stay loaded while their process-global worker pools exist. All supported ABIs are arm64-v8a, with 16 KB aligned native load segments.

The log-mel frontend follows Fermion's Android call guide and the existing Rio Windows implementation: 16 kHz mono, pre-emphasis 0.97, centered 512-point FFT, symmetric 400-sample Hann, hop 160, 128 Slaney mel bands, log guard 2^-24 and unbiased per-feature normalization. Golden features are generated independently using the existing Windows/PyTorch frontend (`scripts/make-speech-fixtures.py`). The `phonon-2-c` MIT implementation was also reviewed as a reference; its different valid-frame convention is not used here.

## Validation

Host tests include golden-feature parity, PCM conversion, silence/bounds handling, token decoding, lossless draft insertion, archive cancellation, traversal/link rejection and full-bundle extraction/checksums. To repeat the large archive check, pass `'-PphononArchive=ABSOLUTE_PATH_TO/phonon-2.5-en.bps.tar.zst'` to `:app:testDebugUnitTest`.

`SpeechDeviceTest` requires an Android phone and the model (or verified archive) in app-private storage. It checks installer/native-library loading, transcription of a CC-BY LibriSpeech fixture, service-process isolation, cancellation, raw-audio deletion, theme panels and unchanged microphone permission. It never grants microphone access. Live microphone quality and permission behavior require a user-controlled recording on the device. Host success does not establish device success or phone latency/memory usage.

### Current verification: 29 October 2026

- Debug app and instrumentation APKs build successfully; 24 JVM tests pass with no skipped tests, including extraction and verification of the full published archive.
- Lint reports zero errors (21 warnings and four hints, including dependency updates and existing platform/style advisories).
- All ten packaged native libraries are ARM64 with load-segment alignment of at least 16 KB; APK ZIP alignment and signature verification pass. The speech JNI library exposes only its three JNI entry points.
- The personal APK retains application ID `dev.bonsai.pocket`, uses version code 7, displays Ember, and has the same debug signing certificate as the previous personal build. It contains notices, but no weights, recordings or signing keys.
- **Pending:** phone installation, native transcription, cancellation/process-memory measurements, light/dark device screenshots and a user-controlled live recording. ADB reported no connected device during this verification. This is a development build; on-device speech support is not yet established.

To run the speech checks, build and install both debug APKs with the existing app ID and signing key using `adb install -r`, preserving all app data. Do not uninstall or clear data. Put the verified archive at `files/speech/phonon-2.5-en.bps.tar.zst` using `run-as` for a debuggable build, or install the model through the microphone sheet. Then run:

```text
adb shell am instrument -w -e class dev.bonsai.pocket.SpeechDeviceTest dev.bonsai.pocket.test/androidx.test.runner.AndroidJUnitRunner
```

For the public application ID, the instrumentation package is `io.github.eruditecoder108.ember.test`; the test class namespace stays `dev.bonsai.pocket`. Microphone permission stays user-controlled. Prerecorded-fixture success still requires a separate live microphone check.

## Liquid voice models

[LFM3-Audio-2B](https://huggingface.co/LiquidAI/LFM3-Audio-2B) accepts text/audio and generates text/speech. [LFM3-Omni-2B](https://huggingface.co/LiquidAI/LFM3-Omni-2B) adds image and video inputs. Upstream llama.cpp audio support merged in [PR 31257](https://github.com/ggml-org/llama.cpp/pull/31257); that does not add microphone, audio codec or playback support to Ember automatically. These models are not integrated into this build. Ember's installed LFM3-1.7B remains text-only.

Phonon here supports English. Hindi is absent from the published Phonon-2.5 and LFM3 Audio speech-language lists. A Hindi/Hinglish option needs a different, separately tested speech model.
