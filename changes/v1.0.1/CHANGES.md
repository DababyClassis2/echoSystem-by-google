# System Failure Fixes & Radar Enhancements (v1.0.1)

This update addresses critical system failures and provides the requested UI/UX enhancements.

## 1. Core System Fixes
- **Foreground Service Crash**: `startForeground()` is now called immediately in `onCreate` and `onStartCommand` to prevent Android OS kill-timeouts.
- **NSD Discovery Deadlock**: Rebuilt the Network Service Discovery with exponential backoff and proper listener cleanup to ensure reliable device finding.
- **Duplicate Events**: Implemented a 1-second deduplication window to prevent UI flickering on multiple file transfers.
- **Netty Stability**: Isolated the web server in its own coroutine scope to prevent the main app from freezing during heavy transfers.

## 2. UI Enhancements
- **Enhanced Device List**: Cards now show IP addresses, port numbers, and live connection status (Online/Offline).
- **Advanced File Browser**: Added multi-selection support and directory traversal for `/storage/emulated/0/echoSystem/`.
- **Trust Manager**: A new dedicated screen to manage trusted devices, allowing you to block, rename, or revoke authorization.
- **Web Dashboard 2.0**: Completely redesigned web interface with a two-pane layout, folder navigation, and a modern "Glassmorphism" aesthetic.
- **Web Portal Tools**: Added buttons to stop, refresh, or create custom servers on different ports.

## 3. How to Verify
1. Open the "Send" tab to see the new device card details.
2. Long-press on any file in the Browser to enter multi-select mode.
3. Access the "Portal" tab to see the active server management options.
