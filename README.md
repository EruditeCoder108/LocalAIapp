<p align="center"><img src="docs/assets/hero.svg" alt="Ember — local AI for Android. Bonsai 2B and 4B." width="100%"></p>

<p align="center">
  <a href="https://github.com/EruditeCoder108/LocalAIapp/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-Android_APK-e53935?style=flat-square"></a>
  <img alt="Android 9 and newer" src="https://img.shields.io/badge/Android-9%2B-202020?style=flat-square">
  <img alt="ARM64" src="https://img.shields.io/badge/Architecture-ARM64-202020?style=flat-square">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/App_license-MIT-202020?style=flat-square"></a>
</p>

<p align="center"><strong>Local chat. Image understanding. A quiet, native interface.</strong><br>Powered by PrismML's Qwen3.5-based Bonsai 2 models.</p>

Ember is a personal Android AI app built with Jetpack Compose and Material 3. Choose a compact model, download it directly from its publisher, and load it when you want to chat. Responses are generated on your device using the CPU.

The interface uses a restrained black, white and red palette, dot-matrix lettering, a conversation drawer and small tactile interactions. It uses static surfaces rather than background blur or continuous decorative animation.

> **Early public release.** The inference engine and core interface were exercised on a CMF Phone 2 Pro during development. The renamed public build and new downloader still need a fresh physical-device acceptance run. Other devices are unverified. See [compatibility and validation](docs/BUILDING.md#compatibility-and-validation).

## Start in a minute

1. Download **[Ember-0.1.0-arm64.apk](https://github.com/EruditeCoder108/LocalAIapp/releases/download/v0.1.0/Ember-0.1.0-arm64.apk)** and install it on a compatible Android phone.
2. Open Ember. Start with **Bonsai 2B**, or tap the model name to select **4B**.
3. Tap **Download**. Keep Ember open; downloads pause when it leaves the foreground. Tap Download again to resume.
4. Once the file has been verified, tap **Load model** and send a message.
5. For photos, unload the model, open the model selector and download its optional **image support**. Then reload and use **+ → Attach image**.

Already have the exact supported files? You can import them through Android's file picker instead. No account or API key is required for local chat.

## Two models, your choice

| | Bonsai 2B | Bonsai 4B |
|---|---|---|
| Role | Lighter starting option | Larger chat option |
| Text download | **517 MB** | **1.15 GB** |
| Optional image support | 365 MB | 367 MB |
| Publisher | [PrismML · 2B](https://huggingface.co/prism-ml/Ternary-Bonsai-2-2B-gguf) | [PrismML · 4B](https://huggingface.co/prism-ml/Ternary-Bonsai-2-4B-gguf) |

Sizes are rounded decimal download sizes, **not RAM requirements**. All four files together occupy about 2.40 GB. Model weights are not bundled in this repository or APK. Downloads use pinned publisher revisions and must match the expected length and SHA-256 before activation.

Only one language model is loaded at a time. Switching unloads the previous model first; stop an ongoing reply before switching. The answering model is recorded with each response.

## What Ember does today

| Feature | How it works |
|---|---|
| **Local conversations** | Streaming replies, saved chat history and a searchable left drawer. Long replies follow the latest text until you scroll up. |
| **Image understanding** | A separate, optional projector processes photos locally. The projector is released before text generation. |
| **Explicit memory** | Save facts in Settings → Memory or type “Remember that …”. Facts persist across chats; retrieval currently uses word matching. |
| **Optional web search** | **+ → Search web** sends that question to DuckDuckGo and gives the local model up to four result snippets. Source links appear beneath the reply. |
| **Real file exports** | Save completed replies as PDF, HTML, Markdown, text, or their first fenced code block using Android's document picker. |
| **Simple controls** | Load/unload, response length, CPU thread count, and system/light/dark appearance. |

Chat, image input, memory and export work offline after the necessary downloads. Web search is explicitly selected per message and requires internet. The free search endpoint can change, rate-limit requests or return a CAPTCHA; Ember reports those failures.

## Storage, memory and privacy

- Conversations, saved facts and copied image attachments are stored in the app's private Android storage.
- No cloud LLM, account system, advertising SDK or analytics SDK is included.
- Model downloads connect to Hugging Face and its file-serving infrastructure. Optional searches send the current question to DuckDuckGo. Opening source links launches an external browser.
- Downloading a model does not load it into RAM. Use **Unload** to release its loaded inference state.
- Leaving the app requests cancellation of generation, search and downloads. Loaded weights may remain cached until Unload, activity disposal or process death; there is no automatic idle-unload timer yet.
- No background inference service, always-listening microphone or boot receiver is included.
- Android backup/device transfer is disabled. Uninstalling removes the app's local data and downloaded models.

## Clear limits

Ember can generate incorrect answers. A source number is not a guarantee that a claim is supported. Image input is resized and token-limited; dense text and small details can be missed. PDF export uses basic text layout.

Semantic memory, automatic recall of old conversations, Python execution, autonomous tools and phone control are **not shipped features**. The active context is 4,096 tokens with thinking disabled, even though the underlying model supports a larger context. See [VISION.md](VISION.md) for future directions.

## Build and contribute

The application uses Kotlin, Compose/Material 3, JNI and a pinned PrismML fork of llama.cpp. The fork is required for the selected PQ2_0 files; replacing it with stock llama.cpp is not supported.

**[Build instructions →](docs/BUILDING.md)** · **[Future direction →](VISION.md)** · **[Third-party notices →](licenses/)** · **[Report an issue →](https://github.com/EruditeCoder108/LocalAIapp/issues)**

Issues are useful when they include the device/Android version, selected model, reproduction steps and a sanitized error message. Please keep personal conversations, photos and credentials out of public reports.

## Credits and license

Created by **[EruditeCoder108](https://github.com/EruditeCoder108)**. Independent project; not affiliated with PrismML, Qwen, Google or Nothing.

Application source: [MIT](LICENSE). Bonsai weights: [Apache 2.0](licenses/model-LICENSE.txt), with [PrismML/Qwen notices](licenses/model-NOTICE.txt). llama.cpp and jsoup: MIT. Geist: SIL Open Font License. The app's abstract logo was generated with AI during development; the README illustration is repository-native SVG artwork.
