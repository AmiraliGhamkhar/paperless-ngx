#!/usr/bin/env bash
# Generates the Gradle wrapper JAR (a binary that is not committed to this repository).
#
# Usage:  ./bootstrap-gradle.sh
# Requires a local Gradle 8.x on PATH, or Android Studio's bundled Gradle:
#   macOS:   /Applications/Android Studio.app/Contents/plugins/gradle/lib/gradle-*/bin/gradle
#   Linux:   ~/.local/share/JetBrains/Toolbox/apps/AndroidStudio/*/plugins/gradle/lib/gradle-*/bin/gradle
#
# After this runs once, use ./gradlew for everything.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo "gradle-wrapper.jar already present."
  exit 0
fi

if command -v gradle >/dev/null 2>&1; then
  echo "Using $(command -v gradle)"
  gradle wrapper --gradle-version 8.9 --distribution-type bin
  echo "Wrapper created. Next: ./gradlew :app:assembleDebug"
  exit 0
fi

cat <<'EOF'
No Gradle found on PATH.

Do one of these:
  1. Install Gradle 8.9, then re-run this script:
       sdk install gradle 8.9        # or brew install gradle, apt install gradle
  2. Open the `vela` folder in Android Studio. It reads
       gradle/wrapper/gradle-wrapper.properties (pinned to 8.9)
     and can provision the wrapper for you (Gradle will offer to use the wrapper distribution).
  3. Download gradle-8.9-bin.zip from https://services.gradle.org/distributions/ and run:
       <gradle-8.9>/bin/gradle wrapper --gradle-version 8.9

Nothing else in the project depends on the wrapper existing.
EOF
exit 1
