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
