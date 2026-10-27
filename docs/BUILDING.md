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

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

The public app uses application ID `io.github.eruditecoder108.ember`. The internal Kotlin/JNI namespace remains `dev.bonsai.pocket`; this does not select a model or change the displayed app name. It is a separate application from the personal development build.

## Install and models

Use `./install.ps1 -Serial YOUR_DEVICE_SERIAL` to install a local debug build. Alternatively install the published, signed release APK. The two signatures differ, so switching between debug and public-release installations may require uninstalling the existing copy, which deletes local app data.

The model picker downloads language and image files directly into private app storage. It uses publisher revisions pinned in `Models.kt`, exact lengths and SHA-256 checks. An interrupted transfer is retained as a `.part` file and can resume. A bad download never activates over an installed model. Leaving the app pauses a download; no background transfer service is included.

The installer also accepts `-ModelFile`, `-Model2BFile`, `-Vision2BFile` and `-Vision4BFile` for explicitly provisioning exact supported files. Do not commit weights or private app data.

## Compatibility and validation

- ARM64 only. Native CPU flags target ARMv8.2 with dot-product support; Android version alone does not establish CPU compatibility.
- CPU inference, four threads by default, 4,096-token active context, thinking off. No MediaTek NPU or GPU inference is claimed.
- The earlier development app's engine/core features were exercised on CMF Phone 2 Pro (Android 16). That is not a fresh physical-device acceptance pass for the renamed public build and downloader. Other devices remain unverified.
- Local JVM tests cover interrupted-download recovery, ignored Range responses, checksum rejection preserving installed data, and cancellation. Device tests under `app/src/androidTest` require the model assets; inference tests create sample chats and should run on a disposable app profile.
- Short device samples cannot establish sustained thermal performance, general image accuracy or universally smooth rendering.

## Public releases

Release APKs use a separate persistent signing key kept outside this repository. No signing key or password is committed. Build unsigned with `:app:assembleRelease`, align with `zipalign -P 16`, then sign and verify with Android's `apksigner` using the private key. Preserve the key for compatible future updates.

Local Gradle checks validate the build and JVM tests. Physical-device testing is separate: passing build checks does not establish on-device inference or download behavior. Keep test model files and private signing material outside version control.
