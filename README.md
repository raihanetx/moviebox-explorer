# MovieBox Explorer

The Android application source.

**Full documentation:** [`docs/README.md`](docs/README.md)

Quick build:

```bash
export ANDROID_HOME=/path/to/Android/sdk
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The built debug APK is in `dist/app-debug.apk`.