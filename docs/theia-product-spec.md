# Théia Product Specification

## Problem Statement

People who enjoy analog photography want to capture everyday life with visual character and emotion, but traditional filters expose technical controls and offer isolated effects. Digital capture can also feel immediate and disposable. Théia should make equipment selection, capture, and development a tactile, expressive, collectible experience while keeping photos and videos under the user's local control.

## Solution

Théia is a free digital analog photography app for iOS and Android. Each image uses a recipe made of exactly one camera body, one lens, and one film stock. The recipe affects the live preview and final result, travels with the media as metadata when the format allows it, and can be reproduced.

The default experience uses 24- or 36-exposure rolls. Inside Théia, roll images remain inaccessible until development; users may start development at any time. To preserve originals after uninstalling the app, each original is saved to the system gallery at capture time. This makes it accessible to other apps before development. The developed version is also saved to the gallery. Théia's album continues to respect whether a roll is closed.

The app works offline without login, a required server, ads, subscriptions, in-app purchases, or marketing tracking. Domain logic and rules are shared with Kotlin Multiplatform; each platform keeps its own native interface, capture stack, and visual processing.

## User Stories

1. As a photographer, I want the app to open directly to the camera with a default setup so I can take a picture without required configuration.
2. As a new user, I want a short, skippable visual onboarding so I can understand the idea without delaying my first capture.
3. As a photographer, I want to assemble a body, lens, and film stock in the Workbench so I can create a visual combination before taking a picture.
4. As a photographer, I want to see the combination's effect in the live preview so I can assess the image before capture.
5. As a photographer, I want to switch between front and rear cameras so I can photograph a scene or myself.
6. As a photographer, I want to set flash to auto, on, or off so I can adapt capture to available light.
7. As a photographer, I want to adjust supported zoom, focus, and exposure so I can compose the image on my device.
8. As a photographer, I want to lock focus and exposure with a long press so I can preserve the scene reading while recomposing.
9. As a photographer, I want to adjust exposure compensation so I can control brightness without changing the whole recipe.
10. As a photographer, I want to trigger the camera with the hardware button and volume buttons when the system allows it so I can hold the device more steadily.
11. As a photographer, I want to enable a 3x3 grid and horizon level so I can frame precisely.
12. As a photographer, I want to use a 3- or 10-second timer so I can join the picture or avoid camera shake.
13. As a photographer, I want to use the highest resolution supported by the hardware so I can preserve detail in final images.
14. As a photographer, I want to see remaining exposures, mute or hear the shutter, and access the latest photo so I can follow capture without opening another screen.
15. As a user, I want clear messages when the camera, storage, or permissions are unavailable so I can recover the flow without losing access to the rest of the app.
16. As a user, I want to create 24- or 36-exposure rolls and resume them in later sessions so I can take pictures over time.
17. As a user, I want to keep rolls closed until I choose to develop them so I can preserve the anticipation of the analog experience inside Théia.
18. As a user, I want to develop a full roll or start development early so I can control the pace of the experience.
19. As a user, I want a brief animation when a roll is complete so development feels meaningful without interrupting the flow.
20. As a user, I want to enable instant development in settings so each image opens without waiting for the roll.
21. As a user, I want each roll to record its equipment, dates, exposures, and associated photos so I can revisit its history.
22. As a user, I want originals to appear in the system gallery as soon as I capture them so they remain available if I uninstall the app.
23. As a user, I want the developed version saved to the system gallery so I can access, share, and organize the image outside Théia.
24. As a user, I want to browse photos and videos in Théia's local album without internet so I can keep my memories with me.
25. As a user, I want the album grouped by roll, date, film stock, and camera so I can find images by context.
26. As a user, I want to search by film stock, lens, body, or time period so I can find a specific capture.
27. As a user, I want to favorite media and view it full screen so I can highlight important memories.
28. As a user, I want to zoom into photos with pinch and double tap so I can inspect details.
29. As a user, I want to share media and its visual recipe so I can present both the image and the combination that created it.
30. As a user, I want an explicit confirmation that explains the scope when deleting a photo so I do not remove media by mistake.
31. As a user, I want to include Théia's copies and system-gallery copies in a confirmed deletion so I can remove media from both places when I choose.
32. As a user, I want to open the Lab and inspect the body, lens, film stock, roll, date, settings, and authorized geotag so I can understand how the image was created.
33. As a user, I want a compact CaptureRecipe written to metadata when the format allows it so the recipe can travel with exported media.
34. As a user, I want to enable geotagging only by explicit choice and remove it later so I can control the location associated with my images.
35. As a user, I want to consult a fully local map when geotagging is enabled so I can explore photos by place without sending location to servers.
36. As a user, I want to record videos up to 60 seconds with the active visual style so I can capture moving scenes with the same visual language as my photos.
37. As a user, I want to enable audio only when I choose so I can control sound capture and microphone permission.
38. As a user, I want to choose between authored video styles and equipment combinations so I can keep a consistent visual look across photos and videos.
39. As a user, I want video to prioritize 1080p at 30 fps and offer higher resolution when the device can sustain the effects so I can get reliable, smooth results.
40. As a user, I want the recipe to produce consistent previews, photos, and videos so I can trust the result I captured.
41. As a user, I want the app to request camera, photo, microphone, and location permissions only when I use each feature so I can decide with context.
42. As a user, I want denied permissions not to block the rest of the app and want instructions on how to re-enable them so I can keep using available features.
43. As a person with a visual or motor disability, I want screen-reader labels, large touch targets, scalable text, high contrast, and states that do not rely on color alone so I can use the camera and album independently.
44. As someone sensitive to motion, I want the app to respect reduced-motion settings so I can use development effects and transitions comfortably.
45. As a user, I want to use the app in Brazilian Portuguese or English so I can understand capture, permissions, and the catalog in my language.
46. As a user, I want all equipment available from the start so I can try combinations without paid locks or mandatory progression.
47. As a user, I want to choose from at least 30 original film stocks in eight collections so I can find palettes for portraits, city scenes, weather, and memory.
48. As a user, I want to choose from at least six illustrated camera bodies and eight lenses with distinct characteristics so I can assemble a camera that reflects my intent.
49. As a contributor, I want guides for the catalog, architecture, builds, and contributions so I can add items or changes without relying on informal knowledge.
50. As someone concerned about privacy, I want photos, videos, and metadata to stay on the device so I can use the app without sending my memories to servers.

## Implementation Decisions

- Use Kotlin Multiplatform for shared domain entities, catalog data, capture-recipe rules, roll rules, serialization, metadata rules, and local business logic.
- Keep the iOS UI in SwiftUI, capture in AVFoundation, photo processing in Core Image/Metal, and media access in Photos.
- Keep the Android UI in Jetpack Compose, capture in CameraX or Camera2, accelerated preview and processing in a native GPU path, and media access through Android platform APIs.
- Keep capture, image processing, gallery, permissions, and platform lifecycle behind narrow native adapters. Use one high-level use-case boundary as the primary automated test seam.
- Model CameraBody, Lens, FilmStock, FilmRoll, CaptureRecipe, PhotoAsset, VideoAsset, Album, UserPreferences, PermissionState, and GeotagMetadata with stable identifiers and versioned serialization.
- CaptureRecipe contains exactly one body, one lens, and one film stock. Every photo and video belongs to one recipe. Recipes are deterministic and versioned; include the recipe payload in image and video metadata when supported.
- FilmRoll has a capacity of 24 or 36. Each exposure is consumed once. Videos do not consume exposures. Undeveloped items stay hidden in Théia's album until the roll is developed, while original files are saved to the system gallery at capture time.
- Save original captures and developed outputs to the system gallery. Do not include a separate in-app backup or restore feature. The app album records development state and associations while the app is installed.
- Request camera permission only when opening the camera; photo-library access when opening the album or saving; microphone access when enabling video audio; and location access only when geotagging is enabled. Denied permissions must leave the rest of the app usable.
- Geolocation is off by default. Deletion requires explicit confirmation; the confirmation offers deletion of system-gallery copies as a clearly described scope.
- Catalog items and examples are original, fictional, available at first launch, and have no paid locks. Use the supplied 11-color palette in the design system and preserve readable contrast.
- Launch with Brazilian Portuguese and English content. Keep strings localizable.
- Use PolyForm Noncommercial 1.0.0 for code. Commercial use requires separate authorization. The name, logo, and brand assets remain protected and separately governed.
- Do not include accounts, a feed, a required server, marketing tracking, ads, subscriptions, in-app purchases, or app-level backup.
- Distribution-channel decisions are intentionally not covered in this specification.

## Testing Decisions

- Test external behavior at the highest approved seam: shared use cases call platform ports for capture and media-library operations. Automated tests use simulated platform adapters; tests assert user-visible state and resulting media and metadata rather than private implementation details.
- Test shared domain behavior for recipe validation and determinism, catalog integrity, 24/36-exposure roll consumption, development visibility, permission-state behavior, geotag removal, and serialization compatibility.
- Test native adapter contracts for camera capability fallback, capture lifecycle, media save and deletion scope, metadata preservation, and cancellation/error handling.
- Use iOS and Android device-level smoke tests for actual camera preview and capture, gallery persistence, video recording, permissions, and responsive UI behavior.
- Verify a preview target of at least 30 fps on representative mid-range devices, final rendering at higher quality, UI responsiveness during capture, and consistent effect output across preview, photo, and video.
- Add UI flow tests for first launch, Workbench selection, photo capture, closed-roll/development, gallery search and favorites, confirmed deletion, video audio permission, and accessibility labels.
- No test precedent exists in the current codebase because it contains no application source or test suite.

## Out of Scope

- Creating any server, required account, social feed, marketing analytics, advertising, subscription, or in-app purchase.
- App-level backup and restore, including encrypted export files.
- Choosing or implementing distribution channels in this specification.
- Reusing real camera, lens, film, competitor, or third-party brand names or assets.
- Flutter, React Native, Expo, and shared Kotlin Multiplatform UI. Kotlin Multiplatform is limited to shared logic; platform UIs and media pipelines remain native.
- Commercial use under the default code license without separate authorization.

## Further Notes

- The approved name is Théia, referencing the Titaness associated with celestial light, sight, dawn, and the brilliance attributed to precious metals. The supplied illustration is a visual reference for the naming rationale; it does not grant independent use of the Théia name, logo, or brand artwork.
- The design uses the supplied palette: solar gold #EAAE35, antique gold #A86D1F, ivory #E7D7B7, mantle terracotta #8E482B, dark bronze #5E321E, muted olive #4B5743, deep sky blue #182344, cosmic violet #2D2D57, nebula lilac #6D5A82, ruin stone #B6A58C, and near-black shadow #121522.
- The current repository has no code, ADRs, glossary, or tests; the architecture and domain vocabulary in this document are the initial project baseline.
- Use “source available under a noncommercial license” rather than “open source” in formal project descriptions because commercial use is restricted by the selected license.
