# Rollback Plan - v0.2.5

To revert to v0.2.4:

1.  **Gradle**: Reset `versionCode` to 6 and `versionName` to "0.2.4" in `app/build.gradle.kts`.
2.  **Metadata**: Restore v0.2.4 description in `metadata.json`.
3.  **Navigation**: Restore `DevicesTabScreen` and its paged tab logic in `Navigation.kt`.
4.  **UI Components**: Delete `/ui/screens/devices/` directory.
5.  **ViewModel**: Remove `nsdState` flow and revert `pairingManager` visibility to private in `EchoViewModel.kt`.
