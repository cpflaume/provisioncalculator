#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  gradle wrapper --gradle-version 9.5.0 --distribution-type bin >/dev/null
fi

./gradlew --no-daemon dependencies -q >/dev/null 2>&1 || true
./gradlew --no-daemon testClasses -q
