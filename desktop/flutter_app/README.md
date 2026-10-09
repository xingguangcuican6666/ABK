# ABK Desktop

Cross-platform (Windows + Linux) desktop shell for ABK, built with Material 3
Expressive. The Flutter UI pairs with the `abk_sidecar` Rust process and drives
the phone-side ABK agent over `adb` (see `../../docs/agent-protocol.md`).

## Run (dev)

```bash
# from the repo root
bash desktop/scripts/run-dev.sh
```

Or run just the Flutter shell against a running sidecar:

```bash
cd desktop/flutter_app
flutter run -d linux    # or: -d windows
```

## Package

- Linux AppImage: `bash desktop/scripts/package-linux-appimage.sh`
- Windows bundle: `pwsh desktop/scripts/package-windows-bundle.ps1`

CI builds both in `.github/workflows/build-abk-desktop.yml`.
