# CVETIK-VPN (WG Tunnel Backend)

## Описание
Android VPN приложение с WebView UI и WireGuard/AmneziaWG backend.

## Конфиги (встроены)
- Пермь-1: AmneziaWG, Cloudflare WARP
- Пермь-2: AmneziaWG, Cloudflare WARP

## Как загрузить в GitHub из Termux

```bash
# 1. Перейди в папку проекта
cd ~/cvetik-vpn

# 2. Скопируй ZIP из Downloads
cp /storage/emulated/0/Download/cvetik-vpn-wgtunnel.zip /tmp/cvetik.zip

# 3. Распакуй
unzip -o /tmp/cvetik.zip

# 4. Загрузи на GitHub
git add .
git commit -m "WG Tunnel backend v5.0"
git push origin main
```

## Как собрать APK
```bash
cd ~/cvetik-vpn
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## ВАЖНО
Этот проект использует UDP forwarding через VPN интерфейс.
Для ПОЛНОГО AmneziaWG шифрования нужна интеграция с amneziawg-go.
Текущая версия: пакеты маршрутизируются, но без Noise протокола.
