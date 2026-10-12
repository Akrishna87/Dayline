#!/usr/bin/env bash
# Runs inside the Android emulator job: adds an event in plain words, makes it repeat weekly,
# adds an all-day event, deletes one occurrence, tries the week view, adds a calendar, searches, and switches to the dark theme.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.dayline
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -60 || true
    echo "Tappable:"; python3 - "$OUT/failure.xml" <<'PY' || true
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("clickable") == "true":
        print(" ", n.get("class"), repr(n.get("text")), repr(n.get("content-desc")), n.get("bounds"))
PY
    adb shell wm size || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes and errors from the app:"
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 || { sleep 2; continue; }
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app. Crashes of Dayline itself are caught from logcat.
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
show() { echo "  on screen: $(tr '\n' ' ' < "$OUT/$1.xml" | grep -o 'text="[^"]\+"' | sed 's/^text=//' | head -40 | tr '\n' ' ')"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
try_tap() { # like tap, but carries on if it isn't there
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}" 2> /dev/null) || return 1
  adb shell input tap $xy
}
wait_for() { # wait_for <dump name> <grep -E pattern> <seconds> <what>
  local end=$((SECONDS + $3))
  while [ $SECONDS -lt $end ]; do
    dump "$1"
    grep -Eq "$2" "$OUT/$1.xml" && return 0
    sleep 2
  done
  fail "$4 (waited $3 s)"
}
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c

close_panel() { hide_keyboard; adb shell input keyevent KEYCODE_BACK; sleep 1; }
count() { grep -o "$2" "$OUT/$1.xml" | wc -l; }

echo "--- Launching the app (opens on the month)"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for month 'text="Nothing planned"' 30 "the month view doesn't show an empty day"
grep -q 'content-desc="Next month"' "$OUT/month.xml" || fail "the month view has no Next month button"
shot 1-month
show month
echo "PASS: month view"

echo "--- A new event, typed in plain words"
tap month "New event"
wait_for new 'content-desc="Event title"' 10 "the event editor didn't open"
tap new "Event title"
sleep 1
adb shell input text "Team%slunch%stomorrow%s1pm"
sleep 1
dump new-typed
shot 2-new-event
grep -q 'Understood: Tomorrow' "$OUT/new-typed.xml" || fail "the title's date and time weren't understood"
hide_keyboard
dump new-ready
tap new-ready "Save"
wait_for month-saved 'text="Team lunch"' 10 "the new event isn't on its day"
grep -Eq '1:00 PM – 2:00 PM|13:00 – 14:00' "$OUT/month-saved.xml" || fail "the event isn't at 1pm for an hour"
shot 3-month-event
echo "PASS: plain-words event"

echo "--- Schedule, and making it repeat"
tap month-saved "Schedule" last
wait_for agenda 'text="Team lunch"' 10 "the event isn't in the schedule"
shot 4-agenda
tap agenda "Team lunch"
wait_for edit 'content-desc="Delete event"' 10 "the event didn't open"
tap edit "Does not repeat"
sleep 1
dump repeat-menu
tap repeat-menu "Weekly on"
sleep 1
dump edit-weekly
grep -q 'text="Weekly on ' "$OUT/edit-weekly.xml" || fail "the event didn't become weekly"
shot 5-edit-weekly
tap edit-weekly "Save"
wait_for agenda-weekly 'text="Team lunch"' 10 "back on the schedule, the event is gone"
[ "$(count agenda-weekly 'text="Team lunch"')" -ge 2 ] || fail "a weekly event shows only once in the schedule"
shot 6-agenda-weekly
echo "PASS: repeating events"

echo "--- An all-day event"
tap agenda-weekly "New event"
wait_for allday 'content-desc="Event title"' 10 "the event editor didn't open again"
tap allday "Event title"
sleep 1
adb shell input text "Holiday%sin%s3%sdays"
sleep 1
hide_keyboard
dump allday-typed
tap allday-typed "All day"
sleep 1
dump allday-on
tap allday-on "Save"
wait_for agenda-holiday 'text="Holiday"' 10 "the all-day event isn't in the schedule"
grep -q 'text="All day"' "$OUT/agenda-holiday.xml" || fail "the holiday isn't shown as all day"
shot 7-all-day
echo "PASS: all-day events"

echo "--- Deleting one occurrence"
tap agenda-holiday "Team lunch" last
wait_for delete-open 'content-desc="Delete event"' 10 "the second occurrence didn't open"
tap delete-open "Delete event"
wait_for delete-dialog 'text="This event"' 10 "no choice of what to delete"
shot 8-delete-choice
tap delete-dialog "This event"
sleep 1
dump delete-chosen
tap delete-chosen "OK"
wait_for agenda-deleted 'text="Deleted on' 10 "no message after deleting one occurrence"
echo "PASS: deleting one occurrence"

echo "--- Week view"
dump before-month
tap before-month "Calendar" last
wait_for month-back 'content-desc="View: Month"' 10 "the Calendar tab doesn't show the month"
tap month-back "View: Month"
sleep 1
dump view-menu
tap view-menu "Week"
wait_for week 'content-desc="View: Week"' 10 "the week view didn't open"
grep -q 'content-desc="Previous"' "$OUT/week.xml" || fail "the week view has no Previous button"
shot 9-week
tap week "View: Week"
sleep 1
dump view-menu-2
tap view-menu-2 "Month"
echo "PASS: week view"

echo "--- Next month"
wait_for month-2 'content-desc="Next month"' 10 "the month view didn't come back"
before=$(grep -o 'content-desc="Showing [^"]*"' "$OUT/month-2.xml" | head -1)
tap month-2 "Next month"
sleep 1
dump month-next
after=$(grep -o 'content-desc="Showing [^"]*"' "$OUT/month-next.xml" | head -1)
[ -n "$after" ] && [ "$before" != "$after" ] || fail "Next month didn't change the month ($before → $after)"
shot 9b-next-month
echo "PASS: changing month"

echo "--- Calendars"
tap month-next "Calendars" last
wait_for calendars 'content-desc="Add calendar"' 10 "the Calendars tab has no Add calendar button"
grep -q 'text="Import events"' "$OUT/calendars.xml" || fail "the Calendars tab has no way to import .ics files"
tap calendars "Add calendar"
sleep 2
adb shell input text "Gym"
adb shell input keyevent KEYCODE_ENTER
wait_for calendars-gym 'text="Gym"' 10 "the new calendar isn't listed"
shot 10-calendars
echo "PASS: calendars"

echo "--- Search"
tap calendars-gym "Search"
sleep 2
adb shell input text "lunch"
sleep 1
hide_keyboard
wait_for search 'text="Team lunch"' 10 "search didn't find the event"
shot 11-search
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: search"

echo "--- Dark theme"
dump before-settings
tap before-settings "Settings"
wait_for settings 'Theme: Dark' 10 "Settings has no theme choice"
tap settings "Theme: Dark"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
dump calendars-dark
tap calendars-dark "Schedule" last
sleep 2
shot 12-schedule-dark
echo "PASS: dark theme"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
