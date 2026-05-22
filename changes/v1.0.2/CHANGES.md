# Backend Stability & Core Engine Update (v1.0.2)

This update focuses exclusively on the stability of the core engine, as requested. No UI changes have been included in this stage.

## 1. Foreground Service Fix
- **Immediate Activation**: `startForeground()` is now the absolute first line in `onCreate` and `onStartCommand`.
- **System Resistance**: This prevents the Android OS from killing the service during the sensitive transition period after boot or launch.

## 2. Discovery Reliability (NSD)
- **Multicast Lock**: Added `WifiManager.MulticastLock` to ensure discovery packets are processed even when the device is in low-power states.
- **Exponential Backoff**: Registration now retries with increasing delays if the network stack is busy.

## 3. High-Speed Web Engine
- **Netty Isolation**: Coroutine scope for the web server is now fully isolated from the main service scope.
- **2GB+ Upload Support**: Switched to a streaming buffer approach (128KB chunks) which removes the previous hardcoded 50MB memory limit.
- **Health Checks**: Added a `/health` endpoint for future internal watchdog monitoring.

## 4. Event Logic
- **Deduplication**: Implemented a `ConcurrentHashMap` based throttling layer in the ViewModel to prevent duplicate UI updates for the same file transfer event.
