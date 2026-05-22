# Rollback Instructions (v1.0.2)

To revert these architectural changes:

1. **Service Revert**: Restore `EchoCoreService.kt` to the version in v1.0.1 backup.
2. **NSD Revert**: Restore `NsdHelper.kt`.
3. **Web Revert**: Restore `WebShareServer.kt`.
4. **ViewModel Revert**: Restore `EchoViewModel.kt`.

*Note: As this update only touches backend logic, your UI layouts will remain unaffected by a rollback.*
