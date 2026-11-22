#!/bin/sh
##############################################################################
# Gradle start up script for Onegal (POSIX / macOS / Linux)
##############################################################################
set -eu

APP_HOME=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        echo "Downloading gradle-wrapper.jar..." >&2
        mkdir -p "$APP_HOME/gradle/wrapper"
        if command -v curl >/dev/null 2>&1; then
            curl -fsSL "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar" -o "$CLASSPATH"
        elif command -v wget >/dev/null 2>&1; then
            wget -qO "$CLASSPATH" "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
        else
            echo "ERROR: Install Gradle or curl/wget to bootstrap gradle-wrapper.jar." >&2
            exit 1
        fi
    fi
fi

JAVACMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVACMD" -Xmx64m -Xms64m \
    "-Dorg.gradle.appname=$(basename "$0")" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
