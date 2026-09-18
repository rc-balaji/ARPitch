#!/bin/sh
# Self-healing Gradle wrapper launcher. If the standard wrapper JAR is not committed,
# fetch the version-matched official wrapper JAR from Gradle's repository first.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  URL="https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar"
  echo "Gradle wrapper JAR missing; fetching official Gradle 9.6.0 wrapper..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 -o "$JAR" "$URL" || exit 1
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$JAR" "$URL" || exit 1
  else
    echo "Install curl or wget, or run: gradle wrapper --gradle-version 9.6.0" >&2
    exit 1
  fi
fi
JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/}java"
[ -x "$JAVA_EXE" ] || JAVA_EXE=java
exec "$JAVA_EXE" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
