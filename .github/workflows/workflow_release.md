# Releases

Every push to `main` creates a release automatically (`release.yml`):

1. Unit tests run (`./gradlew :shared:jvmTest`). If they fail, nothing is released.
2. A GitHub release `v1.0.<run number>` is created with generated release notes.
3. Parallel builds attach the installers to that release:
   - macOS: `.dmg` (Apple Silicon, unsigned: right-click, then "Open" on first start)
   - Windows: `.msi` (per-user install)
   - Linux: `.deb`, `.rpm` and an Arch package (`.pkg.tar.zst`)
   - Android: `.apk`, signed with the sideload key in `androidApp/sideload.keystore`

## Android APK

Download the `.apk` from the release on the phone and open it (allow "install unknown apps" once).
All releases are signed with the same key, so a new APK installs over the old one and keeps the data.
The key is committed on purpose for sideloading only; it must never be used for a store upload.

## Manual run

Actions tab, workflow "Release", "Run workflow".
