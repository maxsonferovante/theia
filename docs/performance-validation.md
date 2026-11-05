# Performance and Reliability Validation

This checklist defines the device measurements for ticket #12. Passing a compile or simulator build does not count as a camera performance result.

## Acceptance targets

- Live preview and recipe rendering: sustain at least 30 frames per second on a representative mid-range device.
- Camera interactions: permission recovery, focus, zoom, exposure, and shutter response remain usable while a capture is being saved.
- Video: default to 1920×1080 at 30 fps for up to 60 seconds; expose UHD only when the camera and encoder report support, and fall back to 1080p if UHD effects cannot bind at 30 fps. Select a supported lower quality when the device cannot sustain 1080p/30.
- Photo development: keep image decoding and rendering off the UI thread; preserve the original gallery item if development fails.
- Local album: decode thumbnails at display resolution, keep search and grouping responsive, and avoid loading full-resolution assets into the scrolling list.
- Lifecycle: rotate or background the app, deny permissions, exhaust or interrupt storage, and reopen the app without losing persisted roll metadata or original files.

## Android procedure

1. Build a release-like APK and install it on a representative physical device.
2. Use Android Studio System Trace or Perfetto while previewing, switching lenses, adjusting zoom/exposure, taking stills, developing a roll, and recording video.
3. Record preview frame intervals, dropped frames, memory peak, capture-to-gallery completion time, and video output dimensions/frame rate.
4. Repeat with the lowest supported device tier and with reduced available storage.
5. Attach device model, OS build, app commit, and trace summary to the performance issue.

## iOS procedure

1. Install a release-like build on a representative physical iPhone.
2. Use Instruments Core Animation and Allocations while previewing, focusing, zooming, developing, and recording.
3. Record frame intervals, dropped frames, memory peak, capture-to-Photos completion time, and video output dimensions/frame rate.
4. Repeat after denying and restoring camera, microphone, photo, and optional location permissions.
5. Attach device model, OS build, app commit, and trace summary to the performance issue.

## Current status

Automated checks cover domain integrity, native compilation, and the Android UI flows listed below. One post-optimization Android preview trace is recorded. The issue stays open until longer and multi-device measurements, iOS hardware measurements, and the remaining recovery scenarios are verified.

## Device validation log

### Baseline smoke check — 2026-11-04

- Device: Motorola Edge 60, Android 16.
- Camera preview ran continuously during a short on-device check. Android BufferQueue logs for the app surface varied from 28.53 to 29.99 fps in the sampled one-second windows; this does not establish a sustained 30 fps pass.
- A 12-second Perfetto trace was captured and analyzed locally with Trace Processor. The `app.theia` window had 352 actual frame-timeline events across 11.918 seconds (29.45 fps); the EGL processing surface recorded 352 swaps across 11.876 seconds (29.56 fps). This trace predates the renderer's frame-buffer and GL-location caching changes below, so it is a baseline, not a post-optimization result.
- A still photo saved to the system gallery and its roll record persisted in Théia after the CameraX MediaStore flow was corrected.
- The synthetic ADB volume-key event opened the system volume panel, so physical-button shutter behavior still needs verification with a real device-button press. iOS capture-event behavior also has only build evidence so far.
- The latest debug APK, including the preview retry/error reporting changes, was installed and launched on the same device on 2026-11-04. This was a launch smoke check, not a full capture or performance rerun.

### Post-optimization validation — 2026-11-05
- Android's EGL preview renderer now caches shader locations and reuses frame buffers and transform matrices. CameraX requests a 30 fps preview range on both photo and video flows.
- Video defaults to 1080p/30. Android exposes UHD only when the selected camera and recorder report UHD support, tries UHD/30, and falls back to the closest supported quality when that session cannot bind. iOS exposes 4K/30 only when the active camera has a matching format, otherwise selects the best supported resolution and frame rate at or below the request; its status reports the applied dimensions and rate. The physical high-resolution paths have not been exercised.
- All 8 Android instrumentation UI tests passed on the Motorola Edge 60 running Android 16. Coverage includes onboarding, large text, the camera denied-permission path, shutter semantics and size, Workbench selection, settings, album search/favorites/deletion, roll development confirmation, and navigation into video audio controls. These tests verify semantics, state, and Compose actions, not TalkBack speech or a still/video capture.
- The app was installed and launched on the same device with camera permission. A 20-second Perfetto trace was captured with the camera preview open. Trace Processor counted 377 EGL swaps across 12.54 seconds (30.06 fps) and 376 camera preview surface frames across 12.52 seconds (30.04 fps). This meets the 30 fps preview target for the sampled interval on this one Android device.
- In the same trace, the app window recorded 376 frames across 12.55 seconds (29.96 fps), with 302 `App Deadline Missed` and 13 `Buffer Stuffing` classifications. The camera preview surface stayed at about 30 fps; investigate whether these window classifications affect control responsiveness during longer interactive capture before closing #12.
- Photo and video MediaStore publication now treats a failed pending-row publish as a save failure and deletes the incomplete media row. A video `SecurityException` without audio is reported as a storage/capture failure rather than a microphone denial.
- `:shared:testAndroidHostTest --rerun-tasks` passed earlier on 2026-11-05. The generic iOS Simulator build succeeded, which confirms compilation only.

The remaining #12 validation includes longer camera runs, a second Android device tier, iOS hardware, video capture/output settings, capture-to-gallery timing, memory peak, interaction latency, storage exhaustion/interruption recovery, and physical-button behavior. TalkBack and VoiceOver walkthroughs and the remaining capture/development accessibility flows also remain open for #10.
