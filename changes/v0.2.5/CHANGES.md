# Change Log - v0.2.5

## [0.2.5] - 2026-05-22
### Unified Devices Tab & Registry Actions (UI 2.0)

This update overhauls the Devices tab by consolidating discovery and "Receive Radar" modes into a single, powerful mesh management interface. It introduces full device registry actions including rename, block, and permission management.

#### Added
- `DevicesScreen.kt`: Consolidated landing page for the Devices tab.
- `DeviceCard.kt`: Material 3 device list items with status indicators and trust badges.
- `DeviceActionsSheet.kt`: Comprehensive bottom sheet for device management and permission toggles.
- `MyIdentityBanner`: Displays the local node's IP and Shield Key for easy discovery.
- `ScanningHeader`: Animated status bar showing the health of the NSD (Network Service Discovery) engine.
- `RenameDeviceDialog`: UI for aliasing devices in the local trust registry.
- `PairDeviceDialog`: Secure PIN entry for establishing initial trust links.

#### Updated
- `EchoViewModel.kt`: Exposed `nsdState` and `trustManager` registry flows. Incremented version to `0.2.5`.
- `Navigation.kt`: Removed legacy `DevicesTabScreen` (paging tabs) in favor of the new unified `DevicesScreen`.
- `metadata.json`: Updated app description to reflect mesh registry enhancements.

#### Improved
- **NSD Health Monitoring**: Added visual feedback for healthy, degraded, or offline network discovery states.
- **Permission Granularity**: Trust registry now supports per-device permission toggles (Browse, Upload, Download, Manage, Delete).
- **Consolidated UI**: Merged "Nearby" and "Receive" tabs into a single vertical scroll view for better ergonomics.
