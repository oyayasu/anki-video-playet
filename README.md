# Anki Video Player — Android

An Android video player designed for studying languages through subtitles. The initial preview is built with **Kotlin**, **Jetpack Compose** and **Media3 ExoPlayer**.

## Preview v0.1

- Play local video via Android's file picker (MP4, MKV, WebM and formats supported by the device/Media3).
- Play **direct** HTTP(S) media URLs including HLS (`.m3u8`) and DASH (`.mpd`) when the host and device allow it. Does not extract videos from web pages, bypass DRM or log in to commercial streaming services.
- Load external SRT, WebVTT and common ASS subtitle files, with word-by-word tap targets in a separate, synchronized subtitle panel.
- Pause when a word is selected, repeat previous/current/next subtitle, change playback speed and subtitle offset.
- Contextual English dictionary: copy a prompt and paste JSON, or supply your **own Gemini API key** to request results automatically. The key is kept in memory for the current session only, not stored or embedded.
- Send a note with the word, original sentence, definitions, IPA and part-of-speech to AnkiDroid using Android's share mechanism. **Manual confirmation in AnkiDroid is required.**

### Install

Open [Releases](https://github.com/oyayasu/anki-video-playet/releases), download the latest `Anki-Video-Player-v*.apk` and install it on Android 8+.

**Important:** preview builds are debug-signed independently by GitHub Actions. A later APK may not install over an older build; uninstalling the previous build may be necessary. There is no persisted learning library in this preview.

### Build

Required: JDK 17, Android SDK API 36 and Gradle 8.11.1.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs the unit tests and assembles the APK on every commit to `main`. Successful builds are published as prereleases.

### Scope and known limitations

- Local and direct media only; generic webpage URLs, YouTube extraction, Netflix and DRM services are **not** supported.
- To tap individual words, load an **external** subtitle file. PlayerView may render embedded subtitle tracks, but those rendered captions are not interactive in this version.
- ASS parsing supports ordinary `Dialogue:` events; unusual custom event layouts and rich ASS effects are not implemented.
- This build shares plain-text cards with AnkiDroid. Automatic creation via AnkiDroid's database API, screenshots and audio clipping are not yet implemented.
- HTTP URLs work when the media server supports compatible codec, range requests and access policies.
- API use may incur provider quota limits; your Gemini key is never put into GitHub or packaged into the APK.

The project intentionally focuses only on the agreed player, subtitle, contextual dictionary and Anki workflow.
