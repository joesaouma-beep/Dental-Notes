# Dental Notes — Android

The chairside app. See the [repository README](../README.md) for how the
learning engine works; this covers the Android side only.

## Build

```
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
```

Needs the Android SDK (compileSdk 35) and JDK 17. minSdk 26.

This is a separate Gradle build from the desktop one so that the desktop side
can be built without the Android SDK. It compiles `../shared/src/main/kotlin`
directly into the app, so the learning engine is the same code on both.

Unit tests for that engine live in `shared/` and `desktop-core/` and run from
the repository root:

```
cd .. && ./gradlew :shared:test :desktop-core:test
```

## Screens

- **Notes** — recent notes, weekly count, live view of what has been learned.
- **Dictate** — template picker, continuous dictation with a live waveform and
  editable transcript, or type instead.
- **Note** — the rendered note, transcript, and an insights tab showing what was
  applied and how far your version diverged. Editing here is what teaches it.
- **Adapt** — every learned preference with its evidence, individually
  switchable and deletable, plus your measured writing profile.
- **Settings** — clinician and practice, Claude API key and model, tooth
  notation, offline dictation, and data controls.

## Layout

```
app/src/main/java/com/dentalstudio/notes/
  data/db/      Room entities, DAOs, database
  data/prefs/   settings
  data/repo/    the generate → edit → learn cycle
  speech/       continuous dictation over SpeechRecognizer
  ui/           Compose screens, theme, navigation
```

Everything else — the learning engine, dental templates, note generation — comes
from `../shared/`.
