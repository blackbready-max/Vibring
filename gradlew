#!/usr/bin/env sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Gradle wrapper jar or installed gradle not found." >&2
    exit 1
fi
