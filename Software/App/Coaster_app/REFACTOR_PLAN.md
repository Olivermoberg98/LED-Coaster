# App refactor plan (Stage A)

Status: **approved 2026-10-10**, see §8 for the decisions. B0–B7, C1 and C2 done;
next is C3 (hardware check). Line numbers and
paths below refer to the code before the refactor. Line references are to `refactor/app` at
`e6c6b7b`. Paths are relative to `app/src/main/java/com/example/myemptyapp/`
unless stated. Versions were checked against Maven Central / Google Maven on
2026-10-10.

## 1. Bugs and risks

Severity: **H** = visible malfunction or crash in normal use, **M** = fails
under plausible conditions, **L** = hygiene / latent.

### Functional

| # | Sev | Where | What goes wrong |
|---|---|---|---|
| 1 | H | `GameActivity.kt:139`, `:520-536`; `MainActivity.kt:530-532` | GameActivity lists only coasters the *system* reports as GATT-connected. MainActivity holds at most one connection and closes the previous one on every connect, so the games screen can show **one coaster at most**, unless another app is holding links. Multi-coaster games look unreachable from the app alone. Needs confirming on a phone. |
| 2 | H | `MainActivity.kt:138`, `:500-508` | The discovery receiver is registered in `onCreate` and unregistered in `onStop`. After a trip to GameActivity or the home screen, "New device" silently finds nothing until the activity is recreated. |
| 3 | M | `GameActivity.kt:215-243` | Dropping a coaster onto an *occupied* circle overwrites `ringDeviceMap[pos]` without removing the old device from `assignedDevices` or disconnecting it. The old coaster stays connected and keeps receiving game commands, and `areAllCirclesConnected` (`:364`) compares a size that can now exceed the circle count. |
| 4 | M | `GameActivity.kt:178-179`, `:390`, `:441-479` | Games receive the live `assignedDevices` set. Long-pressing circles or lowering the count during a game shrinks it; `random()` on an empty set throws `NoSuchElementException`, which crashes the app. |
| 5 | M | `GameActivity.kt:396`, `:439`, `:456`, `:490`, `:500` | Only `currentGameRunnable` is cancellable. The nested `postDelayed` lambdas are not, so `cancelCurrentGame()` leaves a partly cancelled game running. `onDestroy` does call `removeCallbacksAndMessages(null)`, so nothing outlives the activity. |
| 6 | M | `GameActivity.kt:450-457`, `:483-491` | Writes are fire-and-forget, with a hand-tuned 100 ms gap between "all off" and "light one". If an earlier write to the same coaster is still in flight, the next `writeCharacteristic` returns false and the command is dropped. That is only logged, so the wrong coaster can end up lit, or none. |
| 7 | M | `GameActivity.kt:564-600` | `CoasterDevice.connect` has no retry and no failure reporting. A failed connect (status 133 is common) leaves the circle looking assigned while every write is a no-op. Games also start before service discovery has finished. |
| 8 | M | `MainActivity.kt:535-537` | `createBond()` is called on first connect, but the firmware requires no security. This can raise a pairing prompt, race with `connectGatt`, and leave a bond the coaster doesn't use. |
| 9 | M | `MainActivity.kt:180`, `:549` | Saved devices are looked up by name. Two coasters that share a name (an unchanged `coasterID`) collide. `device.name` can be null for an uncached device, and `putString(address, null)` then *removes* the entry. |
| 10 | M | `BluetoothDeviceAdapter.kt:61` | `deviceList.sortBy` runs inside `onBindViewHolder` with no notify, so rows can show stale or duplicated entries while scrolling. |
| 11 | L | `MainActivity.kt:458` | `isDiscoveryInProgress` is only ever set to `false`, so the guard at `:132` does nothing. `cancelDiscovery()` is never called before connecting, and an active inquiry slows the connection. |
| 12 | L | `MainActivity.kt:483-489` | The "Successfully connected" toast fires for *any* ACL link, headphones included, not just the coaster's GATT connection. |
| 13 | L | `MainActivity.kt:562-565` | The "unlock after Bluetooth" layout is never disabled in the first place (no `enabled=false`) and is never re-locked on disconnect, so the controls work with nothing connected and just log "Characteristic not initialized". |
| 14 | L | `GameActivity.kt:193` | The drag shadow is always the *first* row's icon (`recyclerViewDevices.findViewById`), whichever row was pressed. |
| 15 | L | `GameActivity.kt:247-249` | On a failed drop, every circle, occupied ones included, resets to the inactive background. |

### BLE / platform

| # | Sev | Where | What goes wrong |
|---|---|---|---|
| 16 | M | `MainActivity.kt:459` | Classic discovery (`startDiscovery`) for a BLE-only device: a ~12 s inquiry that can't filter by service UUID, so every nearby phone and speaker shows up. It should be a `BluetoothLeScanner` scan filtered on the coaster service UUID. |
| 17 | M | `MainActivity.kt:373-381`, `:408-416`; `GameActivity.kt:621-635`, `:658-672` | There is no GATT operation queue. Android allows one outstanding operation per `BluetoothGatt`, so rapid checkbox toggles or game bursts are dropped. Package 3 adds descriptor writes and reads, which makes this worse. |
| 18 | L | `MainActivity.kt:374-375`, `:409-410` | MainActivity uses only the deprecated `characteristic.value =` + `writeCharacteristic(c)` pair, which mutates shared characteristic state. `CoasterDevice` branches on API 33 correctly. |
| 19 | L | `GameActivity.kt:623`, `:660` | The API-33 write result (a `BluetoothStatusCodes` value) is compared with `BluetoothGatt.GATT_SUCCESS`. It works only because both are 0 (lint `WrongConstant`). |
| 20 | M | `MainActivity.kt:423-434`, `BluetoothDeviceAdapter.kt:50`, `:85` | Permissions are requested ad hoc with no result handling (no `onRequestPermissionsResult`), so the user has to tap again after granting. The adapter requests permissions from inside `onBindViewHolder`, once per row. The manifest requests `BLUETOOTH`/`BLUETOOTH_ADMIN` without `maxSdkVersion="30"`, and both location permissions, none of which minSdk 31 with `neverForLocation` needs. Lint reports 4 `MissingPermission` errors. |
| 21 | L | `MainActivity.kt:83-113`; `GameActivity.kt:558-559` | `targetCharacteristic` is written on the binder thread and read on the main thread without `@Volatile`. Views are only touched via `runOnUiThread`, which is correct. |
| 22 | L | — | Phones commonly cap concurrent BLE links at about 7. GameActivity offers up to 10 circles. Worth knowing; not something the app can fix. |

### Dead code and build hygiene

- `MainActivity.kt`: `bluetoothSocket` (`:65`), `REQUEST_ENABLE_BT` handling (`:455`, the result is never read), `clearPreviouslyConnectedDevices` (`:582`), the `device_address` intent extra (`:200`, nothing sends it), `catch (IOException)` around APIs that never throw it, unused imports (`BluetoothSocket`, `OutputStream`, `CardView`, `RelativeLayout`, `kotlinx.coroutines.delay`). `previousDeviceAdapter` is never attached to a view; it is only used as a list.
- `GameActivity.kt:560`, `:689-701`: `CoasterDevice.requestBluetoothPermissions` duplicates MainActivity's.
- `libs.versions.toml`: `*-vyourversionhere` aliases and their `"your_version_here"` versions (lines 4-9, 17, 23, 25-27, 33, 35), plus unused `android-colorpickerpreference` (`:3`, `:22`). `settings.gradle.kts:3` adds JitPack to *plugin* repositories for no reason.
- `AndroidManifest.xml:4`: the `package=` attribute is deprecated; AGP warns.
- `BluetoothDeviceAdapter.kt` has no `package` declaration.
- `com.github.QuadFlask:colorpicker:0.0.15` (JitPack) has been unmaintained since about 2019. It works, but JitPack availability is a build risk.
- Lint fails on untouched code: 6 errors, 67 warnings.

## 2. Structural problems

1. **BLE is implemented twice.** `MainActivity` and `CoasterDevice` each hold their own GATT callback, UUIDs, Package 1/2 encoders and permission requests. UUIDs live in three places across app and firmware (two in the app).
2. **Connections belong to activities.** Each activity creates and closes its own GATT. Moving between screens drops connections or creates a second client for the same coaster. Nothing can outlive a screen, which the Phase 4 logger needs.
3. **No state layer.** UI state lives in activity fields (`ringDeviceMap`, `assignedDevices`, `selectedCircleCount`) and in view state (spinner selections). There is no single place to ask "which coasters are connected, and what is their battery?"
4. **Game logic is welded to the activity.** `nattDuellen`/`drinkGame` mix timing, random selection, BLE sends and view updates in one closure chain, so they can't be tested or cancelled cleanly.
5. **The protocol is untestable.** The packet encoders are private methods that also touch `BluetoothGatt` and permissions, so byte-for-byte compatibility can't be locked down with tests.
6. **Discovery, persistence and connection are tangled** in MainActivity. Saved devices are keyed by name in some paths and by address in others.

## 3. Target architecture

Keep a **single `:app` module**. A multi-module split isn't worth it at this size. Proposed packages (root name in §3.4):

```
<root>/
  protocol/   pure Kotlin, no android.* imports, JVM-testable
      CoasterUuids.kt        service + characteristic UUIDs, the one copy in the app
      Packets.kt             encodePackage1(outer, inner), encodePackage2(pattern, rgb)
      Pattern.kt             enum FIXED/PULSE/CHASER/RAINBOW with wire names
      BatteryStatus.kt       data class + ChargerState enum + flags
      BatteryStatusDecoder.kt  ByteArray -> Result (Ok(status) | Rejected(reason))
  ble/
      CoasterBleManager.kt   extends Nordic BleManager: required/optional characteristic
                             lookup, init queue (enable notify, read once), write helpers
      CoasterConnection.kt   one per coaster: StateFlow<ConnectionState>,
                             StateFlow<BatteryStatus?>, send(Package1/2), autoReconnect flag,
                             onNotification hook
      CoasterRepository.kt   app-scoped registry keyed by MAC address: get-or-create
                             connection, connect/disconnect, list of live connections
      CoasterScanner.kt      BluetoothLeScanner with ScanFilter on the service UUID
  data/
      SavedDevicesStore.kt   SharedPreferences "BluetoothDevices", same address->name format
  games/
      Game.kt                interface; games are suspend functions over a list of
                             CoasterController (send pattern/colour), cancelled via Job
      NattDuellen.kt, RandomDrink.kt
  ui/main/   MainActivity, MainViewModel, BluetoothDeviceAdapter
  ui/game/   GameActivity, GameViewModel, DevicesAdapter, circle layout logic
  CoasterApp.kt              Application subclass; creates the repository (manual DI)
```

### 3.1 Where state lives

- **`CoasterRepository`** is created once in `CoasterApp` and reached through
  `(application as CoasterApp).repository`. Manual DI, no Hilt/Koin: one
  object doesn't justify a DI framework. It owns every `CoasterConnection`, so
  both screens (and later the logger) share **one connection object per
  coaster**.
- **ViewModels** (`MainViewModel`, `GameViewModel`) hold screen state as
  `StateFlow` and survive configuration changes, which also lets the
  `configChanges` manifest hack go. Activities collect with
  `repeatOnLifecycle(STARTED)`.
- **Game state** (circle → coaster mapping, running game `Job`) moves into
  `GameViewModel`. Games run in `viewModelScope` and are cancelled when the
  screen finishes, or when a coaster is removed mid-game, which fixes #4 and #5.
- **Connection lifetime.** A connection stays open until something calls
  `disconnect()`, independent of activities. To keep today's behaviour,
  GameActivity still disconnects coasters when they're unassigned or the
  screen closes. The difference: the coaster MainActivity is controlling is now
  the same object, so it is not dropped by accident. Decision needed, see §8 Q3.

### 3.2 BLE layer requirements (all built in Stage B)

- **Queue:** every write, read and descriptor write goes through
  `BleManager`'s request queue. No direct `BluetoothGatt` calls anywhere else.
- **Writes:** `WRITE_TYPE_DEFAULT` (with response), as today, so the firmware
  sees identical traffic. The library picks the API-33 or legacy overload
  internally, which removes #18/#19.
- **Notification hook:** `CoasterConnection.onNotification(uuid) { bytes -> }`
  plus the typed `batteryStatus` flow built on it.
- **Auto-reconnect:** `CoasterConnection.autoReconnect: Boolean`. When on,
  connect uses `useAutoConnect(true)` plus a bounded retry for the initial
  link. When off, behaviour matches today (one attempt, `retry(3, 100)` to
  absorb 133s).
- **Optional characteristic:** the status characteristic is looked up in
  `isRequiredServiceSupported` but is **not required**. If it's missing (older
  firmware), the connection still succeeds and `batteryStatus` stays `null`.
- **Scanning:** `CoasterScanner` replaces classic discovery and filters on the
  service UUID, which fixes #2, #11, #12 and #16. The firmware advertises that
  UUID (`BLEHandler.cpp:69`).
- **No bonding:** drop `createBond()` (#8). The firmware has no security
  requirements.
- **Permissions:** one `ActivityResultContracts.RequestMultiplePermissions`
  flow in MainActivity for `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`. The BLE layer
  checks once and reports "missing permission" as a state instead of prompting
  from deep inside. The manifest drops location, and `BLUETOOTH`/`BLUETOOTH_ADMIN`
  get `maxSdkVersion="30"` (or are removed, since minSdk is 31).

### 3.3 How screens and games sit on top

- **MainActivity → MainViewModel:** scan results (`CoasterScanner`), saved
  devices (`SavedDevicesStore`), the "current" coaster address, and that
  connection's state. Checkbox and colour events call
  `connection.sendPackage1/2`. The status line for Phase 3 hangs off
  `connection.batteryStatus`.
- **GameActivity → GameViewModel:** the device list comes from saved devices
  plus their connection state, not from `getConnectedDevices` (#1). Circles map
  to addresses. Drop, remove and count changes are ViewModel calls, and the
  drag-and-drop view code stays in the activity.
- **Games** see only `List<CoasterController>`, so JVM tests can run them with
  fakes and a `TestDispatcher` (the timing logic becomes testable). "Off" stays
  `FIXED` + `0,0,0`, and each send *suspends until the write completes*, which
  replaces the 100 ms guesses (#6). The timing constants (5-10 s, 3 s, 20-25 s,
  1600→200 ms) stay as they are.

### 3.4 Renaming `com.example.myemptyapp`

Two separate knobs:

| Change | Cost |
|---|---|
| **`namespace` + Kotlin package** (code only) | Free for users: no reinstall, no data loss. A mechanical move of every file and every `R` import. Do it in the same step as the package split above. |
| **`applicationId`** | Android treats it as a *different app*: the old one stays installed alongside, and the new one starts with empty SharedPreferences, so the saved-coaster list is lost (re-scan and re-tap each coaster once). There is no Play Store listing to lose. |

Recommendation: rename both, once, early in Stage B, and uninstall the old app
from the phone. The data loss is a handful of saved coasters. Proposed id:
`com.olivermoberg.ledcoaster` (placeholder; Oliver picks). Also rename
`rootProject.name` and `Theme.MyEmptyApp`.

## 4. UI toolkit: Views + XML or Jetpack Compose

| | Stay on Views | Move to Compose |
|---|---|---|
| Pros | Zero migration cost. The screens are small and work. Drag-and-drop onto circles already works. | Current Android standard, and state-driven UI fits the `StateFlow` ViewModels. The dynamic circle grid (`updateCircleLayout`, about 100 lines of imperative view building) becomes a few composables. Live battery badges, stale greying and tints are trivial to express. Previews make the Phase 3 UI quicker to iterate without hardware. |
| Cons | Every Phase 3 indicator is manual view mutation. Leaves the "outdated" feel Oliver wants gone. | New dependencies (Compose BOM, compiler plugin, activity-compose). Requires the toolchain upgrade (§5.1), which is needed anyway. The colour-wheel library is View-based (wrap it with `AndroidView`, or replace it). Compose drag-and-drop (`dragAndDropSource/Target`) is less familiar. |
| Effort | — | About 1-2 sessions per screen. GameActivity is the larger one because of drag-and-drop. |

Incremental path: Compose and Views coexist per activity. Do Stage B (logic,
BLE, ViewModels) on Views first, so the ViewModels are UI-agnostic. Then, if
Oliver chooses Compose, convert **MainActivity first** (simple form plus status
line), then GameActivity. Build the Phase 3 battery display on whichever
toolkit is chosen, so it isn't written twice.

**Recommendation:** move to Compose, but only after Stage B and before the
Phase 3 display. Not mixed into the BLE refactor. **Oliver decides.**

## 5. Dependencies

### 5.1 Toolchain upgrade (prerequisite, not approved)

The installed Android Studio is **2023.3 (Jellyfish)**, which can't sync
anything past **AGP 8.4**. Current stable: AGP **9.4.1** (8.x line ends at
**8.13.2**), Kotlin **2.4.21**. This matters because:

- `ble-ktx:2.11.0` depends on `kotlin-stdlib:2.2.20` and
  `kotlinx-coroutines-android:1.10.2`. A Kotlin 1.9.0 compiler cannot read 2.2
  metadata, so **ble-ktx cannot be used without upgrading Kotlin to ≥ 2.1**.
- Compose on Kotlin 2.x uses the Compose compiler Gradle plugin, which is also
  Kotlin 2.x.
- Recent androidx lifecycle releases need a newer compileSdk. SDK platforms
  35 and 36 are already installed on this machine.

Proposal: **Oliver updates Android Studio to current stable**, and step B0
moves the project to AGP 8.13.2 + Kotlin 2.4.21 + the matching Gradle wrapper +
compileSdk 36. targetSdk stays 34 unless Oliver wants it raised (raising it
changes runtime behaviour, a separate decision). I'd stop at AGP 8.13.2 rather
than 9.x for now: AGP 9 removes the `kotlin-android` plugin in favour of
built-in Kotlin and changes the DSL, which is churn this refactor doesn't need.
It can be a later one-step bump. Fallback if Studio can't be updated: Nordic
`ble` without `-ktx` works on the current toolchain, with about 30 lines of
hand-written `suspendCancellableCoroutine` wrappers.

### 5.2 Libraries

| Dependency | Why | Version | Status |
|---|---|---|---|
| `no.nordicsemi.android:ble` | Connection layer, request queue, auto-connect, notifications | **2.11.0** (latest stable, 2025-09-11) | **Approved** |
| `no.nordicsemi.android:ble-ktx` | `suspend()` on requests, `asFlow()` on notifications, `stateAsFlow()`. Lets games await each write and makes connection state a flow. **I'd use it.** | 2.11.0 | Needs approval; needs §5.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines for ViewModels, games, flows. Arrives transitively with ble-ktx; I'd declare it explicitly. | 1.10.2 | Needs approval |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` + `lifecycle-runtime-ktx` | ViewModel, `viewModelScope`, `repeatOnLifecycle` | 2.11.0 (latest stable) | Needs approval |
| `androidx.activity:activity-ktx` | `by viewModels()`, `registerForActivityResult` for permissions | latest stable, pinned at B0 | Needs approval |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` (testImplementation) | Virtual time for game and connection tests | matches coroutines | Needs approval |
| Compose: `androidx.compose:compose-bom`, `ui`, `material3`, `ui-tooling-preview`, `activity-compose`, `lifecycle-runtime-compose`, plus the `org.jetbrains.kotlin.plugin.compose` Gradle plugin | Only if Compose is chosen (§4) | BOM **2026.09.00** | Needs approval; later |

No DI framework, no Room/DataStore (SharedPreferences is enough for the saved
list), no mocking library (games and decoder are tested with hand-written fakes).

## 6. Order of work

Each step leaves the app building and behaving as today. After each:
`assembleDebug`, `test`, `lint` (§6.1), then commit.

| Step | Content | Fixes |
|---|---|---|
| **B0** | Toolchain upgrade (§5.1). Remove the dead `libs.versions.toml` aliases and the JitPack plugin repo. Manifest `package=` attribute removed. Generate `app/lint-baseline.xml` from the current 6 errors / 67 warnings. | — |
| **B1** | `protocol/` package: UUIDs, `Pattern`, `encodePackage1/2`. **Golden-byte tests** that freeze today's exact bytes for every pattern, both ring flags, and edge colours (`0,0,0`, `255,255,255`). Both activities call the encoders; transport unchanged. | locks byte-for-byte |
| **B2** | Add Nordic `ble` (+ktx if approved), coroutines, lifecycle. `CoasterBleManager`, `CoasterConnection`, `CoasterRepository`, `CoasterApp`. Replace `CoasterDevice` in GameActivity with repository connections. | #7, #17, #19, #21 |
| **B3** | MainActivity onto the repository. `CoasterScanner` replaces classic discovery. `SavedDevicesStore` keyed by address. Permission flow via Activity Result API, and the manifest cleaned. No `createBond`. GameActivity lists saved devices plus connection state. | #1, #2, #8-#13, #16, #18, #20 |
| **B4** | `MainViewModel`, `GameViewModel`; drop `configChanges`. | — |
| **B5** | Games as cancellable coroutines over `CoasterController`, with virtual-time tests. Drop-on-occupied-circle fix. Drag shadow from the pressed row. | #3-#6, #14, #15 |
| **B6** | Package/namespace rename and package split (+ `applicationId` if approved). | — |
| **B7** | Update both `CLAUDE.md` files (app file; root "Android app architecture" section). Lint baseline shrunk to what remains. | — |
| **C1** | `BatteryStatus`, `ChargerState`, decoder, and JVM tests: valid packet, bad checksum, short packet, long packet, wrong type, unknown version, every charger state 0-6 plus an out-of-range one, `0xFF` percent → unknown, flag bits, u16/u32 little-endian including values ≥ 0x8000 / 0x80000000 (signed-byte traps). | — |
| **C2** | `CoasterBleManager`: optional status characteristic; the init queue enables notifications, **then** reads once; both feed the decoder; rejected packets are logged with reason; `CoasterConnection.batteryStatus: StateFlow<BatteryStatus?>`, `null` until the first good packet, reset to `null` on disconnect. "Low" is only `flags bit 0`. | — |
| **C3** | Verify against the firmware's `battery-fake` build: every state seen, voltage falls, fake flag set; and against current firmware without the fake flag. | — |

Then, after toolkit decision: optional Compose migration, followed by Phase 3
display and Phase 4 logger (§7).

### 6.1 Lint policy

`lint` fails on today's code. Proposal: B0 records a baseline so `lint` passes
with "no *new* issues", and each step removes the entries it fixes. The 6
current errors all disappear with B2/B3.

## 7. Designed for, not built yet

**Phase 3 display.**
- `BatteryStatus.receivedAt` is the phone's monotonic time
  (`SystemClock.elapsedRealtime()`, injected as a clock for tests). The UI
  computes `stale = now - receivedAt > 90 s` from a 1 s ticker flow in the
  ViewModel. Not `System.currentTimeMillis`, which jumps.
- Circle: percent text (or `?` for `0xFF`), a charging mark for state 2, a
  warning tint when `flags.low`, greyed out when stale.
- MainActivity status line: `Coaster-05 · 82% · Charging`.
- Low-battery Snackbar: per connection, a `lowWarned` flag set when shown and
  cleared when `flags.low` goes false (or on a new connection). It lives in the
  repository-side connection, not the ViewModel, so a screen change doesn't
  re-fire it.

**Phase 4 run-down logger.**
- `RundownLogger` subscribes to a connection's `batteryStatus` and appends
  every packet (including repeats with the same uptime: each notify is a row) to
  `getExternalFilesDir(null)/rundown-<address>-<start>.csv` with header
  `phone_time,uptime_s,mv,percent,charger_state,flags,pins`. `phone_time` is
  ISO-8601 wall-clock.
- It needs per-packet data that `BatteryStatus` already carries: keep `pins`
  in it, even though the UI ignores them.
- While on, the logger sets `autoReconnect = true` on that connection.
- Multi-hour logging with the screen off needs the process kept alive: a
  **foreground service** (type `connectedDevice`, which needs the
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission at targetSdk 34) that holds
  the repository's logger. No new library. The repository being app-scoped
  (§3.1) is what makes this possible.
- "Hidden toggle": a long-press on the status line, or a debug-only menu item.

## 8. Decisions (2026-10-10)

1. **Toolchain (§5.1):** approved. Oliver updates Android Studio to current
   stable. B0 moves to AGP 8.13.2, Kotlin 2.4.21, the matching Gradle wrapper
   and compileSdk 36. targetSdk stays 34. No AGP 9 for now.
2. **Dependencies (§5.2):** all approved: `ble-ktx`, `kotlinx-coroutines-android`,
   lifecycle (`viewmodel-ktx`, `runtime-ktx`), `activity-ktx`,
   `kotlinx-coroutines-test`. Exact versions pinned in `libs.versions.toml` at B0.
3. **Connection lifetime:** coasters **stay connected** when GameActivity
   closes. A coaster disconnects only when it's unassigned from a circle, when
   the user disconnects it, or when the process ends.
4. **GameActivity device list:** every saved coaster with its live connection
   state, and the coaster connects when it's dropped onto a circle.
5. **Rename:** `applicationId` and namespace both become
   `com.olivermoberg.ledcoaster` (B6), along with `rootProject.name` and
   `Theme.MyEmptyApp`. Oliver uninstalls the old app.
6. **Compose:** yes, after Stage B and before the Phase 3 display,
   MainActivity first. Compose dependencies are approved for that step. Not
   mixed into B0–B7.
7. **Lint baseline (§6.1):** approved.

**Order:** B0–B7 as in §6, then C1–C3.

**Firmware dependency (owned by the firmware session):** after
`ADVERTISING_TIMEOUT_MS` the coaster switches to very slow advertising instead
of stopping, so a saved coaster can always be reached. Auto-reconnect and
connect-on-drop rely on this. A connect can take several seconds while the
coaster advertises slowly, so the UI shows a "connecting" state and doesn't
treat a slow connect as a failure too early.
