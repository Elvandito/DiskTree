# DiskTree

DiskTree is an Android storage analyzer for finding large files and folders. It presents scan results as an expandable tree, with each row showing its size and its share of its parent.

## Features

- Scan shared storage without root access.
- Scan the device data partition through a root manager that exposes `su`.
- Scan only on demand, with a short cooldown between full scans.
- Sort folders and files by size.
- Expand and collapse folders in a virtualized list.
- Switch between logical file size, which matches file managers, and allocated disk usage.
- Search names inside the scanned tree, with ancestors kept in view.
- Sort by size, name, or path, and switch to a flat list of the largest files.
- See space suggestions derived from the scan, such as files over 500 MB, the largest top-level folder, cache folders, and installed app packages.
- Copy the selected path to the clipboard and open a selected file in its app.
- Export the result as a CSV report in Downloads.
- Work with light and dark system themes.
- Keep scan data on the device. The app has no network permission and does not upload file names or paths.

## Storage modes

### Shared storage

Shared storage is the device's external storage directory. On Android 11 and newer, DiskTree requests the special all-files access permission. Older Android versions use `READ_EXTERNAL_STORAGE`.

On Android 11 and newer, the system hides `Android/data` and `Android/obb` from other apps even with all-files access. DiskTree skips those directories silently, and the result only reports a warning when other paths cannot be read.

### Root device

Selecting Root device checks `su` access first by running `id -u` through the root manager. After access is confirmed, DiskTree runs a local size scan as root and scans `/data`, the Android user-data partition. File size uses logical byte sizes, while Disk usage uses `du -a -k` with kilobyte results converted to bytes. The root manager may display a permission prompt when the check or scan starts.

## Size views

DiskTree defaults to File size, which reports logical bytes and is intended to match file managers. Disk usage reports allocated blocks, which can be much smaller for sparse or compressed files. Changing the view clears the current tree so the next scan uses the selected measurement.

## Build with GitHub Actions

The workflow at `.github/workflows/build.yml` runs on pushes and pull requests. It runs:

```text
testDebugUnitTest lintDebug assembleDebug assembleRelease
```

After a successful run, download the `DiskTree-APKs` artifact from the Actions run. It contains the debug APK and the release APK.

## Release signing

The release APK is signed in CI when these repository secrets are set:

| Secret | Value |
| --- | --- |
| `DISKTREE_KEYSTORE_BASE64` | Base64 of a PKCS12 keystore |
| `DISKTREE_STORE_PASSWORD` | Keystore password |
| `DISKTREE_KEY_ALIAS` | Key alias |
| `DISKTREE_KEY_PASSWORD` | Key password |

Without them the build still succeeds and the release APK stays unsigned. For a local signed build, export `DISKTREE_STORE_FILE`, `DISKTREE_STORE_PASSWORD`, `DISKTREE_KEY_ALIAS`, and `DISKTREE_KEY_PASSWORD` before running Gradle. Keystores are ignored by git through `*.p12`, `*.jks`, and `keystore.properties`.

## Build locally

Use JDK 17, Gradle 8.9, and Android SDK 35.

```bash
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The project does not require a Gradle wrapper because the Actions workflow installs the pinned Gradle version directly.

## Project layout

- `app/src/main/java/com/disktree/app/DiskScanner.kt` parses native `du -a -k` and logical-size streams, then builds the size-sorted tree.
- `app/src/main/java/com/disktree/app/DiskTreeViewModel.kt` owns scan state, progress, cancellation, and storage capacity data.
- `app/src/main/java/com/disktree/app/DiskTreeScreen.kt` contains the Compose interface and tree rows.
- `app/src/test/java/com/disktree/app/DiskTreeTest.kt` covers parsing, sorting, and tree expansion.

## License

MIT. See `LICENSE`.
