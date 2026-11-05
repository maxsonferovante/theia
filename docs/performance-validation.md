# Performance and Reliability Validation

This checklist defines the device measurements for ticket #12. Passing a compile or simulator build does not count as a camera performance result.

## Acceptance targets

- Live preview and recipe rendering: sustain at least 30 frames per second on a representative mid-range device.
- Camera interactions: permission recovery, focus, zoom, exposure, and shutter response remain usable while a capture is being saved.
- Video: prefer 1920×1080 at 30 fps for up to 60 seconds; select a supported lower quality when the device cannot sustain that combination.
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

Automated checks cover domain integrity and native compilation. Physical-device traces and measured frame-rate results must be recorded before the performance issue can be closed.

## Partial device smoke check — 2026-11-04

- Device: Motorola Edge 60, Android 16.
- Camera preview ran continuously during a short on-device check. Android BufferQueue logs for the app surface varied from 28.53 to 29.99 fps in the sampled one-second windows; this does not establish a sustained 30 fps pass.
- A 12-second Perfetto trace was captured, but it has not been analyzed or attached as a reviewed trace.
- A still photo saved to the system gallery and its roll record persisted in Théia after the CameraX MediaStore flow was corrected.
- The synthetic ADB volume-key event opened the system volume panel, so physical-button shutter behavior still needs verification with a real device-button press. iOS capture-event behavior also has only build evidence so far.
- The latest debug APK, including the preview retry/error reporting changes, was installed and launched on the same device on 2026-11-04. This was a launch smoke check, not a full capture or performance rerun.
- `connectedDebugAndroidTest` was attempted on the connected device, but all five Compose UI tests failed before assertions with `IllegalStateException: No compose hierarchies found in the app`. Treat the UI automation result as an unresolved test harness failure, not a passing flow check.

The multi-device performance target, trace analysis, capture interruption/storage recovery, hardware-button verification, and Android Compose UI test harness remain open.
