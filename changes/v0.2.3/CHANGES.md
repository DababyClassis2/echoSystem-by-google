# Changes for v0.2.3 - Engine Hardening & Permissions

This release focuses on absolute stability of the core engine and finalizes the user-controlled permission model.

## 1. Bulletproof Service Lifecycle (EchoCoreService)
- **Foreground Safety**: `startForegroundSafe()` is now an idempotent, synchronized method called as the very first line of `onCreate` and `onStartCommand`.
- **Fallbacks**: If notification creation or channel setup fails, the engine still attempts to enter a valid foreground state rather than crashing.
- **Isolated Watchdogs**: The service no longer restarts entirely if an engine fails. Instead, separate health-check pulses for Netty and NSD trigger individual engine restarts.

## 2. Hardened Discovery (NsdHelper)
- **Permission Awareness**: The engine now proactively checks for `CHANGE_WIFI_MULTICAST_STATE` before attempting lock acquisition.
- **Security Guard**: Every `acquire()` call is shielded with a `try-catch` for `SecurityException`.
- **NSD State Machine**: Discovery now transitions through formal states (`DISCOVERING`, `REGISTERED`, `ERROR_DEGRADED`, `OFFLINE`).
- **Watchdog Throttling**: If discovery is stuck, it performs 5 exponential backoff retries before entering `OFFLINE` mode, preventing infinite log spam.

## 3. Web Portal Security Registry
- **Privileged Management**: Peers with `MANAGE_PERMISSIONS` now have access to a new **Security Registry** panel.
- **Remote Control**: Peers can now rename devices, block/unblock unauthorized nodes, and adjust permissions for others directly from the web dashboard.
- **Server Enforcement**: All actions (Rename, Block, Permission change) are verified server-side against the requesting device's granted permissions.

## 4. Stability Improvements
- **Health Endpoint**: Netty now includes a `/health` route used by the watchdog for periodic verification.
- **High-Speed Transfer**: Multipart upload buffers optimized for local gigabit speeds.
- **Clean Shutdown**: Improved coroutine scope cancellation to prevent leaks on service termination.

## How to Verify
1. **Launch App**: Verify it starts immediately without crash.
2. **Settings -> Security**: Manage a device and grant it 'Admin (Manage Others)'.
3. **Web Portal**: Use that device to view the web dashboard; confirm the Shield icon appears in the header.
4. **Permissions**: Use the web dashboard to block another device or rename it; verify the change reflects in the mobile app.

## Rollback
Restore `EchoCoreService.kt`, `NsdHelper.kt`, `ManagementRoutes.kt`, `TrustManager.kt`, `Models.kt`, and `dashboard.html` from v0.2.2 backups.
