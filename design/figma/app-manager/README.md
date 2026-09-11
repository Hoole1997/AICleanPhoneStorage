# App Manager

Source: [Figma 5803:308](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb-%E9%A1%B5%E9%9D%A2UI?node-id=5803-308&m=dev).

`source.json` records the original node IDs and downloaded SVG exports. Regenerate the six icons at mdpi through xxxhdpi with `tools/convert_app_manager_assets.py` using `tools/requirements-app-manager-assets.txt` (CairoSVG also requires Cairo). Conversion is development-only; the application reads local WebP resources. The gradient reuses `home_header_background`; installed app icons come from PackageManager. The older arrow export is retained as a reference for the previous Settings design.

The native layout retains 48dp touch targets, natural text heights and Flow wrapping for sort controls. Last used defaults to newest first, size to largest first, and name to ascending; selecting the same key reverses the order. Unknown values stay at the end in either direction.

The catalog contains launcher-visible apps, consistent with the existing package visibility query. Without usage access, sizes represent APK files only. With access, StorageStatsManager reports app plus data bytes (cache is already included in data), and shared UID sizes are labeled explicitly. Last-use history is queried in one batch over 90 days; a missing record is not presented as proof that the app was never used. All package, file and statistics queries run off the main thread.

Uninstall opens the Android system confirmation for the selected package. System apps and the app itself offer Manage. Returning from settings/uninstall refreshes the actual catalog; no optimistic deletion is performed. Feature exit continues through `FeatureExitCoordinator` with `APPS_EXIT`, so the home page owns the interstitial.

Verification: local/google Kotlin compilation, local Lint, 98 unit tests and 8 API 32 instrumentation tests. Layout coverage includes 320/375/600dp widths, 1.0/1.5/2.0 font scales, long names, French/German/Arabic/Hindi/Chinese sort controls, real package data and a real Activity launch. Screenshot fixtures are isolated inside androidTest and never enter production repositories. No application was actually uninstalled during verification.
