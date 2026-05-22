# LocalShare v0.2.4: Navigation Architecture 2.0

## 🚀 Overview
Version 0.2.4 introduces a modern, stable navigation architecture based on Jetpack Compose Navigation and Material 3 principles. The legacy "Home/Send/Receive" model is replaced with a consolidated "Devices" model, placing local sharing at the center of the experience.

## ✨ Key Changes
- **Consolidated Devices Tab**: Merges discovery and radar modes into a unified "Nearby / Receive" interface.
- **Control Center Menu**: A new top-right context menu for registry, permissions, history, settings, and developer tools.
- **Persistent Bottom Navigation**: Stable M3 `NavigationBar` with consistent route state preservation.
- **Improved Scaffolding**: Uses `CenterAlignedTopAppBar` and `Scaffold` for better edge-to-edge support.
- **Typed Navigation Routes**: Full migration to type-safe (sealed class) route definitions in `AppScreens.kt`.

## 🛠 Structural Mapping
| Old Route | New Tab | Screen Composable |
| :--- | :--- | :--- |
| `home` | - | `PortalHomeScreen` (Moved) |
| `send` | `Devices` | `SendFileScreen` (Tab 0) |
| `receive` | `Devices` | `ReceiveRadarScreen` (Tab 1) |
| `webshare` | `WebShare` | `WebPortalScreen` |
| `history` | Control Center | `HistoryLedgerScreen` |
| `trusted_devices`| Control Center | `SecurityManager` |

## 📦 Modified Files
- `app/build.gradle.kts`: Bumped `versionCode` to 6, `versionName` to 0.2.4.
- `metadata.json`: Updated platform identity.
- `ui/navigation/AppScreens.kt`: Redefined navigation graph.
- `ui/screens/Screens.kt`: Massive overhaul of scaffold and nav host logic.
