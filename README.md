# DiskTree

DiskTree is an Android storage analyzer for finding large files and folders. It presents scan results as an expandable tree, with each row showing its size and its share of its parent.

## Features

- Scan shared storage without root access.
- Scan the device data partition through a root manager that exposes `su`.
- Sort folders and files by size.
- Expand and collapse folders in a virtualized list.
- Show allocated block size, with logical size as a fallback when block data is unavailable.
- Work with light and dark system themes.
- Keep scan data on the device. The app has no network permission and does not upload file names or paths.

## Storage modes

### Shared storage

Shared storage is the device's external storage directory. On Android 11 and newer, DiskTree requests the special all-files access permission. Older Android versions use `READ_EXTERNAL_STORAGE`.

Android can hide some app-specific folders from standard access. The scan result reports when protected paths were skipped.

### Root device

Selecting Root device checks `su` access first by running `id -u` through the root manager. After access is confirmed, DiskTree runs a local `du` process as root and scans `/data`, the Android user-data partition. The root manager may display a permission prompt when the check or scan starts. DiskTree does not delete or modify files.

## Build with GitHub Actions

The workflow at `.github/workflows/build.yml` runs on pushes and pull requests. It runs:

```text
testDebugUnitTest lintDebug assembleDebug assembleRelease
```

After a successful run, download the `DiskTree-APKs` artifact from the Actions run. It contains the debug APK and the unsigned release APK.

## Build locally

Use JDK 17, Gradle 8.9, and Android SDK 35.

```bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The project does not require a Gradle wrapper because the Actions workflow installs the pinned Gradle version directly.

## Project layout

- `app/src/main/java/com/disktree/app/DiskScanner.kt` parses the native `du -a -k` stream and builds the size-sorted tree.
- `app/src/main/java/com/disktree/app/DiskTreeViewModel.kt` owns scan state, progress, cancellation, and storage capacity data.
- `app/src/main/java/com/disktree/app/DiskTreeScreen.kt` contains the Compose interface and tree rows.
- `app/src/test/java/com/disktree/app/DiskTreeTest.kt` covers parsing, sorting, and tree expansion.

## License

MIT. See `LICENSE`.
