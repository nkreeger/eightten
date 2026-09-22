# eightten

A simple Android app for listening to the live Sports Radio 810 WHB stream.

## Features

- One-button Play, loading, and Stop control
- AAC HLS playback with AndroidX Media3
- Background playback through a foreground media service
- Lock-screen and system media controls
- Automatic light and dark themes
- Optional unrestricted battery-use request for more reliable background playback

## Requirements

- Android Studio with the Android SDK installed
- Java 11 or newer
- An Android device or emulator running API 36 or newer

## Build

From the repository root:

```shell
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

With a device connected through ADB:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch, allow notifications so Android can display the foreground
playback notification. For more reliable playback while the app is in the
background, select **Allow background playback** and approve unrestricted
battery use.

## Audio source

The app plays the AmperWave HLS stream documented in
[`docs/audio-stream.md`](docs/audio-stream.md).
