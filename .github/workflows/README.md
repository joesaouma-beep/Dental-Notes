# Getting installable builds

Neither build can be produced without its platform toolchain, so both are built
by GitHub Actions.

1. Open the **Actions** tab of this repository.
2. Pick **Build**, then **Run workflow** on the branch you want.
3. When it finishes, download from the run's **Artifacts** section:
   - `dental-notes-apk` — the Android app. Copy the `.apk` to your phone, allow
     installation from your browser or file manager when prompted, and open it.
     It is a debug build, so it installs alongside a Play Store copy rather than
     replacing one.
   - `dental-notes-windows` — the Windows build. Run the `.msi` to install, or
     use the unpacked `app` folder and run `Dental Notes.exe` directly.

To build locally instead:

```
# Android — needs the Android SDK (Android Studio installs it)
cd android && ./gradlew assembleDebug

# Windows desktop — needs JDK 21; run this on Windows for the .msi
./gradlew :desktop:packageMsi
./gradlew :desktop:run        # run it without packaging
```
