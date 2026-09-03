# Dental Notes — Desktop

Compose Multiplatform build of the same app, for Windows (and macOS and Linux).
See the [repository README](../README.md) for how the learning engine works.

## Run and package

```
./gradlew :desktop:run          # from the repository root, needs JDK 21
./gradlew :desktop:packageMsi   # Windows installer — must run on Windows
./gradlew :desktop:createDistributable   # unpacked app, any platform
```

The `.msi` needs the WiX toolset, which the GitHub Actions Windows runner
already has; **Actions → Build** produces one on every push.

## Layout

- `desktop/` — the Compose UI: sidebar, note pane, dictation, adapt, settings.
- `desktop-core/` — storage, the learning cycle and microphone capture, with no
  Compose dependency so it can be tested headlessly.
- `shared/` — the learning engine, shared with the Android app.

## Storage

Two JSON files rather than a database — the data is small, a clinician can read
or back them up themselves, and there is no native library to ship in the
installer.

```
%LOCALAPPDATA%\DentalNotes\workspace.json   notes, learned preferences, style profile
%LOCALAPPDATA%\DentalNotes\settings.json    clinician details, API key, model
```

Writes go to a temporary file and are then moved into place, so an interrupted
save cannot leave a half-written set of notes. A file that will not parse is
kept as `.corrupt` rather than being overwritten.

## Dictation

Recognition runs locally through [Vosk](https://alphacephei.com/vosk/models).
Download a model, unpack it, and point at the folder in Settings — nothing is
uploaded. Without a model the transcript box is an ordinary text field, so
Windows dictation (Win+H) types straight into it.
