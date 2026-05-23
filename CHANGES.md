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
- **Zero-Trust Access**: Encrypted PIN sequences and sensitive temporary authentication flags reside in `EncryptedSharedPreferences`.

### 5. Enhanced WebSocket & Restful Web Dashboard
- **Directory Nesting & Traversal Checks**: Redesigned `/web/files` to accept a nested directory `path` parameter allowing multi-level browsing inside `/echoSystem/` root. Implemented absolute canonical validation checks to fully block directory traversal attacks (`../` malicious sequences).
- **Mkdir / Rename REST Controls**: Implemented safe, validated endpoints for `/web/mkdir` and `/web/rename`.
- **WebSocket Synchronization Channel**: Upgraded `webSocket("/events")` to broadcast custom, lightweight JSON event formats:
  - `device_online` & `device_offline`
  - `file_changed`
  - `transfer_progress`
  - `transfer_started`, `transfer_completed`, and `transfer_failed`
- **Frontend Real-time Sync**: Updated Web `dashboard.html` with a New Folder action and updated `dashboard.js` with WebSocket-driven auto-mesh reload and responsive progress tracking.

### 6. Background Network Service Discovery (NSD Helper Refinements)
- **Corrected Identifier**: Registered and searched on standard multicast domain type `_localshare._tcp.` (replacing `_echoshare._tcp.`).
- **Rich Metadata Declarations**: Service registration now injects TXT records: `name` (android.os.Build.MODEL), unique `deviceId`, `fingerprint`, `version`, and `capabilities` map.
- **Graceful Offlining via Timer**: Implemented a thread-safe 30-second delay on `onServiceLost` using concurrent coroutine delay clocks. If service returns within 30s, the timer is cancelled and the node remains online; otherwise, it is safely demoted offline and events are sent.
- **File System Watch**: Built multiple concurrent `FileObserver` watches inside the persistent `EchoCoreService` lifecycle mapping to `Received`, `Sent`, and `Shared` storage folders respectively, automatically emitting JSON events to active WebSocket nodes.
