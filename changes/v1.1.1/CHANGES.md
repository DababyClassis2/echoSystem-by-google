# echoSystem v1.1.1 - The "Crystal" Release

This release focuses on hardening the internal mesh architecture while polishing the user experience to production-grade standards. We have integrated 6 significant backend architectural refinements with 6 high-impact UI/UX improvements.

---

## 🏗️ Backend Refinements (by deepseek-System)

1. **Unified Connection Manager**: consolidated Wi-Fi, Hotspot, and Wi-Fi Direct detections into a single reactive StateFlow for reliable mesh joining.
2. **Streaming Transfer Engine**: implemented chunk-by-chunk stream pipelining for both upload and download, enabling stable multi-gigabyte file transfers without OOM crashes.
3. **Room Persistence Layer**: migrated from ephemeral RAM storage to a persistent SQLite database using Room, ensuring trusted nodes and transfer history survive reboots.
4. **RSA Secure Pairing**: introduced 2048-bit RSA key exchange within the hardware-backed AndroidKeyStore to verify identity before granting mesh access.
5. **FileSystem Extensions**: expanded the REST API to support remote folder creation (`mkdir`), renaming, and deep nested path navigation.
6. **Real-time WebSocket Events**: upgraded the web dashboard to use a persistent WebSocket channel for instantaneous progress bars and mesh state updates.

---

## ✨ UI/UX Refinements (by deepseek-UI)

1. **Tap-to-Preview Modals**: introduced full-screen inspection overlays in History and File lists for rapid content verification before opening.
2. **Pulse Animation Transitions**: added state-aware green/red border pulses and smooth layout entry transitions to provide life to the interface.
3. **Friendly Empty States**: replaced technical error codes with evocative illustrations and helpful copy to guide new users through their first sync.
4. **Nuanced Haptic Feedback**: developed a custom tactile system (`HapticUtil`) providing distinct vibrations for button taps, transfer successes, and pairing errors.
5. **Constrained Desktop Layout**: optimized the web dashboard for large monitors by constraining content to a focused 1152px container.
6. **Strict Typography Scale**: applied a rigorous Material 3 typographic hierarchy across Mobile and Web, improving scanability and hierarchy logic.

---

## 🛠️ Verification Checklist
- [x] Version bumped to 1.1.1 (Code: 12)
- [x] Android Permissions validated (VIBRATE, INTERNET, etc.)
- [x] Web Dashboard offline-ready (Tailwind/Lucide local assets)
- [x] Compile success on all modules
