# Rollback Procedures for v0.2.2

If you encounter unexpected regressions in v0.2.2, follow these steps to revert to the previous stable backend state (v0.2.1).

## Automated Reversion
Restore the following files from your v0.2.1 backups:

1.  **EchoCoreService.kt**
    - Path: `app/src/main/java/com/echosystem/localshare/service/EchoCoreService.kt`
2.  **NsdHelper.kt**
    - Path: `app/src/main/java/com/echosystem/localshare/discovery/NsdHelper.kt`

## Manual Reversion (via Diffs)
If you do not have backups, you can manually reverse the changes by applying the inverse of the diffs located in `changes/v0.2.2/DIFFS/`.

### Warning
Reverting will re-introduce the following risks:
- Immediate app crashes on launch on devices requiring strict `startForeground` timing.
- Immediate app crashes on devices where `CHANGE_WIFI_MULTICAST_STATE` is not granted.
