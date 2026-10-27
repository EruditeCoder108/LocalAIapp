# v0.1.0 validation

Checked on 27 October 2026:

- `:app:assembleRelease`, `:app:testDebugUnitTest`, and `:app:lintDebug` completed successfully.
- All four downloader tests passed: interrupted transfer/resume, servers ignoring Range, checksum rejection preserving an installed file, and cancellation before networking.
- Lint reported no errors, 18 warnings and four hints. Warnings include dependency updates, Android compatibility and UI/style suggestions; passing lint does not prove runtime correctness.
- Release APK signature and 16 KiB ZIP alignment verified with Android build tools.
- APK package: `io.github.eruditecoder108.ember`, version `0.1.0`, ARM64 only. Third-party notices are included in its assets.
- Staged source and APK were scanned for private paths, known device identifiers, credential patterns, private keys and excluded model entries. No matches were found. Model weights and private app data are excluded from the repository and APK.

## Still pending

No phone was connected for the public release checks. The renamed package, fresh-install model downloads, pause/resume on Android, and full local/image inference must still be exercised together on a physical device. The earlier personal build's device results are not acceptance results for this public APK.

Future device checks should cover a clean installation, both model downloads, interrupted downloads, a photo with each optional projector, model switching, export, background cancellation and prolonged generation/thermal behavior. Use a disposable profile for instrumentation tests.
