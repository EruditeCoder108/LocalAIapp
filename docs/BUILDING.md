# Build Ember

## Toolchain

- JDK 21; Gradle wrapper 9.6.0.
- Android SDK platform 37 and build-tools 36.0.0. Target API 36; minimum API 28.
- NDK 28.2.13676358 and CMake 3.22.1.
- Network access for dependencies and the pinned inference source.

The first public project was built on Windows with PowerShell. `setup.ps1` locates the SDK at the standard local Android SDK path; set `sdk.dir` in your own ignored `local.properties` if it is installed elsewhere. Set `JAVA_HOME` to JDK 21 before building.

```powershell
./setup.ps1
./gradlew.bat :app:testDebugUnitTest :app:lintDebug --console=plain
```

The setup script fetches and checks PrismML's runtime at commit `e3de715222b2667c54a267441159a5729f11155e`, tag `prism-b10777-e3de715`. This is an intentional compatibility pin for Bonsai PQ2_0. Runtime source and build output are ignored by Git.

LFM3 uses a separate native library from upstream llama.cpp tag `b11938`, commit `19cce6919992fdffd92e0ac152caaf1e2b8f254e`, under `vendor/liquid.cpp`. `setup.ps1` fetches and verifies both pins. Separate Gradle native builds and JNI-only export maps prevent llama/ggml symbols from interposing between runtimes. Only one model is loaded at a time. Do not replace the Prism runtime with upstream llama.cpp: Bonsai PQ2_0 still needs Prism's kernels.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

The public app uses application ID `io.github.eruditecoder108.ember`. The internal Kotlin/JNI namespace remains `dev.bonsai.pocket`; this does not select a model or change the displayed app name. It is a separate application from the personal development build.

To upgrade the earlier personal phone build in place, build with `./setup.ps1 -ApplicationId dev.bonsai.pocket` or pass `'-PemberApplicationId=dev.bonsai.pocket'` to Gradle, then use `./install.ps1 -ApplicationId dev.bonsai.pocket -Serial YOUR_DEVICE_SERIAL`. The version code is 5. Both app IDs display **Ember**. Preserve the original signing key and app ID; installation must succeed with `adb install -r` without uninstalling or clearing data.

## Install and models

Use `./install.ps1 -Serial YOUR_DEVICE_SERIAL` to install a local debug build. Alternatively install the published, signed release APK. Debug and public-release signatures differ: use the matching signing key to update an existing installation, or use a separate app ID. Do not uninstall or clear app data to work around a signature mismatch.

The model picker downloads language and image files directly into private app storage. It uses publisher revisions pinned in `Models.kt`, exact lengths and SHA-256 checks. An interrupted transfer is retained as a `.part` file and can resume. A bad download never activates over an installed model. Leaving the app pauses a download; no background transfer service is included.

The installer also accepts `-ModelFile`, `-Model2BFile`, `-Vision2BFile`, `-Vision4BFile` and `-Lfm3File` for explicitly provisioning exact supported files. LFM3 is the 1,049,615,680-byte `LFM3-1.7B-QAD-Q4_0.gguf` from pinned publisher revision `b3f2ae5d73cd3c91bf40e8ba752c648aa05dffdf`, SHA-256 `43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452`. Do not commit weights or private app data.

## Code files and Canvas

Code fences become named file cards automatically, including replies saved by older builds. The model is prompted to emit complete files using an opening fence such as `html filename=button.html`. Unnamed blocks receive safe default filenames. Downloads write the exact UTF-8 source, without Markdown fences or document wrappers, to `Downloads/Ember` through MediaStore on Android 10+. Android 9 uses the document picker. Generated files never execute through the download action.

Enable **+ → Canvas** for inline HTML/CSS/SVG previews. Browser JavaScript runs inside a sandboxed iframe with no same-origin permission. WebView blocks network loads, file/content access, popups, native bridges, web downloads and device permission requests; CSP additionally blocks fetch, forms, frames and external resources. Named sibling resources in a reply are bundled as data URLs. Python, Kotlin, shell code, Node runtimes, remote CDNs and package installation are not supported. Code inspection and local preview edits are available by tapping the file card; edits affect that preview/download and are not written back into the model's chat history.

Device checks can be selected without running older exploratory tests: `:app:connectedDebugAndroidTest '-PemberApplicationId=dev.bonsai.pocket' '-Pandroid.testInstrumentationRunnerArguments.class=dev.bonsai.pocket.Lfm3DeviceTest,dev.bonsai.pocket.CanvasDeviceTest'`. The native test never changes conversations. The Canvas test creates and removes only its uniquely named download fixture. Use a disposable profile for tests that generate persistent chat fixtures.

## Compatibility and validation

- ARM64 only. Native CPU flags target ARMv8.2 with dot-product support; Android version alone does not establish CPU compatibility.
- CPU inference, four threads by default, 4,096-token active context. Bonsai requests thinking off. LFM3 uses its reasoning template and parses reasoning separately from the visible answer: disabling thinking can produce stray closing tags with this checkpoint. Canvas reserves up to 2,048 generated tokens; other replies use the chosen response length. No MediaTek NPU or GPU inference is claimed.
- The earlier development app's engine/core features were exercised on CMF Phone 2 Pro (Android 16). That is not a fresh physical-device acceptance pass for the renamed public build and downloader. Other devices remain unverified.
- Local JVM tests cover interrupted-download recovery, ignored Range responses, checksum rejection preserving installed data, and cancellation. Device tests under `app/src/androidTest` require the model assets; inference tests create sample chats and should run on a disposable app profile.
- Short device samples cannot establish sustained thermal performance, general image accuracy or universally smooth rendering.

## Public releases

Release APKs use a separate persistent signing key kept outside this repository. No signing key or password is committed. Build unsigned with `:app:assembleRelease`, align with `zipalign -P 16`, then sign and verify with Android's `apksigner` using the private key. Preserve the key for compatible future updates.

Local Gradle checks validate the build and JVM tests. Physical-device testing is separate: passing build checks does not establish on-device inference or download behavior. Keep test model files and private signing material outside version control.
