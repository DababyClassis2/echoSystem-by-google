# LocalShare v0.2.3: UI & System Documentation

This document provides a comprehensive overview of the **LocalShare** application interface, architecture, and system connectivity as of version 0.2.3.

## 🎨 Design Themes & Aesthetics

### Mobile Application
- **Material Design 3 (M3)**: Utilizes the robust M3 framework with dynamic coloring.
- **Glassmorphism Accents**: Subtle translucency in overlays and bottom bars.
- **Color Palette**: 
  - **Primary**: Indigo/Blue accents for active states and actions.
  - **Secondary**: Emerald/Green for success and connected states.
  - **Surface**: Clean, elevated card layouts with a focus on negative space.
- **Interactive States**: Heavy use of ripple effects, spring animations, and smooth transitions (fade/slide).

### Web Portal
- **Cosmic Slate Theme**: A deep-dark visual identity (`#020409` background).
- **Modern Glassmorphism**: High-blur backdrops (`blur(12px)`) with thin stroke borders (`rgba(255, 255, 255, 0.05)`).
- **Typography**: "Plus Jakarta Sans" for a clean, professional, and readable feel.
- **Micro-interactions**: Hover scales, smooth opacity transitions, and animated pulsars for "Scanning" states.

## 📱 Mobile UI (App Tabs & Screens)

The mobile app is structured around 3 primary functional tabs and secondary management screens.

| Tab / Screen | Label | Core Function |
| :--- | :--- | :--- |
| **Send Tab** | `Transmit` | Lists nearby Peer Nodes. Allows picking multiple files and initiating secure packets. |
| **Receive Tab** | `Receive` | Activates the Radar. Displays local IP and the active 6-digit Shield Key (PIN). |
| **Web Share Tab** | `Web Share` | Displays a Dynamic QR Code and IP link. Acts as the entry point for cross-platform sharing. |
| **Trusted Devices** | `Registry` | Accessible from the Top Menu. Manages historical pairings and persistent trust. |
| **Transfer History** | `History` | Detailed logs of all incoming and outgoing transmissions. |

### Major Buttons & Keys
- **Transmit Button (Send Tab)**: Initiates the `sendMultipleFilesToDevice` function.
- **Security PIN (Receive Tab)**: The dynamic 6-digit `pairingPin` required by peers to link.
- **QR Code Canvas**: A custom-drawn, deterministic canvas that maps the `shareUrl` for instant browser access.
- **Local Storage Explorer (Web Tab)**: Jump-link to view and manage files stored in the local portal repository.

## 🌐 Web Portal UI (Dashboard)

The Web Portal is a single-screen responsive hub served on port 8080.

### Structural Flow
1. **Secure Link (Auth)**: Users must enter the phone's PIN. This validates the `X-Device-Id` against the host's `PairingManager`.
2. **Dashboard**: Once authorized, reveals the file repository.
3. **Security Registry**: A privileged view for managing other network nodes (block/permission toggle).

### Key Components
- **Shield Button (Header)**: Opens the "Security Registry" (visible only if `MANAGE_PERMISSIONS` is granted).
- **Category Nav (Bottom)**: Filter library by `Photos`, `Videos`, `Music`, or `Docs`.
- **Upload FAB**: Floating Plus button that triggers the multi-file selector.
- **Transmission Queue**: Real-time progress bars for active multipart uploads.

## ⚙️ Architecture & Implementation

### Core Service: `EchoCoreService`
- **Foreground Engine**: Runs a sticky foreground service with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.
- **Netty Server**: Kotlin-based high-performance server handling REST and WebSockets.
- **NSD Helper**: Manages `_echoshare._tcp.` discovery using zero-conf networking (mDNS).
- **Watchdog Logic**: Periodically (30s) pulses both Netty and NSD engines. Restarts them if communication stalls (45s/60s).

### API & Network Structures
The system uses a custom REST API over local Wi-Fi:
- `POST /pairing/request`: Negotiates link between two nodes.
- `GET /web/files`: Fetches the structured file metadata.
- `POST /web/upload`: Multipath file intake.
- `GET /web/download`: Secure file streaming.
- `POST /web/manage/block`: Remote node isolation.

### Security Registry (New v0.2.3)
- **Persistent Trust**: Managed by `TrustManager` using `EncryptedSharedPreferences`.
- **Granular Permissions**:
  - `BROWSE_FILES`: See the list.
  - `UPLOAD_FILES`: Submit new files.
  - `DOWNLOAD_FILES`: Pull files from host.
  - `DELETE_FILES`: Permanent removal.
  - `MANAGE_PERMISSIONS`: Full administrative control.

## 🔗 Connections & Integration

- **Phone <-> System**: The app requests `MANAGE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` to interface with the Android filesystem safely via scoped storage or direct paths.
- **App <-> Web**: Synchronized via the `PairingManager`. The web browser generates a unique `deviceId` stored in `localStorage`, which must be approved by the phone.
- **Network Stack**: Operates entirely offline on the local WLAN/LAN. Uses `CHANGE_WIFI_MULTICAST_STATE` to ensure discovery works across various router types.

## ✂️ Eliminated or Edited-outs
- **V0.1.x Legacy Auth**: Removed hardcoded "Success" states in favor of real `EncryptedSharedPreferences` and PIN hashing.
- **Simulated Progress**: Replaced fake delays with real `XHR.upload.onprogress` events and Ktor Content Length monitoring.
- **Flat File List**: Edited the original list into a Categorized Grid to support large media libraries.
- **Hardcoded IP**: Eliminated static IP assumptions; system now dynamically queries `LinkProperties` and `WifiManager`.
