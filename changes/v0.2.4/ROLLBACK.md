# LocalShare v0.2.4: Rollback Strategy

If v0.2.4 prevents application launch or navigation, execute the following:

1. **Gradle Revert**: In `app/build.gradle.kts`, set `versionCode` back to 5.
2. **Main Entry Revert**: Open `MainActivity.kt` and change the `MainScreen` import back to `com.echosystem.localshare.ui.screens.MainScreen`.
3. **Module Deletion**: Delete `app/src/main/java/com/echosystem/localshare/ui/navigation/Navigation.kt`.
4. **File Restoration**: Use the v0.2.3 backups of `Screens.kt` and `AppScreens.kt` located in the `/backups/v0.2.3/` directory (if exists) or revert the surgical edits manually.
5. **Clean Build**: Run `gradle clean` (if possible) or just a fresh `compile_applet`.
