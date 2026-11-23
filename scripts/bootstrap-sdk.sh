#!/usr/bin/env bash
set -euo pipefail
WORKSPACE=$(cd -- "$(dirname -- "$0")/.." && pwd)
export ANDROID_HOME="$WORKSPACE/.tools/android-sdk"
export ANDROID_USER_HOME="$WORKSPACE/.tools/user-home/.android"
export GRADLE_USER_HOME="$WORKSPACE/.tools/gradle-home"
mkdir -p "$WORKSPACE/.tools/tmp" "$ANDROID_USER_HOME" "$ANDROID_HOME/cmdline-tools/latest"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$WORKSPACE/.tools/tmp -Duser.home=$WORKSPACE/.tools/user-home"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl --fail --location --retry 3 -o "$WORKSPACE/.tools/cmdline-tools.zip" \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q -o "$WORKSPACE/.tools/cmdline-tools.zip" -d "$WORKSPACE/.tools/sdk-unpack"
    cp -a "$WORKSPACE/.tools/sdk-unpack/cmdline-tools/." "$ANDROID_HOME/cmdline-tools/latest/"
fi
# yes may receive SIGPIPE after sdkmanager exits; use sdkmanager's exit status.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
set -o pipefail
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
    'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$WORKSPACE/CalorieWidget/local.properties"
