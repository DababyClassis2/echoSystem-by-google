# Rollback Action Guide - Reverting to echoSystem v1.1.0

If for any runtime incompatibility or testing regression you need to rollback the echoSystem back to version `v1.1.0`, perform the following direct rollback procedures:

---

## Rollback Sequence (Step-by-Step)

### 1. Revert Database (Room to In-Memory / SharedPreferences)
- **Action**: Delete Room Database and Entity classes in `/app/src/main/java/com/echosystem/localshare/database/` and `/app/src/main/java/com/echosystem/localshare/repository/`.
- **Logic Revert**: Re-enable the simple in-memory `DeviceRegistry` list and standard `SharedPreferences` variables in `TrustManager.kt` and `PairingManager.kt` for storing pairing keys and pins respectively.
- **SQL Cleanup**: Remove `@Database` annotations and delete `EchoDatabase.kt`.
- **Dependency Removal**: In `app/build.gradle.kts` and `libs.versions.toml`, comment out or delete the Room KSP/Runtime dependencies:
  ```kotlin
  // build.gradle.kts
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.room.ktx)
  ```

### 2. Restore Original Web Server Routes
- **FileRoutes.kt**: Revert `/web/files` to return a flat JSON array of files without accepting a nested `path` query.
- **Upload / Download Streams**: Remove the manual chunk-based byte-array pipelining. Revert `/web/upload` to the simpler `call.receiveMultipart()` buffering and writing straight without manual progress flow feedback.
- **Delete mkdir & rename**: Remove the `/web/mkdir` and `/web/rename` endpoints from `FileRoutes.kt`. Re-declare any local data structures like `RenameRequest` instead of `FileRenameRequest`.

### 3. Revert Network Service Discovery (NsdHelper.kt)
- **Service Name Type**: Change the service registry type in `NsdHelper.kt` back to `_echoshare._tcp.` instead of `_localshare._tcp.`.
- **TXT Records**: Remote all `.setAttribute(...)` calls for `deviceId`, `name`, `fingerprint`, `version`, and `capabilities` inside of `performRegistration`.
- **onServiceLost Delay**: Remove the thread-safe `ConcurrentHashMap` with delay coroutine timers. Allow lost nodes to be removed instantaneously from the live scanning UI state flow.

### 4. Remove FileObserver & WebSocket Enhancements
- **Teardown FileObserver**: In `EchoCoreService.kt`, delete the `startFileObserver()` and `stopFileObserver()` methods and their invocations in the service startup / teardown lifecycles.
- **WebSocket Schema**: Revert `WebSocketRoutes.kt` back to standard events. Remove the custom stringified event JSON declarations of `device_online`, `device_offline`, and `file_changed`.

### 5. Restore Connection State Classes
- **Data Class**: Revert `ConnectionState` declaration in `ConnectionManager.kt` back to a simple, declarative inline data class instead of the M3-compliant polymorphic Sealed class syntax. Remove the custom `setDeviceOnline` update hooks.

### 6. Adjust Version Artifacts
- **File Reset**: Set the `version` field in `/VERSION.json` back to `"1.1.0"`. Set `releaseType` back to `"stable"`.
