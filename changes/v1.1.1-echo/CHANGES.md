# echoSystem v1.1.1-echo Stagging Report

This release consolidates the "Registry" backend with a refined Material 3 UI/UX.

## Critical Improvements
1. **Unified Engine**: All transfers now route through `TransferEngineCore` with SHA-256 integrity checks.
2. **Atmospheric UI**: Every empty state is now a starting point, not a dead end.
3. **Pipelining**: Stream chunks are adaptively sized based on available JVM Heap.
4. **Haptic Logic**: Tactile confirmation for all peer events.
5. **Web Desktop Refined**: Dashboard is now centered and responsive.

## Files Staged
- /core/* (Connection, Engine, Recovery)
- /ui/screens/* (Files, History, Devices)
- /assets/web/dashboard.html
- /util/HapticUtil.kt
- /viewmodel/EchoViewModel.kt

## Build Status
- **Android**: Success (v1.1.1-echo)
- **Web**: Validated (v1.1.1-echo)
