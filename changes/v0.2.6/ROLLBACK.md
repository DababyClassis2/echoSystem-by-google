# Rollback Plan - v0.2.6

To revert to v0.2.5:

1.  **Gradle**: Reset `versionCode` to 7 and `versionName` to "0.2.5" in `app/build.gradle.kts`.
2.  **Metadata**: Restore v0.2.5 description in `metadata.json`.
3.  **Navigation**: Restore `LocalFileBrowser` route in `Navigation.kt`.
4.  **UI Components**: Delete `/ui/screens/files/` directory.
5.  **ViewModel**: Remove file browser States and methods (`navigateTo`, `toggleSelection`, etc.) from `EchoViewModel.kt`.
