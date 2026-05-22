# Rollback Plan - v0.2.7

## Reverting to v0.2.6
To revert the WebShare UI 2.0 changes:

1.  **Gradle**: Revert `versionCode` to `8` and `versionName` to `"0.2.6"`.
2.  **Navigation**: Revert `Screen.WebShare.route` to use `WebPortalScreen(viewModel)` instead of `WebShareScreen()`.
3.  **Files**: 
    - Remove the `ui/screens/webshare/` directory.
    - Revert changes to `WebShareServer.kt` (remove `getActiveSessionIps`).
    - Revert changes to `WebShareViewModel.kt` (restore old simple constructor and logic).
4.  **Metadata**: Restore v0.2.6 description in `metadata.json`.

## Impact
Rolling back will remove the QR code display and session tracking functionality.
