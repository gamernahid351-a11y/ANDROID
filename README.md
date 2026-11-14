# PRIME X GATEWAY — Android App

bKash, Nagad, Rocket SMS auto-reader and gateway — same design as the web version.

## Features
- Real SMS reading via BroadcastReceiver
- bKash / Nagad / Rocket SMS parser (exact same regex as web)
- Dark slate/indigo UI (matches web design)
- Login / Register with same API
- Dashboard with live stats
- Transaction history with retry
- Foreground service (runs in background)
- Auto-retry queued transactions every 30 seconds
- Boot-on-startup support

## GitHub Actions Auto Build

Push to GitHub → APK auto builds and appears in **Actions → Artifacts**.

### Steps to get APK:

1. Create a new GitHub repository
2. Upload this project:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USERNAME/primex-gateway-android.git
   git push -u origin main
   ```
3. GitHub Actions will auto-start building
4. Go to: **Actions tab → Latest workflow run → Artifacts**
5. Download `PrimeXGateway-debug.zip` → extract → install `.apk`

## Manual Build (Android Studio)
1. Open Android Studio
2. File → Open → select this folder
3. Wait for Gradle sync
4. Run → Run 'app' or Build → Build Bundle(s)/APK(s)

## Server
Default: `http://5.175.140.94:3000` (same as web project)
Can be changed in Settings screen.

## Permissions Required
- `RECEIVE_SMS` — read incoming SMS
- `READ_SMS` — parse SMS content  
- `INTERNET` — sync to server
- `FOREGROUND_SERVICE` — run in background
- `POST_NOTIFICATIONS` — show gateway status notification
