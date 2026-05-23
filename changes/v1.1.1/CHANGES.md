# Changes Overview - echoSystem v1.1.1 (backend-polish)

This update represents a complete stability, security, validation, and performance rewrite of the echoSystem core backend services spanning Android & Web modules. No new features were added; rather, existing structures were refactored for optimal security, compliance, and runtime predictability.

---

## Refinement Bricks Implemented in v1.1.1

### 1. Unified Connection Manager
- **Consolidation**: Consolidated Wi-Fi, Hotspot, and Wi-Fi Direct detections into a unified reactive stream.
- **Stateflow Exposure**: Implemented real-time online state tracking through `onlineDevices` Flow, allowing UI of both local (Compose) and remote (Web Dashboard) viewers to adapt.

### 2. Large File Transfer Footprint Optimization
- **Streaming Mechanics**: Implemented raw, chunk-by-chunk stream pipelining in `/web/download` and `/web/upload` to prevent Heap overflow / Out-Of-Memory (OOM) crashes on large file payloads (e.g. 1GB+ files).
- **Multipart Isolation**: Redesigned multipart form parsing to process chunks directly to disk partitions rather than buffering them into transient RAM memory arrays.

### 3. Persistent Device & Trust Database (Room / SQLite)
- **Room Migration**: Replaced fragile, ephemeral in-memory `DeviceRegistry` and standard `SharedPreferences` with a resilient SQLite transactional database via **Room Framework** (`EchoDatabase` v1).
- **Entities Declared**:
  - `DeviceEntity`: Stores node identifier (PK), display name, host IP address, trust status (`TRUSTED`, `BLOCKED`, `PENDING`), last seen timestamp, user-assigned notes, and JSON-serialized capability permissions.
  - `TransferHistoryEntity`: Keeps logs of all incoming/outgoing network transfers, state tracking, and checksum verifications.
  - `PairingKeyEntity`: Stores the authorized target node IDs and matched public RSA credentials.
- **Migration & Safety**: Enabled safe DB creation lifecycle with graceful destructive fallbacks to ensure clean first-launch execution.

### 4. Authenticated Pairing (Secure RSA + Playback Safety)
- **Android Keystore Integration**: Introduced modern cryptographic pairing. Generated 2048-bit RSA key pairs securely scoped within the hardware-backed `AndroidKeyStore`.
- **Public Key Exchange**: Exchanged RSA public keys across Web `/pairing/request` and stored them inside `PairingKeyEntity` inside Room. Private keys never leave the secure system keystore.
- **Zero-Zero Access**: Encrypted PIN sequences and sensitive temporary authentication flags reside in `EncryptedSharedPreferences`.

### 5. Enhanced WebSocket & Restful Web Dashboard
- **Directory Nesting & Traversal Checks**: Redesigned `/web/files` to accept a nested directory `path` parameter allowing multi-level browsing inside `/echoSystem/` root. Implemented absolute canonical validation checks to fully block directory traversal attacks (`../` malicious sequences).
- **Mkdir / Rename REST Controls**: Implemented safe, validated endpoints for `/web/mkdir` and `/web/rename`.
- **WebSocket Synchronization Channel**: Upgraded `webSocket("/events")` to broadcast custom, lightweight JSON event formats:
  - `device_online` & `device_offline`
  - `file_changed`
  - `transfer_progress`
  - `transfer_started`, `transfer_completed`, and `transfer_failed`
- **Frontend Real-time Sync**: Updated Web `dashboard.html` with a New Folder action and updated `dashboard.js` with WebSocket-driven auto-mesh reload and responsive progress tracking.

### 7. UX Refinement: Friendly Empty States
- **Emotional Language**: Rewrote cold, technical UI messages (e.g., "Isolated Node", "Null Directory") with friendly, encouraging prose.
- **Radar Screen (No Devices)**: Implemented `EmptyDevicesState` with a Radar icon and a "Scan Again" button to encourage discovery.
- **Storage Screen (Empty Folder)**: Dynamically differentiated between root and subfolder empty states.
  - Root: "Your echoSystem storage is ready. Drop files here or tap Upload."
  - Subfolder: "This folder is empty. Tap + to add files."
- **History Screen**: Added a clear, centered "No transfers yet" state with a descriptive call to action.
- **Web Dashboard**: Updated the file grid with a centered "Nothing here yet" state featuring a `cloud-upload` icon and Indonesian Indo-Mesh indigo aesthetic.
- **Desktop Layout Refinement**: Constrained wide-screen content to `max-w-6xl` while maintaining full-width branding backgrounds for a professional desktop presence.
- **Responsive File Grid**: Optimized grid columns for all screen sizes (from 2 to 5 columns) with improved spacing.
- **Card Polishing**: Added tactile hover feedback (scale and shadow) for all file/folder cards.
- **Centered Sync Engine**: Centered the upload queue at the bottom with constrained width for better focus on wide monitors.

### 8. Haptic & Visual Feedback (v1.1.1 Supplemental)
- **Haptic Patterns**: Created `HapticUtil` for Android to provide distinct tactile feedback:
  - **Light Tap**: Short pulse for all standard buttons and navigation tabs.
  - **Success**: Long heavy vibration for pairing success and transfer completion.
  - **Error**: Double short pulse for pairing failures and transfer errors.
- **Visual Pulse Animations**: 
  - **Mobile**: Dynamic green/red border pulse on `DeviceCard` (600ms) triggered by pairing results.
  - **Web**: Implemented CSS shake animations for errors and green flash-to-fade transitions for successful queue completions.
- **Connection Status**: Added a synchronized pulsing dot to the Web Dashboard status indicator for constant connection health awareness.
