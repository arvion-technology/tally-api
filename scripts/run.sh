#!/usr/bin/env bash
# Compile the API and start it on the JDK HTTP server.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p build/classes
javac --release 17 -d build/classes $(find src/main/java -name '*.java' | sort)
exec java -cp build/classes com.arvion.tally.Main "$@"
