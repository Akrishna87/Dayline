# 📅 Dayline for Android

Dayline is a **calendar app** with what you'd expect from Google Calendar: schedule, day,
3-day, week and month views, repeating events, notifications, time zones, `.ics` import and
export, and widgets. Everything stays on your phone: there are no accounts, no ads and no
internet access.

It is one of three separate apps that work alike:
[**Doable**](https://github.com/Akrishna87/doable) (tasks), **Dayline** (calendar) and
[**Jotter**](https://github.com/Akrishna87/jotter) (notes).

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/dayline/releases/tag/dayline-latest)
   and tap **Dayline.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **Dayline**.

To update, install a newer `Dayline.apk` the same way. It installs over the old one and
keeps everything.

## What it does

- **Views**: **Schedule** (the next three months, day by day), and on the **Calendar** tab
  **Day**, **3 days**, **Week** and **Month**. Swipe sideways or use the arrows to move;
  tap the title to jump to any date; **Today** jumps back. Day, 3-day and week views are a
  time grid with a line for now: tap an empty hour to add an event there, or long-press an
  event and drag it to move it. Settings can show week numbers, hide weekends, and add a
  second time zone beside the hours.
- **New events in plain words**: tap **+** and type the title the way you'd say it.
  Dayline fills in the date and time as you type:

  | Type | For |
  |---|---|
  | `today`, `tomorrow`, `friday`, `next week`, `this weekend`, `in 3 days`, `dec 25`, `2026-12-25` | the day |
  | `9am`, `6:30pm`, `21:00`, `at 5`, `noon`, `tonight` | the start time |
  | `2-3pm`, `9am-11am`, `9am to 11:30am` | the start and end |
  | `every day`, `every weekday`, `every mon, thu`, `every 3 days`, `every 2nd tue`, `every last day`, `yearly` | repeats |
  | `#Work` | an existing calendar |

  For example: `Standup every weekday 9:30am #Work` or `Dentist dec 3 2-3pm`. Changing
  any field by hand takes over from the title.
- **Events**: all-day or with times, over several days, in their own **time zone**, with a
  place, notes, guests, a colour of their own, and **busy/free**. **Duplicate** an event,
  or **email an invitation** to its guests with an `.ics` file attached.
- **Repeats** like Google Calendar's: daily, weekly on chosen days, monthly on a date, on
  the last day or on the 2nd Tuesday, yearly, every N of any of these, ending never, on a
  date, or after a number of times. Changing or deleting one occurrence asks whether it's
  for **this event**, **this and following events**, or **all events**, with **Undo**.
- **Notifications**: as many as you like per event, from at the start to a week before;
  all-day events notify on the day or days before at a time you choose. Separate defaults
  for timed and all-day events.
- **Calendars**: Personal and Work to start with. Add your own, rename and recolour them,
  tick which ones to show, and **export** any of them as an `.ics` file.
- **Import** `.ics` files (from Google Calendar, Outlook or Apple Calendar): open one with
  Dayline, share it to Dayline, or use **Calendars → Import events**. Importing the same
  file twice doesn't add events twice.
- **Widgets**: a schedule of the coming days, and a month that marks busy days.
- **Search** (titles, places, notes and guests), **light and dark themes**, and **backups**
  (Settings → Export backup / Restore from backup) as a JSON file.

Left out, because they need an account and a server: syncing between devices, sharing
calendars with other people, and guests replying to invitations inside the app.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic. It is plain Kotlin (no Android imports) with
  unit tests, so a web version can reuse it later: `Rule.kt` is the repeat rule (and
  reads and writes RFC 5545 `RRULE`s), `Recurrence.kt` works out when repeating events
  happen, across time zones, and when their notifications go off, `Ics.kt` imports and
  exports `.ics` files, `QuickEvent.kt` reads plain-word titles, and `Dates.kt` has the
  month grid and labels.
- `Store.kt` keeps everything in memory and saves it to one JSON file, `Reminders.kt`
  schedules one alarm per event (for its next notification), `Widgets.kt` draws the home
  screen widgets, `Sharing.kt` sends `.ics` files, and `Backup.kt` exports and restores.
- `ui/` has the screens.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It adds an event in
  plain words, makes it weekly, adds an all-day event, deletes one occurrence, opens the
  week view, adds a calendar, searches, and switches to the dark theme. Screenshots from that run are
  attached to each release.

Builds are made by the [Dayline APK workflow](.github/workflows/dayline-apk.yml) on
every push to this repo. Doable, Dayline and Jotter are signed with the same sideload key
(`signing/sideload.keystore`), so they can share data with each other later on.
