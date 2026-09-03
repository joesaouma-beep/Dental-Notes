# Dental Notes

Dictate a dental treatment note, correct anything that is not how you would have
written it, and the app writes the next one your way.

Two apps — Android for the chair, Windows for the desk — sharing one learning
engine, so a preference you teach on either shows up on both.

| | |
| --- | --- |
| `android/` | Android app (Kotlin, Jetpack Compose, Room) |
| `desktop/` | Windows/macOS/Linux app (Compose Multiplatform) |
| `desktop-core/` | Desktop storage, learning cycle and microphone capture |
| `shared/` | The learning engine, dental templates and note generation |
| `index.html` | The original single-file prototype this grew out of |

## Getting the installable builds

The Android SDK and the Windows installer toolchain are needed to package these,
so both are built by GitHub Actions. Open **Actions → Build → Run workflow**, and
download `dental-notes-apk` and `dental-notes-windows` from the finished run.
Details in [.github/workflows/README.md](.github/workflows/README.md).

Locally:

```
cd android && ./gradlew assembleDebug   # APK, needs the Android SDK
./gradlew :desktop:run                  # run the desktop app, needs JDK 21
./gradlew :desktop:packageMsi           # Windows installer, run this on Windows
./gradlew :shared:test :desktop-core:test
```

## How it learns

Every note keeps two copies: the draft that was generated and the version you
kept. The difference between them is the whole training signal.

1. **Diff** — a word-level LCS diff aligns the draft against your edit and groups
   the differences into change blocks.
2. **Mine** — those blocks become candidate preferences of four kinds:

   | Kind | Learned from | Example |
   | --- | --- | --- |
   | `TERM` | a phrase you swap | write "caries", never "cavity" |
   | `REMOVE` | a phrase you delete every time | "patient tolerated the procedure well" |
   | `ADD` | boilerplate you type in | "post-operative instructions given verbally and in writing" |
   | `SECTION_DROP` | a heading you delete | never emit RADIOGRAPHS for restorations |

3. **Weigh** — each preference carries how often it has been seen and how often
   you overruled it. Keeping a phrase the app expected you to change counts
   against the rule, so a one-off correction fades instead of hardening.
4. **Apply** — at two strengths:
   - **2 sightings**, 55% confidence: described in the prompt, so the model
     writes it your way to begin with.
   - **3 sightings**, 75% confidence: applied to the output directly, so a
     correction you have made three times cannot come back a fourth.

A style profile also tracks your preferred note length, how much you trim from
drafts, bullets versus prose, and heading order, so drafts start at your length
rather than being cut down to it. The three most recent notes you approved go
along as worked examples.

`LearningMerge` in `shared/` decides what each edit changes, and both apps only
apply its plan — the phone and the desktop cannot drift apart.

### What it refuses to learn

Anything that is a fact about one patient rather than a habit of yours: digits,
tooth codes, deciduous letters, spelled-out numbers, doses, measurements and
shades. Changing 26 to 27 for one patient must never rewrite the next patient's
chart. The `clinicalFactsAreNeverGeneralised` test holds this in place.

Safety rules in the prompt — never invent findings, keep every tooth number and
dose exactly as dictated, omit a heading rather than pad it — sit above learned
style and are never overridden by it.

## Note generation

With a Claude API key set, notes go through the Messages API, defaulting to
Sonnet 5. Without a key both apps still work: an on-device generator routes each
dictated sentence to the heading it belongs under using a dental keyword lexicon,
and never rewrites clinical content. You always leave the appointment with a note.

## Dictation

- **Android** — continuous recognition through the system recogniser, restarted
  silently across pauses so you can talk through a whole appointment.
- **Windows** — recognition runs locally through a [Vosk](https://alphacephei.com/vosk/models)
  model you point the app at in Settings; nothing is uploaded. Without a model
  the transcript box is an ordinary text field, so Windows' own dictation
  (Win+H) types straight into it.

## Templates

Exam and check-up, scale and clean, restoration, root canal, extraction, crown
and bridge, emergency, paediatric, ortho review, implant, dentures, free form.
Each has its own headings and clinical focus, and preferences scoped to one
template do not leak into the others.

## Privacy

Notes, transcripts and learned preferences stay on the device. Android excludes
them from cloud backup and device transfer; the desktop app keeps them in
`%LOCALAPPDATA%\DentalNotes`. Dictation text is sent to Anthropic only when an
API key is configured, and nowhere else.

Use a patient reference rather than a full name, and follow your own
jurisdiction's record-keeping and privacy obligations. This is a documentation
aid; the clinician remains responsible for the record.
