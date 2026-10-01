#!/usr/bin/env bash
# Standalone DB verification when the Minecraft/Loom build cannot run.
set -euo pipefail
if [ "$#" -ne 3 ]; then
  echo 'Usage: JAVA_HOME=<Java21> bash scripts/run-db-tests.sh <junit-console.jar> <sqlite-jdbc.jar> <slf4j-api.jar>' >&2
  exit 2
fi
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
for dependency in "$@"; do test -f "$dependency"; done
qa_classes="$(mktemp -d)"
trap 'rm -rf "$qa_classes"' EXIT
qa_classpath="$1:$2:$3"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
"$javac_bin" --release 21 -cp "$qa_classpath" -d "$qa_classes" \
  "$repo_dir/core/src/main/java/org/peridoll/mcodyssey/core/Database.java" \
  "$repo_dir/core/src/main/java/org/peridoll/mcodyssey/core/Residents.java" \
  "$repo_dir/eco/src/main/java/org/peridoll/mcodyssey/eco/Economy.java" \
  "$repo_dir/core/src/test/java/org/peridoll/mcodyssey/core/DatabaseTest.java" \
  "$repo_dir/eco/src/test/java/org/peridoll/mcodyssey/eco/EconomyValidationTest.java" \
  "$repo_dir/eco/src/test/java/org/peridoll/mcodyssey/eco/CoreEcoIntegrationTest.java" \
  "$repo_dir/eco/src/test/java/org/peridoll/mcodyssey/eco/EconomyTest.java"
"$java_bin" -jar "$1" execute --class-path "$qa_classes:$2:$3" --scan-class-path \
  --reports-dir "$repo_dir/build/db-test-results" --disable-ansi-colors
