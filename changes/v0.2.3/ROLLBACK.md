# Rollback Procedures for v0.2.3

If v0.2.3 introduces regressions in connectivity or permissions, follow these steps to revert to v0.2.2.

## Restore Files
Manually restore the following files from `/changes/v0.2.2/FILES/`:

1. `EchoCoreService.kt` -> `/app/src/main/java/com/echosystem/localshare/service/EchoCoreService.kt`
2. `NsdHelper.kt` -> `/app/src/main/java/com/echosystem/localshare/discovery/NsdHelper.kt`
3. `Models.kt` -> `/app/src/main/java/com/echosystem/localshare/model/Models.kt` (Restore to previous state)
4. `TrustManager.kt` -> `/app/src/main/java/com/echosystem/localshare/security/TrustManager.kt`
5. `ManagementRoutes.kt` -> `/app/src/main/java/com/echosystem/localshare/server/routes/ManagementRoutes.kt`
6. `dashboard.html` -> `/app/src/main/assets/web/dashboard.html`

## Post-Rollback
1. Clear App Cache.
2. Force stop and restart the app.
3. Verify that the previous "v0.2.2" version string appears in logs.
