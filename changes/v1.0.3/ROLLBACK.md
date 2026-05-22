# Rollback Instructions (v1.0.3)

If the backend instability persists, you can return to the v1.0.2 baseline by restoring these files:

1. `/app/src/main/java/com/echosystem/localshare/service/EchoCoreService.kt`
2. `/app/src/main/java/com/echosystem/localshare/discovery/NsdHelper.kt`
3. `/app/src/main/java/com/echosystem/localshare/web/WebShareServer.kt`
4. `/app/src/main/java/com/echosystem/localshare/viewmodel/EchoViewModel.kt`

*Note: Since this update is purely logic-based and removes UI/Resource changes, a rollback is safe and will not break your saved data or layouts.*
