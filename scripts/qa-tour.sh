#!/usr/bin/env bash
# Screenshot and keyboard tour of the debug build on a running emulator or device.
#
#   scripts/qa-tour.sh [fixture-dir] [output-dir]
#
# Pushes a fresh copy of the fixture library (pvorg.json, VID/, VID/img/, GAL/)
# into the app's private files folder, launches the debug build with the
# debug-only QA extras, and serves the app's requests (logged under the
# PVORG_TOUR tag): screenshots, injected key presses and typed text. Each
# request is acknowledged with a broadcast, so the tour never races the host.
# Exits non-zero when a keyboard check fails or the app crashes.
set -euo pipefail

FIXTURE=${1:-/tmp/pvorg-android-fixture}
OUT=${2:-/tmp/pvorg-android-shots}
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
ADB=${ADB:-$SDK/platform-tools/adb}
PACKAGE=com.pvorg.android
STAGING=/data/local/tmp/pvorg-android-fixture
DEVICE_LIBRARY=/data/data/$PACKAGE/files/library

cd "$(dirname "$0")/.."
[[ -f "$FIXTURE/pvorg.json" && -d "$FIXTURE/VID" && -d "$FIXTURE/GAL" ]] || { echo "No fixture library at $FIXTURE" >&2; exit 2; }
"$ADB" get-state >/dev/null

./gradlew --console=plain -q installDebug

"$ADB" shell am force-stop "$PACKAGE"
# Stage through /data/local/tmp, then copy into the app's private files with
# run-as (debug builds are debuggable), so the app owns every file it rewrites.
"$ADB" shell rm -rf /data/local/tmp/pvorg-android-fixture
"$ADB" push "$FIXTURE" "$STAGING" >/dev/null
"$ADB" shell run-as "$PACKAGE" rm -rf files/library
"$ADB" shell run-as "$PACKAGE" mkdir -p files
"$ADB" shell run-as "$PACKAGE" cp -r "$STAGING" files/library
"$ADB" shell rm -rf /data/local/tmp/pvorg-android-fixture
# adb push skips empty folders (such as an empty gallery); recreate them.
(cd "$FIXTURE" && find . -mindepth 1 -type d -empty) | while IFS= read -r dir; do
  "$ADB" shell run-as "$PACKAGE" mkdir -p "\"files/library/${dir#./}\"" </dev/null
done

rm -rf "$OUT" && mkdir -p "$OUT"
"$ADB" logcat -c
# Read the app's requests through a FIFO so the logcat process can be stopped
# when the tour ends or the script is interrupted.
WORK=$(mktemp -d /tmp/pvorg-android-tour.XXXXXX)
mkfifo "$WORK/requests"
"$ADB" logcat -v raw -s PVORG_TOUR:I </dev/null > "$WORK/requests" &
LOGCAT=$!
trap 'kill $LOGCAT 2>/dev/null; rm -r "$WORK"' EXIT
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity" --es pvorg.qa.library "$DEVICE_LIBRARY" --ez pvorg.qa.tour true >/dev/null

# adb reads stdin; inside the loop below that would be the logcat stream.
ack() { "$ADB" shell am broadcast -a "$PACKAGE.QA_ACK" -p "$PACKAGE" </dev/null >/dev/null; }

key() {
  local spec=$1 keys=()
  [[ $spec == *CTRL+* ]] && keys+=(KEYCODE_CTRL_LEFT)
  [[ $spec == *SHIFT+* ]] && keys+=(KEYCODE_SHIFT_LEFT)
  local code=${spec##*+}
  if ((${#keys[@]})); then "$ADB" shell input keycombination "${keys[@]}" "$code"; else "$ADB" shell input keyevent "$code"; fi </dev/null
}

# Drags the video scrubber from one fraction to another with real touch events
# and screenshots while the finger is still down, showing the frame preview.
scrub() {
  local name=$1 from=$2 to=$3 bounds
  "$ADB" shell uiautomator dump /data/local/tmp/pvorg-ui.xml </dev/null >/dev/null
  bounds=$("$ADB" shell cat /data/local/tmp/pvorg-ui.xml </dev/null | tr '>' '\n' | grep 'content-desc="Playback position"' | grep -oE 'bounds="[^"]*"' | grep -oE '[0-9]+')
  "$ADB" shell rm -f /data/local/tmp/pvorg-ui.xml </dev/null
  if [[ -z $bounds ]]; then echo "  ! scrubber not found for $name"; return; fi
  local left right top bottom
  read -r left top right bottom <<< "$(echo $bounds)"
  local y=$(((top + bottom) / 2))
  local x0 x1
  x0=$(awk -v l="$left" -v r="$right" -v f="$from" 'BEGIN { printf "%d", l + (r - l) * f }')
  x1=$(awk -v l="$left" -v r="$right" -v f="$to" 'BEGIN { printf "%d", l + (r - l) * f }')
  "$ADB" shell input motionevent DOWN "$x0" "$y" </dev/null
  for step in 1 2 3 4 5 6; do
    "$ADB" shell input motionevent MOVE $((x0 + (x1 - x0) * step / 6)) "$y" </dev/null
  done
  sleep 1.2  # the preview frame is decoded asynchronously
  "$ADB" exec-out screencap -p </dev/null > "$OUT/$name.png"
  "$ADB" shell input motionevent UP "$x1" "$y" </dev/null
  shots=$((shots + 1))
}

shots=0 passed=0 failed=0 finished=0
# Each request arrives within seconds; a minute of silence means the tour stalled.
while IFS= read -r -t 60 line; do
  line=${line%$'\r'}
  case $line in
    "shot "*) "$ADB" exec-out screencap -p </dev/null > "$OUT/${line#shot }.png"; shots=$((shots + 1)); ack ;;
    "key "*) key "${line#key }"; ack ;;
    "scrub "*) read -r _ name from to <<< "$line"; scrub "$name" "$from" "$to"; ack ;;
    "text "*) "$ADB" shell input text "${line#text }" </dev/null; ack ;;
    "check PASS "*) passed=$((passed + 1)); echo "  ✓ ${line#check PASS }" ;;
    "check FAIL "*) failed=$((failed + 1)); echo "  ✗ ${line#check FAIL }" ;;
    timeout*) echo "  ! $line" ;;
    done) finished=1; break ;;
  esac
done < "$WORK/requests"
((finished)) || echo "  ! the tour stalled or the app exited before finishing"

crashes=$("$ADB" logcat -d -b crash | grep -c "FATAL EXCEPTION" || true)
anrs=$("$ADB" logcat -d | grep -c "ANR in $PACKAGE" || true)
echo "$shots screenshots in $OUT · $passed checks passed, $failed failed · $crashes crashes, $anrs ANRs"
[[ $finished -eq 1 && $failed -eq 0 && $crashes -eq 0 && $anrs -eq 0 ]]
