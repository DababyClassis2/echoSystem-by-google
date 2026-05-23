# Rollback Action Guide - v1.1.1 to v1.1.0

If any instability is detected in the Crystal Release (v1.1.1), follow these steps to revert to the stable v1.1.0 baseline.

## 🔙 1. Backend Reversion
- **Database**: Delete Room database implementation in `com.echosystem.localshare.database` and restore in-memory `DeviceRegistry`.
- **API**: Revert `FileRoutes.kt` to the simple non-streaming multipart receiver and remove `mkdir`/`rename` endpoints.
- **Security**: Remove RSA KeyStore generation and revert to simple PIN-only verification.
- **Discovery**: Restrict NSD TXT records to name and IP only.

## 🎨 2. UI/UX Reversion
- **Typography**: Reconnect standard `MaterialTheme.typography` defaults in `Type.kt`.
- **Transitions**: Remove additional `animate*AsState` animations from card modifiers.
- **Haptics**: Disable `HapticUtil` calls in viewmodels and listeners.
- **Web Layout**: Remove `max-w-6xl` containers from `dashboard.html`.

## ⚙️ 3. Environment Reversion
- **Version**: Reset `versionCode: 11` and `versionName: "1.1.0"` in `app/build.gradle.kts`.
- **Assets**: Remove locally downloaded `tailwind.js` and `lucide.min.js` if fallback to CDN is desired (though not recommended for local mesh).
