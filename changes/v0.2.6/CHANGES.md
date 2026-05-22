# Change Log - v0.2.6

## [0.2.6] - 2026-05-22
### Integrated File Grid & Transfer Analytics (UI 2.0)

This update replaces the legacy Receive tab with a high-performance local file browser, featuring a Material 3 grid layout, multi-selection capabilities, and real-time mesh transfer tracking.

#### Added
- `FilesScreen.kt`: The new primary destination for the Files tab, integrating directory navigation and real-time transfer progress.
- `FileGrid.kt`: Adaptive grid layout with specialized iconography for media, archives, and system objects.
- `FolderTree.kt`: Breadcrumb navigation header for fluid movement through the `echoSystem` directory structure.
- `FileActionsSheet.kt`: Unified management sheet for Rename, Move, Share, and Delete operations.
- **Selection Engine**: Multi-select logic in `EchoViewModel` with dedicated selection toolbar.

#### Updated
- `EchoViewModel.kt`: Added `currentDir`, `browserFiles`, and `selectedFiles` StateFlows. Integrated standard folder assurance (`Received`, `Sent`, `Shared`).
- `Navigation.kt`: Migrated the "Files" route to the new integrated `FilesScreen`.
- `build.gradle.kts`: Incremented version to `0.2.6` (v8).

#### Improved
- **Real-time Progress**: Added a dedicated section at the top of the Files tab to show ongoing mesh transfers with real-time progress bars.
- **Visual Feedback**: Selection state is now clearly indicated with primary-colored containers and checkmark badges.
- **Aesthetic Pairings**: Better use of M3 `surfaceColorAtElevation` for structural depth.
