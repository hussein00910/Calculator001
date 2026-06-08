[app]

# (str) Title of your application
title = Smart Web Scraper

# (str) Package name — lowercase, no spaces
package.name = smartwebscraper

# (str) Package domain
package.domain = org.webscraper

# (str) Source code directory where main.py lives
source.dir = .

# (list) Extensions to include in the APK
source.include_exts = py,png,jpg,kv,atlas,txt

# (list) Directories to exclude from source package
source.exclude_dirs = app,.github,.git,.buildozer,bin,.idea,.gradle

# (str) Application version
version = 4.0

# (list) Requirements — only pure-Python or safe-to-compile libraries
# pandas, openpyxl, lxml, pillow removed: they require complex C compilation
# beautifulsoup4 uses html.parser (built-in) — no lxml needed
requirements = python3,kivy==2.3.0,requests,beautifulsoup4,urllib3,charset-normalizer,idna

# (str) Supported orientation
orientation = portrait

# (bool) Fullscreen
fullscreen = 0

# ─── Android specific ────────────────────────────────────────────────────────

# (list) Permissions
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE

# Allow legacy external storage (needed for /sdcard/Download/ on Android 10)
android.extra_manifest_application_arguments = android:requestLegacyExternalStorage="true"

# (int) Target Android API
android.api = 33

# (int) Minimum Android API — 21 covers ~99% of active devices
android.minapi = 21

# (str) Android NDK version
android.ndk = 25b

# (int) Android NDK API level
android.ndk_api = 21

# (str) Target architecture — arm64-v8a for modern 64-bit phones
android.archs = arm64-v8a

# (bool) Allow app data backup
android.allow_backup = True

# (bool) Automatically accept SDK licenses (required for CI)
android.accept_sdk_license = True

# ─── Buildozer settings ──────────────────────────────────────────────────────

[buildozer]

# Log level: 0=error only, 1=info, 2=debug with full command output
log_level = 2

# Warn when buildozer is run as root
warn_on_root = 1
