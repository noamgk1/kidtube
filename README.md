# KidTube

An Android tablet app that plays YouTube **only** from a whitelist a parent approves.
No server. Storage is plain files on the device. The parent's side lives in the same
app behind a password.

The promise the design is built around: *no content the parent did not approve reaches
the screen.* That is enforced structurally rather than by care — see
[The ticket invariant](#the-ticket-invariant).

- Design: [`docs/notes/specs/2026-10-18-kids-youtube-whitelist-design.md`](docs/notes/specs/2026-10-18-kids-youtube-whitelist-design.md)
- Implementation plan: [`docs/notes/plans/2026-10-18-kids-youtube-whitelist.md`](docs/notes/plans/2026-10-18-kids-youtube-whitelist.md)

## Status

| | |
|---|---|
| Unit tests | 313, all passing |
| Instrumented tests | 12, all passing on an Android 15 emulator |
| `assembleDebug` | succeeds |

### The verification gate has run, and the answer is the adverse one

**YouTube's 360p cap on *Made for Kids* content holds even with a real proof-of-origin
token.** Measured on 18 October 2026 against NewPipeExtractor v0.26.5: a genuine
BotGuard-minted token was served to the client that asks for it, and kids content still
returned a single muxed 360p stream, while the control video returned full adaptive
2160p in the same run.

So the expensive half of the engine choice bought nothing on this library version, and
there is a real decision waiting — accept 360p, or bring the official-API engine
forward. **Read [`docs/notes/notes/gate-result.md`](docs/notes/notes/gate-result.md)**;
it has the raw output, the mistake the first run nearly led us into, and the three
options.

```bash
# connectedDebugAndroidTest has no --tests option; filter with the runner argument.
./gradlew :engine-extractor:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=com.noam.kidtube.extractor.PoTokenGateTest

# Everything, with the network suite opted in.
./gradlew :app:connectedDebugAndroidTest
./gradlew :engine-extractor:connectedDebugAndroidTest -PnetworkTest=true
```

The gate and the canary print their findings with `println`, which on Android goes to
logcat rather than to Gradle's console:

```bash
adb logcat -d -s System.out | grep -E "GATE/|CANARY/"
```

## Installing it

```bash
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk  (~1.8 MB)
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Install the release build, not the debug one.** A debug APK is `debuggable`, and on a
debuggable app anyone with adb can `run-as` into its private files — the whitelist, the
settings, the password hash. That is precisely the promise this app makes to a parent,
so for anything beyond a quick look on your own machine, use release.

The release build is signed with the local **debug keystore** so it installs without
setting up a key. That key is public and well known, so an APK signed with it must never
be handed around as if it were trusted. To sign with a real key, put this in
`local.properties`:

```properties
sideload.keystore=C:\path\to\your.jks
sideload.storePassword=...
sideload.keyAlias=...
sideload.keyPassword=...
```

### First run

The home screen is empty, because nothing is approved yet. **Press הורים in the top
bar** to reach the parent screen. The password is `karmon1234` until you change it,
which you can do under **הגדרות** — and should, before handing the tablet over.

Two ways to approve content, both on the parent screen:

- **חיפוש** searches YouTube and lets you tick channels and videos to allow. This is the
  only place in the app that queries YouTube for something not yet approved, which is
  why it sits behind the password.
- **תכנים** takes a pasted channel or video link, for when you already have one.

Approving a **channel** also allows whatever it uploads in future; approving a **video**
allows that video and nothing else from its channel.

## Building

Needs JDK 17 and an Android SDK. `local.properties` must point at the SDK:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

```bash
./gradlew test testDebugUnitTest   # every unit test, no device needed
./gradlew assembleDebug            # the APK
```

Note: `JAVA_HOME` on the development machine pointed at a non-existent
`jdk-17.0.2`; the real directories are `jdk-17` and `jdk-20`. Worth fixing.

## Architecture

Four Gradle modules. The boundaries are enforced by the compiler, not by discipline.

| Module | Holds | Android? | Network? |
|---|---|---|---|
| `:core` | domain model, `Policy`, the file stores | no | no |
| `:engine-api` | the two swap points plus scriptable fakes | yes | no |
| `:engine-extractor` | NewPipeExtractor, ExoPlayer, PO tokens | yes | yes |
| `:app` | both interfaces, sync, wiring | yes | no |

`:core` is a plain JVM module, so the rules that carry the product's promise run as
unit tests in milliseconds — and a test asserts that `android.content.Context` is not
even on its classpath.

### The engine seam

The design's central decision was to put the metadata source and the player behind two
interfaces, so the engine can be swapped without touching policy, storage or UI:

```kotlin
interface ContentSource   // resolveChannel, listVideos, latestVideos
interface PlayerEngine    // createController + its own Compose Surface
```

`:engine-extractor` exposes exactly one entry point, `ExtractorEngine.create()`,
returning a bundle of `:core`/`:engine-api` types only. NewPipeExtractor is an
`implementation` dependency there, so if any NewPipeExtractor type leaked into a public
signature, `:app` would fail to compile. It did, once, during implementation — which is
how we know the boundary works.

`app/Wiring.kt` is the only file in `:app` that names the extractor engine.

### The ticket invariant

```
Policy.issue(video)        -> PlaybackTicket     (whitelist, blocklist, budget)
resolver.resolve(ticket)   -> VideoDetails       (extraction needs a ticket)
Policy.confirm(ticket, …)  -> ConfirmedTicket    (age limit, live-ness)
player.start(confirmed)                          (accepts nothing else)
```

Both ticket constructors are `internal` to `:core`, so no UI code can manufacture one.
A bug in the kid's screen cannot leak content, because it has no way to produce a
ticket.

Two phases rather than one because `ageLimit` and live-ness only arrive with the
extracted stream info — a channel listing does not carry them. The design spec asserted
a single-phase check; that gap was found and closed during planning.

### Two-speed catalog sync

Measured on 2026-10-17: deep paging costs 1.2–7.3 seconds per page, and one page took
26 seconds. So the daily path never touches the extractor:

- **RSS** (`latestVideos`) — one HTTP request, newest ~15 entries. Runs on launch and
  every six hours via WorkManager. Carries no duration, hence `VideoRef.durationSeconds`
  is nullable.
- **Deep paging** (`listVideos`) — on add and on explicit rebuild only, in the
  background, with visible progress. A sync interrupted by process death simply
  re-pages; `CatalogStore.merge` de-duplicates by id.

The child's grid reads **only** local files and never awaits the network, so it opens
instantly and still works on a day when the engine is broken.

## Deliberate design choices worth knowing

- **No autoplay, ever.** A video ends and the grid returns. The automatic chain is the
  mechanism that builds the rabbit hole; its absence is the product.
- **Shorts and livestreams are never read.** They are separate channel tabs, so they
  are excluded by design rather than filtered out. A livestream is unreviewable by
  definition — it does not exist until it happens.
- **`ageLimit > 0` is refused even inside an approved channel.**
- **The blocklist is one global set of video ids** and always overrides the whitelist.
  Reachable from two places: a channel's catalog, and inline from the history row — which
  is how a parent discovers what to block in the first place.
- **No password recovery, deliberately.** A back door for a parent is a back door for a
  child. The reset wipes the whitelist, the blocklist and the history with it, which is
  exactly what makes it useless as a child's shortcut. The safety net is an exported
  backup — which carries **no** password, so restoring one cannot lock the parent back
  out of their own app.
- **The budget is debited 15 seconds up front** at playback start. Without that, a child
  who force-kills the app every fourteen seconds would never be charged, because
  reconciliation only lands every fifteen. Charging first inverts the incentive.
- **Budget uses `SystemClock.elapsedRealtime()`**, never the wall clock, so moving the
  device clock during a video buys nothing. A clock moved *backwards* keeps the existing
  consumption rather than wiping it.
- **A session that crosses midnight keeps consuming the day it began.**
- **Audio is chosen by device locale** with a fallback to the original track. One measured
  channel offered **270** dubbed audio tracks; taking the first would hand a child a
  language nobody in the house speaks.
- **Two audiences, two error surfaces.** The child gets a calm card with one way out and
  no jargon; the parent gets the real exception, video id and timestamp on the status
  screen. A child who sees a broken screen goes looking for a way around it.

## One residual risk worth knowing

The app ships with the password already set to `karmon1234`, and a destructive reset
restores that same password rather than clearing it. That closes the hole a first-run
prompt leaves open — on a device where no password has been chosen yet, the admin door
cannot tell a parent from a child, and whoever holds it first would get to choose.

What is left is that **the factory password is in this file**. Anyone who reads it, or
who has used the app before, knows it. It buys a parent the time to change it rather
than security on its own, so the parent screen says so until you do. **Change it under
הגדרות before handing the tablet over.** The way in is a plain button: what guards the
parent screens is the password behind it, not the button being hard to find — which it
was, for the parent as much as for the child. The only reset is destructive.

## Licence

**GPLv3** — see [`LICENSE`](LICENSE).

This is not a preference. `NewPipeExtractor` is GPLv3 with no linking exception, so any
APK containing `:engine-extractor` is a combined work that must be GPLv3, and its source
must be available. Personal use involves no distribution and so no obligation; sending
the APK to anyone does.

Separately, the extractor engine works against YouTube's internal API, which is contrary
to YouTube's terms of service and rules out Google Play regardless of licensing.

The module boundary pays an unplanned dividend here: `:engine-extractor` is the only
GPL-contaminated module, so a future official-API engine could ship from the same
`:core` and `:app` without it.
