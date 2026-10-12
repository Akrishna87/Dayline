# 📅 Dayline for Android

Dayline is a **calendar app**: a month view, an agenda, repeating events, reminders and
calendars you can colour and hide. Everything stays on your phone: there are no accounts,
no ads and no internet access.

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

- **Month**: a grid with a coloured dot for each event, and the picked day's events under
  it. Swipe the grid sideways (or use the arrows) to change month; **Today** jumps back.
- **Agenda**: the next three months, day by day, leaving out empty days.
- **New events in plain words**: tap **+** and type the title the way you'd say it.
  Dayline fills in the date and time as you type:

  | Type | For |
  |---|---|
  | `today`, `tomorrow`, `friday`, `next week`, `this weekend`, `in 3 days`, `dec 25`, `2026-12-25` | the day |
  | `9am`, `6:30pm`, `21:00`, `at 5`, `noon`, `tonight` | the start time (an hour long) |
  | `2-3pm`, `9am-11am`, `9am to 11:30am` | the start and end |
  | `every day`, `every weekday`, `every monday`, `weekly`, `every month`, `yearly` | repeats |
  | `#Work` | an existing calendar |

  For example: `Standup every weekday 9:30am #Work` or `Dentist dec 3 2-3pm`. Changing
  any field by hand takes over from the title.
- **Events**: all-day or with times, over several days, with a place and notes. A
  repeating event can end on a date. Deleting one asks whether to delete **this event**,
  **this and following events**, or **all events**, with **Undo**.
- **Reminders** before an event (at the start, 5 minutes to a day before). All-day
  events remind from 9:00 on the day. Set the default for new events in Settings.
- **Calendars**: Personal and Work to start with. Add your own, rename and recolour
  them, and tick which ones to show.
- **Search**, **light and dark themes**, and **backups** (Settings → Export backup /
  Restore from backup) as a JSON file.

Changes to one occurrence of a repeating event apply to the whole series. To move just one,
delete that occurrence and add it again as its own event.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic. It is plain Kotlin (no Android imports) with
  unit tests, so a web version can reuse it later: `Recurrence.kt` works out when
  repeating events happen and when their reminders go off, `QuickEvent.kt` reads
  plain-word titles, and `Dates.kt` has the month grid and labels.
- `Store.kt` keeps everything in memory and saves it to one JSON file, `Reminders.kt`
  schedules one alarm per event (for its next reminder), and `Backup.kt` exports and
  restores.
- `ui/` has the screens.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It adds an event in
  plain words, makes it weekly, adds an all-day event, deletes one occurrence, adds a
  calendar, searches, and switches to the dark theme. Screenshots from that run are
  attached to each release.

Builds are made by the [Dayline APK workflow](.github/workflows/dayline-apk.yml) on
every push to this repo. Doable, Dayline and Jotter are signed with the same sideload key
(`signing/sideload.keystore`), so they can share data with each other later on.
