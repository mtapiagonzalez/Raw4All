# Repository Guidelines

## Structure

Raw4All is a standalone Android Java camera prototype. App source is in `src/local/s23probe/`, resources in `res/`, design SVGs in `assets/design/`, and the active manifest at `AndroidManifest.xml`. `checks/` covers frame-rate selection, horizon filtering, RAW10 unpacking, and recording time. `build/` and `dist/` hold generated files and are ignored by Git.

## Build and Validation

Run `./build.ps1` from this directory in PowerShell. The script uses Android SDK Platform 35, Build Tools 36.0.0, Android Studio's JDK, and a local debug keystore. Override locations with `-SdkPath`, `-JavaPath`, and `-KeyStore`. It builds a versioned APK in `build/`, verifies the signature, and writes its SHA-256 hash. `AndroidManifest.xml` defines the version; increase versionCode for each update.

Validate affected features on the actual device: preview proportions, both landscape directions, portrait, format selection, rotation lock during recording, microphone audio, zoom, and Gallery publishing. Hardware camera behavior cannot be established by compilation alone. Low-storage handling must not be tested by filling the owner's phone.

Run `./check.ps1` for the local Java checks; `-JavaPath` overrides the JDK location. Use a fresh checkout or build directory when validating a distributable APK to avoid stale classes.

## Coding Conventions

Use four-space indentation, UpperCamelCase classes, lowerCamelCase methods and fields, and UPPER_SNAKE_CASE constants. Match the existing Camera2 callback lifecycle. Keep resource names lowercase with underscores. There is no configured formatter, lint runner, or coverage threshold.

## Camera Compatibility

The package `com.secure.mathai.raw4all` separates Raw4All from Expert RAW. The tested firmware accepts this name under a broad package-prefix camera policy; arbitrary package names are not validated. Keep a consistent signing key for updates. W uses ID 56; ST uses ID 54. Do not claim compatibility beyond tested firmware. Recording format, orientation, and preview scale must remain fixed while recording. Zoom labels must reflect the camera's actual supported range.

## Commits and Pull Requests

Use short imperative commit subjects. Describe the behavior changed and record validation, device model, firmware, and remaining limits. Include screenshots for UI changes. Do not add Samsung APKs, decompiled sources, personal recordings, keystores, or signing credentials to this repository. Do not invent a distribution license or imply Samsung endorsement.
