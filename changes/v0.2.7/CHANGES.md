# LocalShare v0.2.7 - WebShare UI 2.0

## Overview
This update overhauls the WebShare (Portal) tab to align with UI 2.0 standards, providing better visibility into active browser sessions and a more secure pairing experience.

## Key Changes
- **Core Engine**: `WebShareServer` now tracks and exposes active WebSocket session IP addresses.
- **ViewModel**: `WebShareViewModel` integrated with `TrustManager` and `PairingManager`.
- **UI Components**:
    - `QrCanvas`: High-fidelity QR code display using ZXing.
    - `SessionList`: Real-time list of connected mesh nodes (browsers).
    - `WebShareControls`: Simplified Ignite/Terminate logic.
- **User Experience**: Added animated transitions and "Waiting for Ignition" empty states.

## Modified Files
- `app/build.gradle.kts` (Version bump)
- `metadata.json` (Description update)
- `WebShareServer.kt` (Session tracking)
- `WebShareViewModel.kt` (State management)
- `Navigation.kt` (Route cleanup)

## New Files
- `ui/screens/webshare/QrCanvas.kt`
- `ui/screens/webshare/SessionList.kt`
- `ui/screens/webshare/WebShareControls.kt`
- `ui/screens/webshare/WebShareScreen.kt`
