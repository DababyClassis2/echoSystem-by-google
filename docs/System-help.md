# Blueprint Summary: echoSystem Android App (v1.1.0)

## a) Architecture Overview
- **Package Structure**:
  - `com.echosystem.localshare.core`: System-level coordination, error handling, performance tracking, and network mode management.
  - `com.echosystem.localshare.discovery`: Device discovery services using Network Service Discovery (NSD).
  - `com.echosystem.localshare.model`: Unified domain models for devices, transfers, and permissions.
  - `com.echosystem.localshare.security`: Security managers for pairing (PIN) and device trust/permissions.
  - `com.echosystem.localshare.server`: Ktor-based local web server implementation and API routing.
  - `com.echosystem.localshare.service`: `EchoCoreService` — the primary background foreground service hosting the portal engine.
  - `com.echosystem.localshare.ui`: Jetpack Compose screens and navigation hierarchy.
  - `com.echosystem.localshare.viewmodel`: Centralized state management using `EchoViewModel` following MVVM.
  - `com.echosystem.localshare.repository`: Data access layers for file operations and device registry.
- **Architectural Patterns**:
  - **MVVM**: Separation of UI (`ui`), Logic (`viewmodel`), and Data (`repository`).
  - **Observer Pattern**: Utilizes `ServerEventBus` and `CoreEventBus` (Kotlin Flows) for real-time system events.
  - **Supervisor Pattern**: `CoreSystemSupervisor` manages task stability and failover logic.
- **Data Layer**:
  - **Persistence**: `EncryptedSharedPreferences` for secure storage of trusted devices and permissions (`TrustManager`).
  - **File Storage**: Local file system interaction via `FileRepository`.
  - **In-Memory**: `DeviceRegistry` and `TransferEngineCore` maintain transient state for active sessions.
  - **Database**: Room dependencies included in Gradle, but no active `@Database` implementation found in current core logic.

## b) Network & Connection
- **Network Management**:
  - `NetworkModeManager` evaluates active interfaces to switch between **LAN**, **Wi-Fi Direct**, and **Hotspot** modes.
  - Implements a failover routine (`fallbackIfModeFails`) to cycle network channels if connectivity degrades.
- **Device Discovery**:
  - **NSD (Network Service Discovery)**: `NsdHelper.kt` registers and discovers services on port 8080 using the `_http._tcp` service type.
- **Pairing & Trust**:
  - **PIN Mechanism**: `PairingManager` generates and verifies numeric PINs.
  - **Device Trust**: `TrustManager` tracks trusted devices, blocked status, and specific permissions (Browse, Download, Upload, Delete) per device ID.
  - **Persistent trust**: Trusted status is stored securely in encrypted preferences.

## c) File Transfer
- **Transfer Protocol**:
  - **Ktor HTTP**: Uses Ktor Client (CIO) and Ktor Server (Netty) for high-performance multipart file streaming.
  - **Multipart FormData**: Files are transmitted as multipart items in POST requests.
- **Implementation Flows**:
  - **Send**: `EchoViewModel.sendMultipleFilesToDevice` uses Ktor Client to push files to remote device endpoints.
  - **Receive**: Managed via `POST /web/upload` (for web clients) and `/transfer/upload` (for app peers) in `FileRoutes.kt`.
- **Progress Tracking**:
  - `TransferEngineCore` tracks active jobs using a `Job` map.
  - Real-time progress is emitted through `ServerEventBus` using `onUpload` (client) or manual stream measurement (server).
- **Queuing & Capabilities**:
  - Supports **Pause**, **Resume**, **Cancel**, and **Prioritization** of transfer tasks.
  - In-memory queue managed by `TransferEngineCore`.

## d) Web Dashboard
- **Web Server**:
  - **Ktor (Netty engine)** running inside `EchoCoreService` on port **8080**.
- **API Endpoints**:
  - `GET /`: Serves the primary web portal dashboard.
  - `GET /{filename}`: Serves static assets (CSS/JS) for 100% offline usage.
  - `GET /web/files`: JSON list of files in the shared directory with metadata.
  - `GET /web/download`: Secure file download stream.
  - `POST /web/upload`: Multipart file upload handler with progress reporting.
  - `POST /web/delete`: Remote file/folder deletion.
  - `GET /web/permissions`: Query allowed capabilities for the current web client.
  - `WS /events`: WebSocket endpoint for real-time transfer progress and peer notifications.
- **Frontend State**:
  - Modern HTML/Tailwind/JS dashboard located in `src/main/assets/web/`.
  - Offline-ready with local copies of Tailwind and Lucide icons.

## e) UI Screens (Android)
- **Compose Screens**:
  - `OnboardingScreen`: Initial setup and permissions.
  - `DevicesScreen`: Nearby peer discovery and pairing management.
  - `FilesScreen`: Unified file browser for the local `echoSystem` directory.
  - `WebShareScreen`: Controls for the web portal (QR code, PIN, active sessions).
  - `HistoryScreen`: Log of recent incoming and outgoing transfers.
  - `SettingsScreen`: General app configuration.
  - `DeveloperScreen`: Internal logs and system diagnostics.
- **Navigation Structure**:
  - Uses **Jetpack Navigation Compose** with a bottom navigation bar layout.
  - Routes defined as type-safe objects/strings (e.g., `ROUTE_DEVICES`, `ROUTE_WEBSHARE`).

## f) Permissions & Storage
- **Permissions**:
  - Requests standard storage permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` or Media permissions for API 33+).
  - Handles `CHANGE_WIFI_MULTICAST_STATE` for discovery.
- **Storage Strategy**:
  - **Primary Root**: `/storage/emulated/0/echoSystem/` is the enforced directory for all shared content.
  - Subfolders: Automatically creates `Received`, `Sent`, and `Shared` directories within the root.
  - File I/O is managed centrally via `FileRepository`.

## g) Dependencies
- **Core Frameworks**:
  - **Jetpack Compose**: Modern declarative UI.
  - **Ktor (Server & Client)**: Networking and web portal hosting.
  - **Dagger Hilt**: Dependency Injection.
  - **Kotlin Coroutines & Flow**: Concurrency and reactive events.
- **Key Libraries**:
  - **Room**: Included for database persistence (potential for history/metadata).
  - **ZXing**: Used for QR code generation in WebShare.
  - **Security Crypto**: Used for `EncryptedSharedPreferences`.
  - **Coil**: Image loading.
  - **Moshi/Kotlinx Serialization**: JSON processing.

## h) Current Gaps
- **Room Integration**: Database dependencies are present, but core logic (History/Trust) still relies on SharedPreferences or memory; a full DAO/Entity implementation for transfer history is not currently active.
- **Persistence of Queue**: The transfer engine queue is in-memory; tasks may not automatically resume after a service kill without manual trigger.
- **TODOs**: Reference to `data_extraction_rules.xml` for backup/restore rules.
