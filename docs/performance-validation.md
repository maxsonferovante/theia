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
- All 9 Android instrumentation UI tests passed on the Motorola Edge 60 running Android 16 on 2026-11-05. Coverage includes onboarding, large text, the camera denied-permission path, shutter semantics and size, Workbench selection, settings, album search/favorites/deletion, roll development confirmation, navigation into video audio controls, and recovery when Théia's local album metadata write fails after a video has been saved to the system gallery. The first-launch test revokes camera permission during setup so it does not depend on prior device state. These tests verify semantics, state, and Compose actions, not a still/video capture or spoken TalkBack output.
- Explicit 48 dp minimum targets now cover camera permission and controls, Album/Lab actions, video actions, geotag actions, and onboarding buttons. The full 9-test Android instrumentation suite passed again on the connected phone after the deterministic first-launch setup; this verifies Compose semantics and actions, not spoken TalkBack output.
- The app was installed and launched on the same device with camera permission. The saved Perfetto trace spans 12.913 seconds; its camera-active frame interval spans 12.8 seconds. Trace Processor counted 383 EGL swaps across 12.788 seconds (29.95 fps) and 383 camera preview surface frames across 12.784 seconds (29.96 fps). This is close to, but does not strictly establish, the 30 fps preview target on this one Android device.
- In the same trace, the app window recorded 384 frames across 12.816 seconds (29.96 fps), with 304 `App Deadline Missed` and 15 `Buffer Stuffing` classifications. Frame intervals averaged 33.46 ms and ranged from 12.91 to 53.59 ms. The high missed-deadline count and frame-interval spread leave interaction smoothness unresolved; investigate controls and repeat a correctly timed longer capture before closing #12.
- The active rear camera reported a fixed 30 fps AE range during the trace; the device's legacy preview capabilities list ranges up to 30 fps. The 60 Hz display cadence therefore does not imply that the sensor can produce 60 distinct preview frames per second.
- Android now records the average encoded frame rate measured from the saved video samples in the Théia album metadata; it no longer reports the requested CameraX target as if it were an observed output rate. A short capture on the Motorola Edge 60 measured 30 fps with audio disabled. The temporary test video was deleted from the device after verification.
- Photo and video MediaStore publication now treats a failed pending-row publish as a save failure and deletes the incomplete media row. A video `SecurityException` without audio is reported as a storage/capture failure rather than a microphone denial.
- If a video reaches the system gallery but writing Théia's local album record fails, Android now keeps the gallery file and reports that the album entry could not be saved instead of allowing the callback error to escape.
- `:shared:testAndroidHostTest --rerun-tasks` passed earlier on 2026-11-05. The generic iOS Simulator build succeeded, which confirms compilation only.
- A temporary TalkBack check on the Motorola confirmed that Android bound the TalkBack service and displayed its accessibility-focus outline over Théia content. The check did not verify spoken labels, complete screen-reader navigation, or VoiceOver. TalkBack was disabled and its prior device setting restored afterward.
- The first-launch device flow covers both denied and granted camera states, waiting for the physical camera to initialize before asserting the shutter is enabled. The complete 9-test connected Android instrumentation suite passed on the Motorola Edge 60 running Android 16 on 2026-11-05, including this combined flow.

The remaining #12 validation includes longer camera runs, a second Android device tier, iOS hardware, video capture/output settings, capture-to-gallery timing, memory peak, interaction latency, storage exhaustion/interruption recovery, and physical-button behavior. TalkBack and VoiceOver walkthroughs and the remaining capture/development accessibility flows also remain open for #10.
