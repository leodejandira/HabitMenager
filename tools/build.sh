#!/usr/bin/env bash
# Local build helper: retries when Maven Central rate-limits downloads.
export ANDROID_HOME=/opt/android-sdk
cd "$(dirname "$0")/.."
log=$(mktemp)
for i in 1 2 3 4 5; do
  ./gradlew "$@" --console=plain -q --max-workers=2 >"$log" 2>&1 && { echo "BUILD OK ($*)"; rm -f "$log"; exit 0; }
  if grep -qE "429|Could not GET|Could not HEAD|Read timed out" "$log"; then echo "network retry $i"; sleep 15; else break; fi
done
grep -E "^e: |^w: .*(error)|error:|What went wrong|FAILED|Exception" -A2 "$log" | grep -v "^Picked up" | head -60
exit 1
