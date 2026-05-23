# echoSystem v1.1.1 Rollback Protocol

If critical mesh instability or UI regressions are detected, follow these steps to revert to v1.1.0.

## Automated Method
1. Execute `./scripts/revert_v1.1.1.sh` (if available in your CI/CD environment).

## Manual Method
1. **Gradle Reversion**: 
   - Open `app/build.gradle.kts`
   - Set `versionName` back to "1.1.0"
   - Set `versionCode` back to 10.

2. **Feature Toggle Removal**:
   - Delete `app/src/main/java/com/echosystem/localshare/core/connection/ConnectionManager.kt`
   - Revert `EchoViewModel.kt` to depend on the legacy `DiscoveryManager`.

3. **Database Migration**:
   - v1.1.1 introduced Room. Reverting will lose persistence.
   - Delete `app/src/main/java/com/echosystem/localshare/database/` directory.
   - Clear app storage on test devices to reset Room schema.

4. **Manifest Cleanup**:
   - Remove `android.permission.VIBRATE` from `AndroidManifest.xml`.

5. **Web Assets**:
   - Restore `app/src/main/assets/web/` from v1.1.0 backup.

---
**Warning**: Downgrading may cause "Database downgrade not supported" errors unless `SQLiteOpenHelper` version is manually handled or app data is cleared.
