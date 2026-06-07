[app]

# (str) Title of your application
title = Smart Web Scraper

# (str) Package name — must be lowercase, no spaces
package.name = smartwebscraper

# (str) Package domain (used for Android/iOS packaging)
package.domain = org.webscraper

# (str) Source code directory where main.py lives
source.dir = .

# (list) Extensions to include in the APK
source.include_exts = py,png,jpg,kv,atlas,txt

# (list) Files/dirs to exclude from source package
source.exclude_dirs = app,.github,.git,.buildozer,bin,.idea,.gradle

# (str) Application version
version = 4.0

# (list) Python + Kivy requirements
# lxml is needed by BeautifulSoup for fast HTML parsing
# pillow is required by openpyxl for image handling in Excel
requirements = python3,kivy==2.3.0,requests,beautifulsoup4,pandas,openpyxl,urllib3,lxml,pillow,certifi,charset-normalizer,idna,soupsieve,et-xmlfile

# (str) Supported orientation
orientation = portrait

# (bool) Show app in fullscreen
fullscreen = 0

# (str) Application icon — leave blank for default
#icon.filename = %(source.dir)s/icon.png

# (str) Presplash image
#presplash.filename = %(source.dir)s/presplash.png

# ─── Android specific ────────────────────────────────────────────────────────

# (list) Permissions
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE

# Allow legacy external storage access (needed for /sdcard/Download/ on Android 10)
android.extra_manifest_application_arguments = android:requestLegacyExternalStorage="true"

# (int) Target Android API — as high as possible
android.api = 33

# (int) Minimum Android API supported
android.minapi = 26

# (str) Android NDK version
android.ndk = 25b

# (int) Android NDK API level
android.ndk_api = 26

# (str) Target architecture — arm64-v8a for modern phones
android.arch = arm64-v8a

# (bool) Allow app data backup
android.allow_backup = True

# (bool) Skip checking/updating Android SDK (set True to speed up repeated builds)
android.skip_update = False

# (bool) Automatically accept Android SDK licenses (required for CI)
android.accept_sdk_license = True

# ─── Buildozer settings ──────────────────────────────────────────────────────

[buildozer]

# Log level: 0=error only, 1=info, 2=debug with full command output
log_level = 2

# Warn when buildozer is run as root
warn_on_root = 1
