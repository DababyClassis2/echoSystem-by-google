# Rollback Plan - v0.2.8

## Reverting to v0.2.7
To revert the Web Dashboard 2.0 changes:

1.  **Gradle**: Set `versionCode` back to `9` and `versionName` to `"0.2.7"`.
2.  **Web Assets**:
    - Restore the previous `dashboard.html` (single-file version).
    - Delete `app/src/main/assets/web/dashboard.css` and `app/src/main/assets/web/dashboard.js`.
3.  **Kotlin Routes**:
    - Revert `/management/*` routes in `ManagementRoutes.kt` back to `/web/manage/*`.
    - Revert `FileWebResponse` and `FileRoutes.kt` changes (remove `path` support and `isDirectory` field).
4.  **Metadata**: Restore v0.2.7 description in `metadata.json`.

## Impact
Rollback will disable subfolder browsing in the web portal and return the UI to the older mobile-only style.
