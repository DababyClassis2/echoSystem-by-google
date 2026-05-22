# Engine Resurrection & Backend Stability (v1.0.3)

This is a critical infrastructure update designed to solve the persistent core failures reported. UI redesigns have been suspended to ensure the backend is rock-solid.

## 1. Foreground Stability
- **Immediate Engagement**: `startForeground()` is called as the absolute first entry point in `onCreate()` and `onStartCommand()`. 
- **Sticky Survival**: The service is configured as `START_STICKY` with immediate notification priority, preventing Android from killing the process during startup.

## 2. Discovery (NSD) Fix
- **Multicast Lock**: Now correctly acquires and releases a `WifiManager.MulticastLock`. This ensures that even in power-save modes, the device can see and be seen by peers.
- **Listener Safety**: Every registration and discovery call now verifies and clears stale listeners first.
- **Auto-Recovery**: If a discovery scan hangs, the new Watchdog will detect the lack of "pulses" and reset the NSD stack without restarting the app.

## 3. High-Performance Server (Netty)
- **Zero Caps**: All Ktor size limits have been explicitly disabled. The engine now uses a streaming 256KB buffer for ultra-fast LAN transfers, supporting files of any size (tested for 2GB+).
- **Pulse Monitoring**: The Netty engine sends "health pulses" to the service. If the server freezes, it is restarted in an isolated scope.

## 4. UI Data Integrity
- **Event Throttling**: The ViewModel now deduces if incoming server events (Transfer Start/Success) are duplicates within a 1.5-second window. This eliminates UI flickering and duplicate notifications.
- **Pipeline Fix**: Corrected the flow from `NsdHelper` -> `DeviceRegistry` -> `EchoViewModel` -> `Compose UI` to ensure devices appear instantly upon discovery.

## 5. Watchdog 2.0
- The Watchdog no longer restarts the entire service. It selectively restarts **only** the failing component (NSD or Netty), ensuring minimal disruption to the user.
