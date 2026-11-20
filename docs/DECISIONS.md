# Architectural & Technical Decisions Log

This document records key architectural, engineering, and dependency decisions made during the design and development of **SIPHON** (PRD v2).

---

## 1. Engine Selection and Version Resolution

### Resolved Dependency:
* **Library:** `io.github.junkfood02.youtubedl-android:library:0.17.2`
* **FFmpeg:** `io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2`
* **Aria2c:** `io.github.junkfood02.youtubedl-android:aria2c:0.17.2`
* **Maintainer:** JunkFood02 (developer of Seal)
* **Target Platforms:** Android API 29 through API 34+

### Engine Update Strategy:
* **Decision:** We use the built-in update functionality:
  ```kotlin
  YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
  ```
* **Rationale:** Rather than writing a fragile custom binary scraper and downloader that manually unpacks ELF binaries into app storage, `youtubedl-android` provides a native, officially tested Python wheel / executable updater that fetches directly from the `yt-dlp` release feed with cryptographic hash verification and atomic binary swap.

---

## 2. Pause State Machine & The Core Invariant

### The Invariant:
**"A manual pause always beats automatic rules."**

### State Table:
| State | Cause | Resumes on Wi-Fi? | Resumes on SD Card Return? | Persisted in DB |
|---|---|---|---|---|
| `WAITING` | Link added with "Download later" | Yes (when rules pass) | Yes | `pausedByUser = false` |
| `QUEUED` | Dispatcher scheduled download | Yes | Yes | `pausedByUser = false` |
| `DOWNLOADING` | Process active in FGS | Active | Active | `pausedByUser = false` |
| `PAUSED_BY_USER` | User clicked Pause / Pause All | **NO (NEVER auto-resumes)** | **NO** | `pausedByUser = true` |
| `PAUSED_NO_WIFI` | Usable Wi-Fi disconnected | **YES (when Wi-Fi returns)** | Yes | `pausedByUser = false` |
| `PAUSED_NO_STORAGE` | SD card unmounted or <2x space | Yes (if Wi-Fi OK) | **YES (when SD mounted / space freed)** | `pausedByUser = false` |
| `MOVING` | Verifying and copying to SD tree | In-flight | In-flight | `pausedByUser = false` |
| `COMPLETED` | Verified moved to final SD folder | Done | Done | Terminal |
| `FAILED` | Fatal error or invalid link | No (user retry) | No | Preserved |

### Enforcement:
1. `DownloadItem.pausedByUser: Boolean` is strictly `true` only on user action.
2. The scheduler queries:
   ```sql
   SELECT * FROM download_items 
   WHERE status IN ('WAITING', 'PAUSED_NO_WIFI', 'PAUSED_NO_STORAGE') 
     AND pausedByUser = 0 
   ORDER BY position ASC
   ```
3. Network callbacks, reboot listeners, or storage events strictly filter by `pausedByUser = 0`.

---

## 3. Storage Architecture (SD Card First)

### Removable Volume Verification:
* Never assume `getExternalFilesDirs()[1]` is an SD card. Android devices can have internal emulated secondary volumes.
* We inspect:
  ```kotlin
  val isRemovable = Environment.isExternalStorageRemovable(file)
  val isMounted = Environment.getExternalStorageState(file) == Environment.MEDIA_MOUNTED
  ```
* If no physical removable SD card is detected, Siphon prompts the user with a clear warning: *"No SD card detected. Please insert an SD card or select internal storage."*

### 2x Free Space Guard:
* Direct SAF writes are slow and prone to corruption during network drops.
* Downloads first stage in the SD card app-specific cache directory (`/Android/data/com.lonerxone.siphon/files/staging/`).
* Upon completion, the file is copied into the user's selected SAF target folder and byte length is verified before unlinking the staging copy.
* Therefore, the SD card must have at least **2x the file size free** prior to download start. If `usableSpace < totalBytes * 2`, the download pauses as `PAUSED_NO_STORAGE`.

---

## 4. Startup & Reboot Stuck-Queue Normalization

### Problem:
If the phone is powered off, restarted, or the app process is terminated by the OS while items are in `DOWNLOADING`, `QUEUED`, or `MOVING`, those items would remain stuck in active states indefinitely.

### Solution:
On Application launch (`SiphonApp.onCreate()`) and in `BootReceiver`:
```kotlin
downloadDao.normalizeStuckDownloads()
```
* Sets any item in `DOWNLOADING`, `QUEUED`, or `MOVING` back to `WAITING` (or `PAUSED_NO_WIFI`).
* **Critically leaves any item with `pausedByUser = true` untouched.**

---

## 5. Android 14/15 Foreground Service Resilience

* Service declared with `android:foregroundServiceType="dataSync"` and `FOREGROUND_SERVICE_DATA_SYNC`.
* Android 15 introduces a 6-hour cumulative quota per 24 hours on `dataSync` services. Siphon overrides `Service.onTimeout(int startId, int fgsType)` to cleanly stop the active engine process, persist `.part` / `.aria2` state, detach the foreground notification without throwing a `RemoteServiceException`, and schedule a WorkManager fallback.
