#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

echo "Building…"
gradle :core:test :app:assembleDebug

echo "Installing…"
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Running diagnostics instrumentation test…"
gradle :app:connectedDebugAndroidTest

echo "Copying report…"
adb shell run-as com.example.movieboxexplorer cat files/diagnostics_report.txt | tee diagnostics_report.txt

echo "Report saved to diagnostics_report.txt"
