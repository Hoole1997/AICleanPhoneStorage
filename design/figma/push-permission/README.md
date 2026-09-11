# Notification permission guide

[Figma 5816:4453](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb-%E9%A1%B5%E9%9D%A2UI?node-id=5816-4453&m=dev). Original illustration and close-icon exports are preserved in `source/` with metadata in `source.json`. `tools/convert_push_permission_assets.py` produces transparent density-specific WebP using the existing asset conversion dependencies. Conversion is development-only.

The native Material bottom sheet retains 16dp top corners when expanded, a 100dp illustration, 48dp close/Allow touch targets, natural text heights and bounded scrolling on smaller displays. Text uses the project's Roboto fonts and is localized in all supported languages.

## Host Activity and permission flow

`PushPermissionCoordinator.attach(activity, runtime, model, permissions)` binds the flow to the supplied Activity and that Activity's shared PermissionCoordinator. No return route is hard-coded to MainActivity. PermissionSettingsNavigator derives the return component from the Activity that opened system settings, so StartupActivity returns to StartupActivity and MainActivity returns to MainActivity.

The guide is only queued after a denied XXPermissions callback. Missing permission alone, successful requests and launch errors do not create it. Denials received in the background wait until the host is resumed. Initial attempts, pending actions, guide visibility and completion are retained in SavedStateHandle. Closing the guide or denying its retry does not cause a prompt loop in the same host lifetime. A queued action can resume after process recreation; an orphaned runtime callback releases the flow without inventing a denial. Restored system-settings requests still wait for the shared result.

Allow retries a normal runtime request. Permanent denial, pre-Android-13 notification settings and an OEM-disabled notification service instead use the shared POST_NOTIFICATIONS settings flow. Its two-minute bounded check begins only after the user explicitly chooses to open settings. Grant stops detection and triggers one public-API return attempt; cancellation, manual return and timeout stop further checking. Android may block background navigation; manual return consumes the same result. Permission checks use XXPermissions and successful authorization refreshes resident notifications.

## Startup sequencing

Startup waits for language preparation and the permission flow before requesting `loadSplash`. Already-granted permission completes immediately. After denial, the guide must be closed, or its Allow request/settings flow must return a result, before the ad can start. Ad scheduling still requires a visible, resumed Activity with window focus. The ad callback and a minimum three-second stay jointly control navigation. The three-second clock starts when the visible, resumed startup page actually begins the splash-ad request, so time spent in notification prompts/system settings cannot cause an immediate transition after returning. A long-running real ad does not incur an additional three-second delay after its callback.

Permission return intents are handled before reading a new startup entry, preserving the original notification destination and preventing a second ad cycle. Startup forwards a completion marker to MainActivity, which skips an immediate duplicate permission request without treating denial as a grant. The notification module's trigger scheduling and resident-notification implementation are unchanged.

## Validation

Local/google compilation, local Lint, 113 app unit tests and 12 targeted API 32 instrumentation tests passed. Coverage includes a long system-settings visit followed by an immediate SDK callback, permission/ad sequencing, one-shot callbacks, Activity return targets, original notification destinations, process restoration, close/Allow behavior, normal/double font sizes and popup corners. Permission and ad branches use fake gateways/callbacks for deterministic testing; actual Android permission buttons and live ads were not exercised in this validation.
