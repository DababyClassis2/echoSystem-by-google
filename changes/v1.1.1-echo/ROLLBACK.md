# Rollback Guide for echoSystem v1.1.1-echo

If critical stability issues are detected in the v1.1.1 release, follow these instructions to safely revert to the redundant v1.1.0 stable branch.

## 1. Core Engine Reversion
- **Unified Handover**: Delete `com.echosystem.localshare.core.TransferEngineCore` and revert `EchoViewModel` to direct `HttpClient` calls.
- **Connection Logic**: Revert `ConnectionManager` to the legacy `NetworkUtils` static polling method.
- **Recovery Logic**: Disable `CoreRecoveryManager` background checks.

## 2. UI/UX Reversion
- **Typography**: Restore `Type.kt` to standard Material 3 defaults.
- **Transitions**: Remove `MotionSystem.kt` and revert `Navigation.kt` to standard cross-fades.
- **Previews**: Remove `PreviewOverlay.kt` and re-enable system Intent sharing as the primary view method.

## 3. Web Portal
- Revert `dashboard.html` to the v1.1.0 template (remove `max-width` constraints and Lucide integration if necessary).

## 4. Metadata
- Downgrade `metadata.json` version string to `1.1.0`.
- Downgrade `VERSION.json` to `1.1.0`.

---
*Automatic rollback scripts are available in the system root under `.hidden/rollback.sh`.*
