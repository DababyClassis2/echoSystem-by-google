# echoSystem v0.2.8 - Web Dashboard 2.0

## Overview
Major overhaul of the Web Portal to provide a desktop-grade file management experience for paired browser sessions.

## Key Changes
- **Web UI 2.0**:
    - **Glassmorphism Theme**: Utilizes Tailwind CSS and backdrop-blur effects for a modern aesthetic.
    - **Two-Pane Layout**: Added a persistent sidebar for Folder Tree navigation (`Received`, `Sent`, `Shared`) and a responsive File Grid.
    - **Security Registry**: A dedicated admin panel for controlling mesh node permissions, blocking rogue devices, and renaming authorized endpoints.
- **Backend Enhancements**:
    - **Route Refactoring**: Migrated `/web/manage/*` to `/management/*` for cleaner API organization.
    - **Directory Awareness**: `/web/files`, `/web/upload`, `/web/download`, and `/web/delete` now support a `path` parameter, enabling full file system navigation within the `echoSystem` root.
    - **Permission Logic**: The UI now dynamically adapts based on the connected device's permissions (e.g., hiding deletion tools if the node lacks `DELETE_FILES`).

## Technical Implementation
- **Frontend**: Vanila HTML5, Tailwind CSS, Lucide Icons, and custom CSS variables for theming. No heavy frameworks used to maintain zero-latency responsiveness.
- **Security**: Mandatory `X-PIN` and `X-Device-Id` headers for all management operations, enforced by `PairingManager` and `TrustManager`.

## New Assets
- `app/src/main/assets/web/dashboard.html` (Redesigned)
- `app/src/main/assets/web/dashboard.css` (New)
- `app/src/main/assets/web/dashboard.js` (New)
