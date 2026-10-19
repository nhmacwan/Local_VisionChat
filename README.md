# Local Vision Chat (starter Android project)

A minimal Android Studio starter for a chat-style UI with:
- local `.litertlm` model selection through Android's document picker
- image/file attachment picker
- camera photo capture
- on-device inference through Google's LiteRT-LM Kotlin API

## Important limitations
- This is a **source project**, not a signed APK. Build it with Android Studio.
- The code is a starter template and may need small adjustments for the exact LiteRT-LM release/API version installed by Gradle.
- The selected model must be a compatible `.litertlm` bundle. Start with the image-enabled bundle from:
  https://huggingface.co/litert-community/SmolVLM2-500M
- SmolVLM2-500M's published Android bundle is primarily documented for single-image question answering, not native video input.
- The current attachment picker is for image/text files; arbitrary PDFs, video files, and Office documents are not directly understood by this model.
- Camera uses Android's system camera UI; this app does not require broad camera/storage permissions.
- To avoid repeated loading cost, a production version should retain the Engine and Conversation between messages and properly handle lifecycle/configuration changes.

## Build
1. Install Android Studio (stable) and Android SDK Platform 35.
2. Unzip this project.
3. Open the `LocalVisionChat` folder in Android Studio.
4. Let Gradle sync and download dependencies.
5. Connect the Android phone with Developer Options + USB debugging enabled, or use an emulator.
6. Select Run.

## First run
1. Download the `SmolVLM2-500M.litertlm` bundle from the model repository linked above.
2. In the app, tap **Choose model** and select that `.litertlm` file.
3. Tap **Camera** or **Attach file/photo**, then enter a question and tap Send.
4. If model loading fails, try the official Google AI Edge Gallery app first to verify the bundle works on the handset.

## Model
SmolVLM2-500M LiteRT-LM bundle (~361 MB):
https://huggingface.co/litert-community/SmolVLM2-500M
