# UI/UX Visual Identity & Component Summary

This document provides a blueprint of the current user interface and experience design for **echoSystem (v1.1.0)**, intended for the UI/UX engineering team to use as a baseline for polish and enhancements.

## 1. Mobile App (Android/Compose) Visual Hierarchy

The mobile application follows a **"Dark Industrial"** aesthetic, utilizing high-contrast surfaces and neon accents to evoke a sense of a secure mesh networking tool.

### Primary Screens
- **Radar (Devices Screen)**:
  - **Identity Header**: A prominent top section showing "My Node Identity" (IP Address) and the current "Shield Key" (Pairing PIN).
  - **Scanning Status Bar**: A dynamic surface that changes color based on network state (Primary Blue for scanning, Green for registered/discoverable, Red for error).
  - **Node List**: A scrollable `LazyColumn` of `DeviceCard` components. Each card displays device name, IP, and a "radar" icon.
  - **Bottom Action Sheet**: Selecting a device opens a comprehensive sheet with actions: *Send Files, Rename Registry entry, Block Node, or Pair*.
- **Storage (Files Screen)**:
  - **Folder Breadcrumbs**: A horizontal tree navigation at the top allowing users to step back through the `echoSystem` directory structure.
  - **Selection Toolbar**: When items are long-pressed, a "Mesh Selection" bar appears at the top (Primary Blue) with clear "Actions" and "Clear" buttons.
  - **Active Progress Banner**: An animated section that appears automatically when transfers are ongoing, showing filenames and linear progress bars.
  - **File Grid**: A responsive grid of files showing custom icons for folders vs. files.
- **Portal (WebShare Screen)**:
  - **Control Center**: Large "Ignite/Extinguish" toggle buttons for the local Ktor server.
  - **Branding**: Displays a large "Web Portal 2.0" header with a QR Code for instant browser pairing.
  - **Verification Key**: Shows a large, monospaced pairing PIN for browser-side authentication.
  - **Active Session Ledger**: A list of connected browser sessions with their IP and status.
- **Shield (Settings Screen)**:
  - **Identity Profile**: A card to customize the "Broadcast Name" (how the phone appears to others).
  - **Security Token Manager**: A dedicated card for regenerating the 6-digit Shield PIN.
- **History (Ledger Screen)**:
  - A chronological log of all incoming and outgoing transfer events.

---

## 2. Web Dashboard Layout (Browser)

The web dashboard is a single-page application built with **Tailwind CSS** and **Lucide Icons**, designed to feel like a desktop file manager.

### Layout Panels
- **Header**: Sticky h-20 bar with the "echoSystem" branding, real-time node status dot (Green/Red), global search input, and a "Registry" button for managing authorized peers.
- **Sidebar (Remote Storage)**:
  - **Folder Tree**: A vertical navigation panel showing the hierarchy of the shared `echoSystem` directory.
  - **Upload Zone**: A dashed-border "Drop files to sync" area at the bottom for drag-and-drop interactions.
- **Main Content Grid**:
  - **Breadcrumb Toolbar**: Shows the current path (e.g., `/root/documents`) and item count.
  - **Resource Grid**: A responsive flex-grid of "File Cards" with hover animations (translate-y-1) and glassmorphism effects.
- **Sync Engine (Footer Queue)**:
  - A persistent (hidden when empty) panel at the bottom center that lists active uploads/downloads with real-time percentage updates.

---

## 3. Aesthetic Specifications

### Color Palette
| Element | Hex Code | Purpose |
| :--- | :--- | :--- |
| **Cosmic Slate** | `#1A1C1E` | Background (App & Web) |
| **Cosmic Blue** | `#D1E4FF` | Primary Material Accents |
| **Indigo Mesh** | `#6366f1` | Web Dashboard Accent / Primary Buttons |
| **Metallic Surface**| `#2C3135` | Card Backgrounds |
| **Error Red** | `#F43F5E` | Alerts / Blocked Nodes |
| **Grid Slate** | `#020817` | Web Main Content background |

### Typography
- **Mobile**: Uses the system default font family (Inter/Roboto), with `FontWeight.Black` heavily applied to titles to create a "bold engineering" feel.
- **Web**: Uses **'Plus Jakarta Sans'** (imported via Google Fonts).
- **Monospace**: `JetBrains Mono` or system monospace is used for all PIN codes and IP addresses to emphasize technical accuracy.

### Spacing & Borders
- **Grid System**: 8dp/16dp standard padding.
- **Corner Radius**: 
  - Cards: `16dp` to `24dp` (Rounded-2xl/3xl).
  - Web Glass Panels: `16px` backdrop-filter blur.
  - Input Fields: `12dp` rounded corners.

---

## 4. Icons & Symbols
- **Mobile**: Exclusively uses **Material Symbols (M3)** via `androidx.compose.material.icons`.
- **Web**: Exclusively uses **Lucide Icons** (`lucide-js`). 
- **Consistency Note**: We map specific concepts (Radar -> Discovery, Shield -> Security, Box -> Files) across both platforms for mental model consistency.

---

## 5. Identified UX Pain Points (v1.1.0)
- **Navigation Jarring**: On mobile, the Selection Toolbar for files overlays the primary TopAppBar, which can feel abrupt.
- **Empty States**: Some screens use slightly clinical language ("Isolated Node", "Null Directory") which may be too technical for casual users.
- **Haptic Feedback**: Currently minimal; adding more distinct vibrations for "Success" vs. "Error" pairing would improve "perceived security."
- **File Previews**: Tapping a file in the grid currently does nothing; it requires a long-press for actions. A "Quick Look" feature is missing.
- **Manual Refresh**: Users must often manually hit the FAB to update discovery or file lists.
