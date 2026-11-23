#!/usr/bin/env bash
set -euo pipefail
WORKSPACE=$(cd -- "$(dirname -- "$0")/.." && pwd)
export GRADLE_USER_HOME="$WORKSPACE/.tools/gradle-home"
export ANDROID_HOME="$WORKSPACE/.tools/android-sdk"
export ANDROID_USER_HOME="$WORKSPACE/.tools/user-home/.android"
mkdir -p "$WORKSPACE/.tools/tmp" "$WORKSPACE/.tools/user-home/.android"
if [[ -f "$WORKSPACE/.tools/android-home/debug.keystore" && ! -f "$WORKSPACE/.tools/user-home/.android/debug.keystore" ]]; then
    cp "$WORKSPACE/.tools/android-home/debug.keystore" "$WORKSPACE/.tools/user-home/.android/debug.keystore"
fi
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WORKSPACE/.tools/tmp -Duser.home=$WORKSPACE/.tools/user-home"
cd "$WORKSPACE/CalorieWidget"
exec ./gradlew --no-daemon --console=plain "$@"
