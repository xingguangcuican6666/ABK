# Module Repository MIUIX Refactoring Implementation Plan

**Spec**: `docs/superpowers/specs/2026-06-22-module-repository-miuix-design.md`
**Date**: 2026-06-22

---

## Task 1: Add Navigation3 Routes

**File**: `app/src/main/java/com/abk/kernel/ui/navigation3/Routes.kt`

- Add two new Route data objects inside the `Route` sealed interface:

```kotlin
@Parcelize
@Serializable
data object BuildModuleRepoSettings : Route

@Parcelize
@Serializable
data object RuntimeModuleRepoSettings : Route
```

**Verify**: File compiles, no duplicate Route names.

---

## Task 2: Create `ModuleRepositoryScreenMiuix.kt` — entry point and shared components

**File**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/ModuleRepositoryScreenMiuix.kt`

Create the new file with:

- **Imports**: MIUIX basics (`Scaffold`, `TopAppBar`, `Card`, `Text`, `Button`, `ButtonDefaults`, `MiuixScrollBehavior`, `Icon`, `IconButton`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TextButton`, `MiuixTheme`, `WindowDialog`, `overScrollVertical`, `scrollEndHaptic`, `MiuixIcons`, `Back`)
- **Entry function** `ModuleRepositoryScreenMiuix(vm, mode, outerPadding, onRepositoryPageVisibleChange)` that dispatches to BUILD_ABK or RUNTIME_STANDARD content
- **Shared components** (private):
  - `MiuixModuleSearchField(value, onValueChange)` — MIUIX Card wrapping BasicTextField + search icon
  - `MiuixModuleTagChip(label, primary)` — colored Box + MIUIX Text
  - `MiuixModuleActionButton(icon, label, enabled, onClick)` — MIUIX Button with icon
  - `MiuixModuleInitialLoading()` — centered CircularProgressIndicator + body text
- **Data helpers** (private, reused from original):
  - `MergedRuntimeCatalogModule` / `MergedBuildCatalogModule` data classes
  - `mergeRuntimeCatalogModules()` / `mergeBuildCatalogModules()` merge functions
  - `matchesQuery()` filter extensions
  - `metaLine()` composable helpers
  - All the locale label functions (`buildRepoTitleLabel`, `runtimeRepoTitleLabel`, etc.)

**Verify**: File compiles with all shared components defined.

---

## Task 3: Add BUILD_ABK mode content

**File**: Same as Task 2, append to the file.

Add `BuildModuleRepositoryScreenMiuix` composable:

- `Scaffold` with `TopAppBar` (title from locale, settings icon → `navigator.push(Route.BuildModuleRepoSettings)`)
- `LazyColumn` with:
  - Search field item
  - Refresh indicator (LinearProgressIndicator)
  - Loading / empty / module card items
  - Bottom spacer (80.dp)
- Module card: MIUIX Card with name, meta, description, tag chips, action buttons (Open + Add to Build)
- Manage `pendingCatalogModule` state for stage selection and module set dialogs
- `onRepositoryPageVisibleChange(true)` when pushing settings route, `false` on pop

**Verify**: BUILD_ABK mode renders module list, cards show correct data.

---

## Task 4: Add BUILD_ABK dialogs

**File**: Same as Task 2, append to the file.

Add dialog composables:

- `BuildModuleStageSelectionDialogMiuix` — WindowDialog with:
  - Card containing module info + Checkbox list for stages
  - Row bottom: "All Stages" / "Cancel" / "Add Selected" (primary)
- `BuildModuleSetSelectionDialogMiuix` — WindowDialog with:
  - Card containing description + scrollable children list with nested stage Checkboxes
  - Row bottom: "Cancel" / "Add Selected" (primary)
- Both use `material3.Checkbox` (MIUIX has no Checkbox) tinted with `MiuixTheme.colorScheme.primary`

**Verify**: Dialogs render, checkboxes work, callbacks fire correctly.

---

## Task 5: Add RUNTIME_STANDARD mode content

**File**: Same as Task 2, append to the file.

Add `RuntimeModuleRepositoryScreenMiuix` composable:

- `Scaffold` with `TopAppBar` (title from locale, settings icon → `navigator.push(Route.RuntimeModuleRepoSettings)`)
- `LazyColumn` with search, refresh indicator, loading/empty/module cards
- Module card: MIUIX Card with name, meta, description, tag chips (id, API, verified, multi-source), action buttons (Open + Install)
- Manage `pendingInstallModule`, `installDialogVisible`, `installRunning`, `installSuccess`, `installLog` state
- `startInstall()` function reused from original (download + root shell + install)
- `onRepositoryPageVisibleChange` same pattern as BUILD_ABK

**Verify**: RUNTIME_STANDARD mode renders, install flow starts correctly.

---

## Task 6: Add RUNTIME_STANDARD dialogs

**File**: Same as Task 2, append to the file.

Add dialog composables:

- `RuntimeModuleInstallConfirmDialogMiuix` — WindowDialog with:
  - Card: module name (title4), meta (body2), source, zipUrl (body2, maxLines=4), description, risk warning
  - Row bottom: "Cancel" / "Install" (primary, UploadFile icon)
- `RuntimeModuleInstallProgressDialogMiuix` — WindowDialog with:
  - Title: running ? "Installing" : "Install Module"
  - Card with dark terminal surface: monospace log lines, `$` commands in primary color
  - Row bottom: running → disabled "Running" text; done → "Close" + success ? "Reboot" (error color)
  - Terminal container color derived from `MiuixTheme.colorScheme` light/dark detection

**Verify**: Confirm dialog shows module info, progress dialog displays terminal output, reboot button works.

---

## Task 7: Create BUILD settings sub-page screen

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/BuildModuleRepoSettingsScreenMiuix.kt`

```kotlin
@Composable
fun BuildModuleRepoSettingsScreenMiuix(vm: MainViewModel) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = buildRepoCentralLabel(context),
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(MiuixIcons.Back, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(verticalScroll, padding=20dp, spacing=16dp) {
            // Add repo Card: URL input + Add/RefreshAll buttons
            // Repo list or empty state
            // Per-repo Cards: name, url, module count, skipped, error, indexUrl, Refresh/Delete buttons
        }
    }
}
```

- Reuse locale label functions from ModuleRepositoryScreen
- Use MIUIX Card for each section
- URL input: BasicTextField inside Card (same pattern as search field)
- Delete button uses error-colored text
- Refresh button shows CircularProgressIndicator when refreshing

**Verify**: Screen renders, can add/refresh/delete build module repositories.

---

## Task 8: Create RUNTIME settings sub-page screen

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/RuntimeModuleRepoSettingsScreenMiuix.kt`

Same structure as Task 7 but for runtime repositories:
- Calls `vm::addRuntimeModuleRepository`, `vm::refreshRuntimeModuleRepository`, etc.
- Uses runtime locale labels

**Verify**: Screen renders, can add/refresh/delete runtime module repositories.

---

## Task 9: Wire up `MainActivity.kt`

**File**: `app/src/main/java/com/abk/kernel/MainActivity.kt`

Changes:

1. **Import** `ModuleRepositoryScreenMiuix` (no import needed if using fully qualified name in existing pattern)

2. **MIUIX branch** (line ~866): Replace `ModuleRepositoryScreen` with `ModuleRepositoryScreenMiuix`:
```kotlin
AbkTab.Modules -> com.abk.kernel.miuix.ui.screens.ModuleRepositoryScreenMiuix(
    vm = vm,
    mode = if (state.runtimeNavigationEnabled) {
        com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
    } else {
        com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
    },
    outerPadding = contentPadding,
    onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
)
```

3. **Entry provider** (after `Route.BuildQueue` entry): Add two new entries:
```kotlin
entry<Route.BuildModuleRepoSettings> {
    com.abk.kernel.miuix.ui.screens.BuildModuleRepoSettingsScreenMiuix(vm = vm)
}
entry<Route.RuntimeModuleRepoSettings> {
    com.abk.kernel.miuix.ui.screens.RuntimeModuleRepoSettingsScreenMiuix(vm = vm)
}
```

**Verify**: MIUIX theme shows new screens, MD3 theme unchanged.

---

## Task 10: Compile verification

- Run `./gradlew :app:compileDebugKotlin`
- Fix any import or type errors
- Verify no warnings related to new files

**Verify**: Build succeeds with exit code 0.

---

## Task 11: Integration verification

- Verify MIUIX theme path:
  - Module tab shows MIUIX-styled cards, TopAppBar, search field
  - BUILD_ABK mode: cards with "Add to Build" action, stage selection dialog
  - RUNTIME_STANDARD mode: cards with "Install" action, install confirm/progress dialogs
  - Settings icon pushes sub-page with slide animation + predictive back
  - Bottom bar blur animation when settings sub-page is visible
- Verify MD3 theme path:
  - Module tab still shows original Material 3 Expressive screens
  - All existing overlays and dialogs work identical
- Verify data operations:
  - Search filters modules correctly
  - Add/delete/refresh repositories works in both modes
  - Module install downloads and installs via root shell

---

## Execution Order

1. **Task 1** (Routes) — must be first, other tasks depend on Route existence
2. **Tasks 2-6** (main screen file) — sequential, each task builds on previous
3. **Tasks 7-8** (settings sub-pages) — independent of each other, depend on Task 1
4. **Task 9** (MainActivity wiring) — depends on Tasks 2-8
5. **Task 10** (compile) — depends on all above
6. **Task 11** (verification) — depends on compile success

## Parallelization

- Tasks 7 and 8 can be done in parallel (independent settings screens)
- Tasks 2-6 must be sequential (same file, each adds to it)
