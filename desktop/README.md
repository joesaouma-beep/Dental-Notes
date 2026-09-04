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

Recognition runs on this machine through [Vosk](https://alphacephei.com/vosk/models).
Download a model, unpack it, and point Settings at the unpacked folder — the one
containing `am/` and `conf/`. Nothing is uploaded. Without a model the transcript
box is an ordinary text field, so Windows dictation (Win+H) types straight into it.

### Which model

| Model | Size | When |
| --- | --- | --- |
| `vosk-model-en-us-0.42-gigaspeech` | ~2.3 GB | **Start here on a desk PC.** Trained on varied real-world speech, so it copes better with non-US accents than the LibriSpeech-weighted models. |
| `vosk-model-en-us-0.22` | ~1.8 GB | Solid alternative if gigaspeech is slow to load or memory is tight. |
| `vosk-model-small-en-us-0.15` | ~40 MB | Testing that the microphone path works at all. Too error-prone for real notes. |

Vosk publishes no Australian English model, so an accent penalty is unavoidable.
It matters less than it sounds: with a Claude API key set, the transcript is not
the note — the model is instructed to correct unambiguous speech-recognition
errors in dental terms while leaving every tooth number, dose and measurement
exactly as dictated. **Without an API key that safety net is gone**, because the
on-device generator only routes sentences to headings and never rewrites clinical
content. Pick the largest model you can if you are running without a key.

Expect dental vocabulary — occlusal, distal, amalgam, gingivitis — to be the
weak point rather than the accent. Read the transcript before generating.
