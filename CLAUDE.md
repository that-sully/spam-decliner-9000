# Spam Decliner 9000 — Android App

## Project Overview
An Android app that automatically screens and blocks spam/robocalls using the
Android `CallScreeningService` API (Android 10 / API 29+).

The user selects this app as their "Caller ID & Spam" app in system settings.
The app intercepts every incoming call and decides whether to allow, block, or
send to voicemail based on a local spam database and personal block/allowlists.

There is no remote API dependency — all call decisions happen locally and
instantly using the Room database.

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| Language | Kotlin |
| Architecture | MVVM + Repository pattern |
| DI | Hilt 2.51.1 (KSP-based) |
| Local DB | Room 2.7.1 (SQLite) |
| Async | Kotlin Coroutines + Flow |
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose 2.9.0 |
| Networking | Retrofit 2.11.0 + OkHttp 4.12.0 + Moshi 1.15.1 (FTC sync only) |
| Background work | WorkManager 2.10.1 |
| Min SDK | 35 (Android 15) |
| Target SDK | 36 (Android 16) |
| Compile SDK | 36 |
| AGP | 8.9.1 |
| Gradle | 8.11.1 |

---

## Project Structure

```
app/src/main/
├── java/com/example/spam_decliner_9000/
│   ├── SpamBlockerApp.kt              # Application class (@HiltAndroidApp)
│   │                                  # Schedules SpamSyncWorker + ContactsSyncWorker
│   ├── di/
│   │   └── AppModule.kt               # Hilt module — provides DB, DAOs, Repository,
│   │                                  # AppSettings, WorkManager
│   ├── service/
│   │   └── CallScreeningService.kt    # Core: intercepts all incoming calls
│   ├── data/
│   │   ├── SpamRepository.kt          # Single source of truth for all data
│   │   ├── AppSettings.kt             # SharedPreferences wrapper (block unknown toggle)
│   │   ├── db/
│   │   │   ├── SpamDatabase.kt        # Room database (version 2)
│   │   │   ├── SpamNumberDao.kt       # FTC spam list queries
│   │   │   ├── UserListDao.kt         # Personal block/allowlist queries
│   │   │   └── CallLogDao.kt          # Blocked call history queries
│   │   ├── model/
│   │   │   ├── SpamEntry.kt           # FTC spam DB entry
│   │   │   ├── UserListEntry.kt       # Personal block/allowlist entry (+ contactName)
│   │   │   └── BlockedCallEntry.kt    # Blocked call log entry
│   │   └── remote/
│   │       └── SpamApiClient.kt       # Retrofit client for FTC Socrata API (bulk sync only)
│   ├── worker/
│   │   ├── SpamSyncWorker.kt          # Weekly FTC DNC data download → Room
│   │   └── ContactsSyncWorker.kt      # Daily contacts → allowlist sync
│   └── ui/
│       ├── MainActivity.kt            # Single activity (@AndroidEntryPoint)
│       │                              # NavHost + bottom navigation
│       ├── history/
│       │   ├── HistoryViewModel.kt    # @HiltViewModel
│       │   └── HistoryScreen.kt       # Blocked call log with inline actions
│       ├── blocklist/
│       │   ├── BlocklistViewModel.kt  # @HiltViewModel
│       │   └── BlocklistScreen.kt     # Blocklist + Allowlist tabs
│       ├── settings/
│       │   ├── SettingsViewModel.kt   # @HiltViewModel
│       │   └── SettingsScreen.kt      # Toggle, DB stats, sync controls
│       └── theme/
│           ├── Color.kt
│           ├── Theme.kt
│           └── Type.kt
└── AndroidManifest.xml
```

---

## Call Screening Decision Logic

Priority order — first match wins:

| Step | Check | Action |
|---|---|---|
| 1 | Personal allowlist | Allow through |
| 2 | Android "Spam Risk" flag (STIR/SHAKEN `VERIFICATION_STATUS_FAILED` = 2) | Block — no voicemail |
| 3 | Personal blocklist | Block — no voicemail |
| 4 | FTC spam database (local Room lookup) | Block — no voicemail |
| 5 | "Block unknown numbers" toggle ON + not in device contacts | Send to voicemail silently |
| 6 | Default | Allow through |

**Block vs voicemail distinction:**
- Confirmed spam (steps 2–4): `setDisallowCall(true)` + `setRejectCall(false)` — caller gets a dead line, voicemail is untouched.
- Unknown numbers (step 5): `setDisallowCall(true)` + `setRejectCall(true)` — routed to voicemail so the user can review.

---

## Key Architecture Decisions

- **Hilt DI** — `@AndroidEntryPoint` on `MainActivity` and `SpamCallScreeningService`.
  All ViewModels use `@HiltViewModel`. Dependencies are wired in `di/AppModule.kt`.

- **No remote per-call API** — all blocking decisions are made from the local Room DB
  for zero latency. The only network activity is the weekly FTC bulk sync on Wi-Fi.

- **Spam database** — seeded from the FTC Do Not Call complaint dataset via the
  Socrata API (`data.ftc.gov`). Downloaded weekly by `SpamSyncWorker` in 50,000-record
  pages. Numbers stored in E.164 format.

- **Contacts sync** — `ContactsSyncWorker` runs every 24 hours, reading all phone
  numbers and display names from `ContactsContract` and upserting them into the
  allowlist. Uses `OnConflictStrategy.REPLACE` (`upsertContact`) so contact names
  stay current. First sync fires immediately when `READ_CONTACTS` is granted.

- **Number normalization** — all numbers stored and looked up in E.164 format
  (e.g. `+14155551234`). Normalization is handled in `SpamRepository.normalizeNumber()`
  without libphonenumber (hand-rolled for US numbers; extend for international).

- **Single-activity, Compose UI** — `MainActivity` hosts a `NavHost` with three
  bottom-nav destinations: History, Lists, Settings.

- **Room DB version** — currently at version 2. Migration 1→2 adds the `contactName`
  column to the `user_list` table.

---

## Permissions

| Permission | Purpose |
|---|---|
| `READ_CALL_LOG` | Display blocked call history |
| `READ_PHONE_STATE` | Basic phone state access |
| `READ_CONTACTS` | Contacts → allowlist sync + unknown number detection |
| `INTERNET` | FTC dataset download |
| `RECEIVE_BOOT_COMPLETED` | Restart WorkManager jobs after reboot |
| `BIND_SCREENING_SERVICE` | Required on service — allows telecom framework to bind |

`READ_CONTACTS` is a dangerous permission requested at runtime when the user
enables the "Block unknown numbers" toggle in Settings.

---

## Background Workers

### `SpamSyncWorker`
- **Schedule:** Every 7 days, Wi-Fi only, battery not low
- **Source:** FTC Do Not Call complaint dataset via Socrata API
- **Endpoint:** `https://data.ftc.gov/resource/dumd-b9yd.json`
- **Behaviour:** Pages through in 50,000-record chunks until an empty page is returned.
  Numbers stored with `category = "ftc_complaint"`, `confidence = 0.9`.
- **Manual trigger:** "Sync" button on the Settings screen.

### `ContactsSyncWorker`
- **Schedule:** Every 24 hours, no network required
- **Source:** Device contacts via `ContactsContract.CommonDataKinds.Phone`
- **Behaviour:** Reads all phone numbers + display names, upserts into the allowlist.
  Safe to run repeatedly — existing entries are updated, not duplicated.
- **Immediate trigger:** Fires once via `runNow()` as soon as `READ_CONTACTS` is granted.

---

## UI Screens

### History
- Live list of all blocked/voicemailed calls (phone number, reason, timestamp)
- Reason is colour-coded by source: Android Spam Risk, personal blocklist, FTC database, unknown number
- Tap "Actions" on any row to add the number to the blocklist or allowlist

### Lists
- Two tabs: **Blocklist** and **Allowlist**
- Allowlist entries imported from contacts show the contact's display name as the
  headline with the phone number as supporting text
- Delete entries with the trash icon; add manually via the FAB (+ button)

### Settings
- **Block unknown numbers** toggle — enables step 5 of the decision logic; requests
  `READ_CONTACTS` on enable and immediately triggers a contacts sync
- **Contacts** — informational row describing the 24-hour automatic sync
- **FTC complaint entries** — live count of spam numbers in the local DB
- **Sync database now** — triggers an immediate one-off `SpamSyncWorker` run
- **How it works** — plain-English summary of the blocking rules

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug
```

---

## Important Android Constraints

- **Only one CallScreeningService can be active at a time** — the user must
  explicitly select this app in Phone → Settings → Caller ID & Spam.

- **`BIND_SCREENING_SERVICE`** permission on the service declaration is
  mandatory — without it, the telecom framework will not bind to the service.

- **No UI from the service** — `onScreenCall()` must call `respondToCall()` within
  a reasonable time (< 2 seconds). If no response is given, the system allows the call.

- **`VERIFICATION_STATUS_FAILED`** — the constant is defined locally as `2` in
  `CallScreeningService` to avoid resolution issues across SDK versions. The value
  is stable and documented in the Android platform source.

- **OEM quirks** — Samsung One UI and MIUI may override or restrict
  `CallScreeningService`. Test on stock Android (Pixel) first.

- **Block vs voicemail** — `setDisallowCall(true)` + `setRejectCall(false)` hangs
  up with no voicemail. `setRejectCall(true)` routes to voicemail. Do not confuse
  these when modifying the response builders.

---

## Sensitive Areas / Known Gotchas

- Number normalization is hand-rolled for US (+1) numbers only. International
  numbers with country codes other than +1 may not normalize correctly — consider
  adding libphonenumber if international support is needed.
- `ContactsSyncWorker` uses `upsertContact()` (`OnConflictStrategy.REPLACE`) while
  manual adds use `insert()` (`OnConflictStrategy.IGNORE`). Do not swap these —
  using REPLACE for manual adds would silently overwrite user notes.
- The FTC Socrata dataset ID (`dumd-b9yd`) may change if the FTC updates their
  data portal. If the sync starts returning empty results, verify the dataset ID at
  `data.ftc.gov`.
- Room DB is at **version 2**. Any new columns added to existing entities require
  a new migration in `SpamDatabase`. Do not increment the version without adding
  the corresponding `Migration` object.
