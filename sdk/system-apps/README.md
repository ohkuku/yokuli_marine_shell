# System .ykl applications

`catalog.json` is the source for Yokuli's built-in `.ykl` packages. Running `python3 sdk/tools/package_extensions.py` writes each immutable package plus a SHA-256 index into `app-shell/src/main/assets/ykl/system`. The APK signature authenticates these resources; the SHA index detects inconsistent package resources and is not an independent publisher trust chain.

All built-in applications are resolved through `YklPackageCatalog` and launched through `YklPackageScreen`. The same catalog contains user-installed `.ykl` applications. It owns package identity, root entry, route ownership and execution backend. `WpShellRuntime` derives its catalog and task launch identity from these records. Existing launcher IDs and root tokens are preserved to retain the user's tiles, tasks and Back relationships.

There are two backends:

- `host-kotlin`: trusted system components compiled into the signed host APK. `SystemYklApps` is a fixed component allowlist and adapts each package's own subpages to its actual Compose implementation. Maps and 3D renderers retain their existing lifecycle and resource ownership. These packages cannot be changed or removed by an imported file.
- `web`: JavaScript or compiled Kotlin/JS applications with local packaged assets and a capability-based SDK. User import refuses `host-kotlin`, `component`, `hostApp` and the reserved `com.yokuli` package namespace. No DEX, APK, class-name reflection or arbitrary native module loader exists.

To add a built-in app, add its compiled `AppId`/component to `SystemYklApps`, add the corresponding manifest with nonoverlapping owned routes to `catalog.json`, regenerate packages, and compile the host. Do not add a branch to `WpShellExperience`: that file has one package runtime entry. Keep domain data in Core and page-local state in the Shell task visit. A packaged system component is not permission to create a second sensor owner or database.

The native UI libraries are the actual Gradle modules `:core:design` (theme, typography, W10 controls, motion/brand) and `:ui:shell-compose` (task input, transitions, launcher, W10 shell behavior). Existing application rows and page headers are shared through `app-shell/.../rebuild/ui/Metro.kt`. System package components use these same production libraries; there is no separate preview-only SDK implementation.

Installable JS and Kotlin/JS applications use the public `Yokuli.ui` library served from `/_sdk/yokuli.js` and `/_sdk/yokuli.css`: page, section, metrics, field, switch, radio choice group, swipeable pivot, buttons and source/age/error presentation. `system.apps()` enumerates both system and installed packages; `navigation.open(packageId)` opens their root through the existing task graph. Foreground control is paused while the Shell covers or switches an app.
