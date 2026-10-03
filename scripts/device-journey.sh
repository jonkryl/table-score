#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r "$(find ci-apks -name 'app-debug.apk' -print -quit)"
adb install -r "$(find ci-apks -name 'app-debug-androidTest.apk' -print -quit)"
adb shell pm clear com.jonkryl.tablescore

run_instrumentation() {
    local report="$1"
    shift
    adb shell am instrument -w -r "$@" com.jonkryl.tablescore.test/androidx.test.runner.AndroidJUnitRunner | tee "$report"
    python3 scripts/check-instrumentation.py "$report"
}

# Repository persistence tests use an isolated directory. The journey saves a real game in the app's files.
run_instrumentation ci-artifacts/01-game-journey.txt -e notClass com.jonkryl.tablescore.RestartAndExportTest
adb shell am force-stop com.jonkryl.tablescore
# A second instrumentation invocation creates a fresh application process and reads the saved game.
run_instrumentation ci-artifacts/02-process-restart-export.txt -e class com.jonkryl.tablescore.RestartAndExportTest
adb pull /sdcard/Android/data/com.jonkryl.tablescore/files/screenshots ci-artifacts/screenshots
adb shell dumpsys package com.jonkryl.tablescore > ci-artifacts/package.txt
adb logcat -d -v threadtime > ci-artifacts/logcat.txt
