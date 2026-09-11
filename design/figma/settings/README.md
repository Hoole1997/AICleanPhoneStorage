# Settings

Source: [Figma 5811:482](https://www.figma.com/design/dVsTL6ggoDPXVPcEKg856G/lcb-%E9%A1%B5%E9%9D%A2UI?node-id=5811-482&m=dev).

`source.json` records node IDs and original SVG exports. Run `tools/convert_settings_assets.py` using `tools/requirements-app-manager-assets.txt` to generate transparent WebP at mdpi through xxxhdpi. The back arrow exactly matches the existing App Manager export and uses a drawable alias; the background reuses the existing Figma gradient. No conversion or remote asset loading occurs in the application.

The card has a 12dp corner radius, 24dp icons and 16dp chevrons. Five 48dp touch targets with 8dp gaps and 12dp outer vertical padding reproduce the design’s 296dp card height. Text is naturally measured; long title/value combinations stack at larger font sizes instead of clipping.

The current language is a native-name resource in each supported locale. Android resolves it using the same configuration as the visible UI, including follow-system and English fallback. Settings refreshes the value on resume after language changes, without polling or reading preferences on the main thread. Translation overrides retain the native names when regenerating localization files.

Verification covers local/google Kotlin builds, local Lint, six SettingsDeviceTest cases, five locales at normal/double font size, all 16 language names, unsupported-language fallback, actual language switching and Activity recreation, existing settings destinations and feedback draft restoration. Design screenshot uses a 375dp frame and a simulated 44dp system inset; runtime uses actual window insets.
