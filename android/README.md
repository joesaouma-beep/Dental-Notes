# Dental Notes

An Android app for dictating dental treatment notes that gets better at writing
them the more you correct it.

Dictate the visit at the chair, get a structured treatment note back, edit
anything that is not how you would have written it. The app compares your
version with the one it produced and folds the difference into how it writes the
next one. After a handful of visits the draft arrives already in your wording,
your length and your layout.

## The adaptive loop

Every note keeps two copies: the draft that was generated and the version you
kept. The gap between them is the whole training signal.

1. **Diff** — a word level LCS diff (`learning/TextDiff.kt`) aligns the draft
   against your edit and groups the differences into change blocks.
2. **Mine** — `learning/RuleMiner.kt` turns those blocks into candidate
   preferences of four kinds:

   | Kind | Learned from | Example |
   | --- | --- | --- |
   | `TERM` | a phrase you swap | write "caries", never "cavity" |
   | `REMOVE` | a phrase you delete every time | "patient tolerated the procedure well" |
   | `ADD` | boilerplate you type in | "post-operative instructions given verbally and in writing" |
   | `SECTION_DROP` | a heading you delete | never emit RADIOGRAPHS for restorations |

3. **Weigh** — each preference carries `occurrences` and `contradictions`.
   Keeping a phrase the app expected you to change counts against the rule, so a
   one-off correction fades instead of hardening into a habit.
4. **Apply** — at two strengths:
   - **2 sightings** and 55% confidence: described in the prompt, so the model
     writes it your way to begin with.
   - **3 sightings** and 75% confidence: applied to the output directly by
     `learning/RuleApplier.kt`, so a correction you have made three times can
     never come back a fourth.

Alongside the rules, a style profile tracks your preferred note length, how much
you trim from drafts, bullets versus prose, average sentence length and heading
order. Drafts start at your length rather than being cut down to it.

The three most recent notes you approved are also sent as worked examples, which
carries tone that a list of rules cannot.

### What it deliberately refuses to learn

Anything that is a fact about one patient rather than a habit of yours:
digits, tooth codes, deciduous letters, spelled-out numbers, doses,
measurements and shades. Changing 26 to 27 on one patient must never rewrite the
next patient's chart. `RuleMiner.isLearnableTerm` enforces this, and
`clinicalFactsAreNeverGeneralised` in the test suite holds it in place.

Safety rules in the prompt — never invent findings, keep every tooth number and
dose exactly as dictated, omit a heading rather than pad it — sit above learned
style and are never overridden by it.

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

## Note generation

With a Claude API key set, notes are generated through the Messages API
(`data/generate/AnthropicNoteGenerator.kt`), defaulting to Sonnet 5.

Without a key the app still works: `OnDeviceNoteGenerator` routes each dictated
sentence to the heading it belongs under using a dental keyword lexicon and
never rewrites clinical content. You always leave the appointment with a note.

## Templates

Exam and check-up, scale and clean, restoration, root canal, extraction, crown
and bridge, emergency, paediatric, ortho review, implant, dentures, free form.
Each carries its own heading set and clinical focus, and preferences scoped to a
template do not leak into the others.

## Privacy

Notes, transcripts and learned preferences live in the app's private storage and
are excluded from cloud backup and device transfer. Dictation text is sent to
Anthropic only when an API key is configured. Speech recognition can be forced
on-device in Settings. Nothing is sent anywhere else.

Use a patient reference rather than a full name in the patient field, and follow
your own jurisdiction's record-keeping and privacy obligations. This app is a
documentation aid; the clinician remains responsible for the record.

## Build

```
cd android
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # the learning engine test suite
```

Requires the Android SDK (compileSdk 35) and JDK 17. minSdk 26.

## Layout

```
app/src/main/java/com/dentalstudio/notes/
  learning/     diff, rule mining, confidence, style guide, deterministic apply
  domain/       dental templates and tooth notation
  data/db/      Room entities, DAOs, database
  data/generate/ Claude client, prompt builder, on-device fallback
  data/prefs/   settings
  data/repo/    the generate → edit → learn cycle
  speech/       continuous dictation over SpeechRecognizer
  ui/           Compose screens, theme, navigation
```

The learning engine is pure Kotlin with no Android dependencies, so it is
covered by ordinary JVM unit tests in `app/src/test/`.
