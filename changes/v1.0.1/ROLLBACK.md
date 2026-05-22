# Rollback Instructions (v1.0.1)

If you encounter issues after applying these changes, follow these steps to return to the previous state:

1. **Delete modified files**:
   - `/app/src/main/java/com/echosystem/localshare/service/EchoCoreService.kt`
   - `/app/src/main/java/com/echosystem/localshare/discovery/NsdHelper.kt`
   - `/app/src/main/java/com/echosystem/localshare/viewmodel/EchoViewModel.kt`
   - `/app/src/main/java/com/echosystem/localshare/web/WebShareServer.kt`
   - `/app/src/main/java/com/echosystem/localshare/ui/screens/LocalFileBrowser.kt`
   - `/app/src/main/java/com/echosystem/localshare/ui/screens/Screens.kt`
   - `/app/src/main/java/com/echosystem/localshare/ui/components/Components.kt`
   - `/app/src/main/assets/web/dashboard.html`

2. **Restore from Backup**:
   The `FILES/` folder in this stage contains the "New" versions. To undo, you would need the original files. 
   *(Note: The AI agent usually keeps the previous state in history, but manual backup is recommended before applying).*

3. **Clean Build**:
   Run `gradle clean` to ensure no stale build artifacts remain.
