# Changes for v0.2.2 - Crash Mitigation

This version addresses critical stability issues that caused the application to crash immediately upon launch on various Android devices.

## 1. Foreground Service Fix (EchoCoreService)

### The Issue
Android requires that any service started via `startForegroundService()` must call `startForeground()` within a very short time window (usually 5-10 seconds). If this call is missed or fails, the system throws a `RemoteServiceException`. In previous versions, if an error occurred during notification setup, the call was skipped, leading to a crash.

### The Fix
- **Early Execution**: `startForegroundSafe()` is now the absolute first line of code executed in `onCreate()`.
- **Redundancy**: The foreground state is re-confirmed in `onStartCommand()` to handle system-initiated service restarts.
- **Defensive Logging**: Added extensive logging and `AppLogger` integration to track the service lifecycle in real-time.
- **Resilience**: Even if sub-engines (Netty/NSD) fail to start, the service itself remains in a valid foreground state to prevent process termination.

## 2. Multicast Lock Crash-Proofing (NsdHelper)

### The Issue
The NSD engine requires a `MulticastLock` to reliably discover devices on some Wi-Fi networks. Acquiring this lock requires the `CHANGE_WIFI_MULTICAST_STATE` permission. On many devices, if this permission is not explicitly granted or if the system restricts it, calling `acquire()` throws a `SecurityException`, crashing the app.

### The Fix
- **Safe Acquisition**: Created `acquireMulticastLockSafely()` which performs a pre-flight permission check.
- **Exception Shielding**: Wrapped the `acquire()` call in a `try-catch` block specifically for `SecurityException`.
- **Degraded Operation**: If the lock cannot be acquired, the app now logs a warning to `NSD_WATCHDOG` and continues with standard NSD registration. While discovery might be slightly less reliable on some old routers, the app will no longer crash.
- **Safe Release**: Hardened the release logic to prevent null-pointer or state exceptions during shutdown.

## How to Verify
1. **Launch the App**: The app should now reach the main screen without disappearing or showing a "stopped" dialog.
2. **Check Logs**: Navigate to Settings -> Logs. You should see "Service online and in foreground."
3. **NSD Status**: If your device restricts multicast, the logs will show a warning: "Permission CHANGE_WIFI_MULTICAST_STATE missing. Discovery may be degraded." but the app will continue to function.
