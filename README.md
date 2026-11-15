# Local Receiver

**Local Receiver** is a lightweight, offline Android utility app that lets you save files shared from any app directly to a chosen directory on your device.

---

## Why Use Local Receiver?

Many Android apps only expose a **Share** button without a way to save files to a specific local folder. Local Receiver fills that gap as a universal "Save to folder" target in the Android share sheet:

- **Save from share-only apps**: Quickly save attachments, PDFs, images, or exports from messaging, email, or viewer apps that lack a built-in download/export-to-folder option.
- **Move files out of app-private storage**: Copy files shared via `content://` URIs into `Downloads` or any user-accessible folder for easy access by file managers, other apps, or USB transfer.
- **Skip repetitive folder picking**: Set a default destination folder once and batch-save single or multiple shared files in two taps.
- **Keep track of what you saved**: Use the built-in Copy History to quickly reopen received files or delete them from storage later.

---

## How It Works

1. Open any file browser or Android app and tap **Share** on one or more files.
2. Choose **Local Receiver** from the Android share sheet.
3. Review the source file(s) and destination folder in the confirmation dialog (switch folders or reset to default Downloads if needed), then tap **Copy Here**.
4. Monitor live copy progress, cancel if needed, or tap **Open** once the transfer completes.

---

## Features

- **Share Sheet Integration**: Handles both single (`ACTION_SEND`) and multiple (`ACTION_SEND_MULTIPLE`) shared files of any MIME type (`*/*`).
- **Flexible Destination Folder**:
  - Defaults to the system **Downloads** folder (`/sdcard/Download`) via `MediaStore.Downloads`.
  - Supports choosing any custom folder via Android's Storage Access Framework (`OpenDocumentTree`) with persisted read/write permissions.
  - Switch folders or reset to **Default** directly from the main screen or the share confirmation dialog.
- **Foreground Copy Dialog**:
  - Shows file count, current file name, and byte transfer progress (64 KB buffered streaming).
  - Supports cancellation mid-transfer via the Cancel button or Back navigation, cleaning up incomplete destination files.
  - Provides an **Open** button upon completion to launch a viewer for the copied file.
- **On-Device Copy History**:
  - Persists transfer records locally in SQLite (`CopyHistoryDatabase`) with **Success**, **Failed**, and **Cancelled** statuses.
  - Supports opening copied files, deleting copied files from storage, removing individual entries, or clearing all history logs without deleting files.
- **Offline & Privacy-Friendly**: Declares no network or tracking permissions in `AndroidManifest.xml`.

---

## Requirements & Tech Stack

- **Minimum SDK**: Android 10 (API 29)
- **Target / Compile SDK**: API 36
- **Language**: Kotlin (JVM target 17)
- **UI**: AndroidX AppCompat, Material Components, ViewBinding, RecyclerView

---

## Building & Testing

```bash
# Build debug APK (uses applicationId org.liberty.android.localreceiverdebug)
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

---

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.
