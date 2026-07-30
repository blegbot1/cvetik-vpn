# CVETIK-VPN (WG Tunnel Fork)

## Description
Android VPN app with WebView UI and full WireGuard/AmneziaWG backend.

## Features
- WebView UI (dark theme, no ripple, clean design)
- Full AmneziaWG config parser (Jc, Jmin, Jmax, H1-H4, MTU)
- Embedded configs: Perm-1, Perm-2
- Auto ping every 5 seconds
- Traffic stats every 1 second
- Keepalive packets
- Full logging

## How to build in Termux
```bash
cd ~/cvetik-vpn
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## How to push to GitHub
```bash
cd ~/cvetik-vpn
git add .
git commit -m "WG Tunnel fork v10.0"
git push origin main
```

## IMPORTANT
This uses UDP forwarding with proper VPN interface setup.
For FULL AmneziaWG encryption (Noise protocol + junk packets),
native amneziawg-go library is needed.
