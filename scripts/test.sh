#!/usr/bin/env bash
# Compile the API and the custom harness, then run the harness.
# No Maven, no Gradle, no JUnit: javac and java only.
set -euo pipefail
cd "$(dirname "$0")/.."

BUILD="build"
rm -rf "$BUILD/classes" "$BUILD/test-classes"
mkdir -p "$BUILD/classes" "$BUILD/test-classes"

echo "javac release 17: API"
javac --release 17 -d "$BUILD/classes" $(find src/main/java -name '*.java' | sort)

echo "javac release 17: harness"
javac --release 17 -cp "$BUILD/classes" -d "$BUILD/test-classes" $(find test -name '*.java' | sort)

echo "java: TallyTest"
java -cp "$BUILD/classes:$BUILD/test-classes" com.arvion.tally.test.TallyTest
