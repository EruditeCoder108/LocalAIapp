plugins { id("com.android.library") }
val nativeBundle=rootProject.file("vendor/phonon-android/phonon2-android-arm64-v8a-0.2.11")
val preparePhonon=tasks.register<Copy>("preparePhonon") {
    from(nativeBundle) { include("libphonon2_enc*.so","libphonon2_tdt*.so") }
    into(layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a"))
    doFirst {check(nativeBundle.resolve("phonon2.h").isFile) {"Run setup.ps1 to fetch the pinned Phonon Android runtime."}}
}
android {
    namespace="dev.bonsai.pocket.phononruntime"
    compileSdk=37
    ndkVersion="28.2.13676358"
    defaultConfig {
        minSdk=28
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared";cppFlags += "-std=c++17" } }
    }
    externalNativeBuild { cmake {path=file("src/main/cpp/CMakeLists.txt");version="3.22.1"} }
}
tasks.named("preBuild") {dependsOn(preparePhonon)}
