# Flash Screen MIUIX Refactoring Implementation Plan

**Spec**: `docs/superpowers/specs/2026-06-22-flash-screen-miuix-design.md`
**Date**: 2026-06-22

---

## Task 1: Add Navigation3 Routes

**File**: `app/src/main/java/com/abk/kernel/ui/navigation3/Routes.kt`

Add two new parameterized Route data classes inside the `Route` sealed interface:

```kotlin
@Parcelize
@Serializable
data class FlashWorkflowDetail(val runId: Long) : Route

@Parcelize
@Serializable
data class FlashPrebuiltDetail(val releaseId: Long) : Route
```

**Verify**: File compiles, no duplicate Route names.

---

## Task 2: Create `FlashArtifactsMiuix.kt` — shared artifact components

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/flash/FlashArtifactsMiuix.kt`

Create the file with:

- **Imports**: MIUIX basics (`Card`, `Text`, `Button`, `ButtonDefaults`, `Icon`, `IconButton`, `LinearProgressIndicator`, `HorizontalDivider`, `MiuixTheme`, `overScrollVertical`, `scrollEndHaptic`)
- **Shared tag chip**: `MiuixTagChip(label, primary: Boolean)` — colored Box + MIUIX Text, same pattern as ModuleRepository
- **Shared artifact row**: `MiuixDownloadedOutputRow(artifact, onCopyPath, onInstall, onFlash, onDelete, allowRootActions)` — MIUIX Card row for each downloaded file with copy/install/flash/delete action buttons
- **Shared artifact source card**: `MiuixArtifactSourceCard(source, matchedLocal, downloadProgress, autoDownload, pendingAutoDownload, showDownloadCancelActions, onDownload, onCancelDownload, onCancelAutoDownload, onCopyPath, onInstall, onFlash, onDelete, allowRootActions)` — MIUIX Card with:
  - Artifact icon + name + type/size subtitle
  - Auto/manual download badge
  - Active download progress (LinearProgressIndicator)
  - Action buttons row (Download / Flash / Install)
  - Downloaded file rows via `MiuixDownloadedOutputRow`
- **Shared local artifact card**: `MiuixLocalOnlyArtifactCard(artifact, onCopyPath, onInstall, onFlash, onDelete, allowRootActions)` — MIUIX Card for downloaded files not linked to a remote source
- **Shared category section**: `MiuixWorkflowCategorySection(group, category, showDuration, createdAt, finishedAt, progress, downloadProgress, autoDownload, pendingAutoDownloadRunId, onDownload, onCancelDownload, onCancelAutoDownload, showDownloadCancelActions, onCopyPath, onInstall, onFlash, onDelete, allowRootActions)` — MIUIX Card wrapping category header + artifact rows
- **Shared download management card**: `MiuixWorkflowDownloadManagementCard(tasks, pendingRunId, pendingRunLabel, onCancelTask, onCancelPending)` — MIUIX Card with download task rows and cancel buttons

Import shared logic:
- `com.abk.kernel.ui.screens.flash.artifactIcon`
- `com.abk.kernel.ui.screens.flash.artifactTypeLabelRes`
- `com.abk.kernel.ui.screens.flash.flashButtonLabelRes`
- `com.abk.kernel.utils.DownloadUtils` (classifyCategory, classifyArtifact, formatSize, matchesDownloadedArtifact)

**Verify**: File compiles with all shared artifact components defined.

---

## Task 3: Create `FlashDialogsMiuix.kt` — all dialog conversions

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/flash/FlashDialogsMiuix.kt`

Create the file with:

- **Imports**: MIUIX `WindowDialog` (from `top.yukonga.miuix.kmp.window`), `Card`, `Text`, `Button`, `ButtonDefaults`, `TextButton`, `MiuixTheme`, `HorizontalDivider`, `Checkbox` (material3)
- All dialog composables (private or internal):

| Composable | Title | Content | Actions |
|---|---|---|---|
| `MiuixFlashConfirmDialog(onConfirm, onDismiss)` | "确认刷写" | Warning message Text | Cancel TextButton + Confirm TextButton (primary) |
| `MiuixInstallManagerConfirmDialog(onConfirm, onDismiss)` | "确认安装" | Warning message Text | Cancel + Confirm (primary) |
| `MiuixCancelBuildConfirmDialog(runId, onConfirm, onDismiss)` | "取消构建？" | Message Text | Cancel + "Yes, cancel" (error color) |
| `MiuixDeleteFileDialog(artifact, onConfirm, onDismiss)` | "删除文件" | File path in Text | Cancel + Delete (error) |
| `MiuixDeleteWorkflowDialog(group, hasRemote, onConfirm, onDismiss)` | "删除工作流" | Workflow info + Checkbox for remote delete + Checkbox for delete files | Cancel + Delete (error) |
| `MiuixDismissFailedRunDialog(hasDownloadedFiles, onConfirm, onDismiss)` | "移除失败记录" | Message + delete files Checkbox | Cancel + Remove |
| `MiuixBuildParameterSummaryDialog(group, summary, onDismiss)` | "参数详情" | Card with key-value pairs table | Close |
| `MiuixPrebuiltParameterSummaryDialog(release, summary, onDismiss)` | "参数详情" | Card with key-value pairs table | Close |
| `MiuixTerminalDialog(title, running, success, logLines, onClose, onReboot)` | Dynamic | Card with dark monospace log container | Close + Reboot (if success) |

Terminal dark container: derive from `MiuixTheme.colorScheme` — use `surface` with dark text in light mode, inverted in dark mode. `$` command lines rendered in `primary` color.

Import shared logic:
- `com.abk.kernel.ui.screens.flash.parseBuildParameterSummary` (from FlashDialogs.kt)
- `com.abk.kernel.ui.screens.flash.parsePrebuiltGkiParameterSummary` (from FlashDialogs.kt)
- `com.abk.kernel.data.model.BuildParameterSummary`

**Verify**: File compiles, all dialog composables defined.

---

## Task 4: Create `FlashScreenMiuix.kt` — main list page scaffold and state

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/FlashScreenMiuix.kt`

Create the file with:

- **Imports**: MIUIX basics, blur backdrop helpers, Navigation3 LocalNavigator, all flash/ sub-package imports
- **Entry composable** `FlashScreenMiuix(vm, outerPadding, onDetailPageVisibleChange)`:
  - Collect `state` from `vm.uiState`
  - State variables: `activeContentTab`, `filter`, `selectedRunId`, `selectedItem`, `deleteFileTarget`, `deleteWorkflowTarget`, `parameterTarget`, `prebuiltParameterTarget`, `showFlashConfirm`, `showInstallManagerConfirm`, `cancelConfirmRunId`, `showTerminal`, `terminalLog`, `terminalRunning`, `terminalSuccess`, `terminalTitle`, `terminalCanReboot`, `ghostFailedSheetRunId`
  - Derived values: `rootGranted`, `prebuiltOnlyMode`, `currentContentTab`, `remoteArtifacts`, `workflowDownloadedArtifacts`, `workflowActiveDownloads`, `workflowGroups`, `recentRunById`, `filterMenuExpanded`
  - Scroll behavior: `MiuixScrollBehavior()`
  - Blur backdrop: `rememberBlurBackdrop(state.miuixBlurEnabled)` + `getMiuixAppBarColor()`
  - Compute visible workflow groups (applying filter logic from `FlashWorkflowFilter`)

**Verify**: File compiles with entry point skeleton, no UI yet.

---

## Task 5: Add main list page UI to `FlashScreenMiuix.kt`

**File**: Same as Task 4, fill in the Scaffold body.

```
Miuix Scaffold {
  TopAppBar(
    title = if (rootGranted) "刷写 / 安装" else "文件",
    scrollBehavior = MiuixScrollBehavior(),
    color = barColor
  )
  LazyColumn(scrollEndHaptic, overScrollVertical, nestedScroll) {
    item: MiuixHeroCard (buildStatus, availableCount, downloadedCount, rootGranted)
    if (prebuiltEnabled && loggedIn) item: TabSwitcherRow
    // Workflow tab content
    // Prebuilt tab content
    item: Spacer(80.dp + outerPadding.bottom)
  }
}
```

**MiuixHeroCard**: MIUIX `Card` with status icon (tinted by build status), title ("产物中心"/"文件中心"), counts summary.

**TabSwitcherRow**: `Row` with two MIUIX `Button`s — equal width, selected = primary blue, unselected = surface.

**Workflow tab items**:
- Refresh + Filter row: `OutlinedButton(refresh)` + `IconButton(filter)` with `OverlayListPopup` anchoring filter options
- `MiuixWorkflowDownloadManagementCard` (if active downloads)
- `MiuixWorkflowRunCard` items (from FlashArtifactsMiuix) — each navigates to `navigator.push(Route.FlashWorkflowDetail(runId))`
- Empty states (filter empty / no artifacts / no files)

**Prebuilt tab items**:
- `MiuixPrebuiltReleaseListHeader` (count + refresh button)
- `MiuixPrebuiltReleaseCard` items — each navigates to `navigator.push(Route.FlashPrebuiltDetail(releaseId))`
- Local prebuilt files via `MiuixLocalOnlyArtifactCard`
- Empty / disabled states

**Callbacks**:
- `onDetailPageVisibleChange(true)` when pushing a detail Route
- `onDetailPageVisibleChange(false)` when the main list is visible (no detail pushed)

**Verify**: Main list renders with hero, tabs, workflow cards, prebuilt cards, filter works.

---

## Task 6: Add operation callbacks and dialogs to `FlashScreenMiuix.kt`

**File**: Same as Task 4, append.

- `copyDownloadedFilePath(item)` — clipboard copy + snackbar
- `requestInstallManager(item)` — set state for flash confirm dialog
- `requestFlash(item)` — set state for flash confirm dialog
- Flash/install root operations (delegate to `RootUtils` via scope.launch)
- Terminal execution logic (appendTerminalOutput pattern)
- All dialog state management and wiring to `FlashDialogsMiuix` composables

Wire up:
- `MiuixFlashConfirmDialog` → flash operation
- `MiuixInstallManagerConfirmDialog` → install manager
- `MiuixCancelBuildConfirmDialog` → `vm.cancelWorkflowRun()`
- `MiuixDeleteFileDialog` → `vm.deleteDownloadedArtifact()`
- `MiuixDeleteWorkflowDialog` → `vm.dismissFailedWorkflow()`
- `MiuixDismissFailedRunDialog` → dismiss ghost failed
- `MiuixBuildParameterSummaryDialog` / `MiuixPrebuiltParameterSummaryDialog`
- `MiuixTerminalDialog` → flash/install output

**Verify**: All operations (download, flash, install, copy, delete, cancel) trigger correctly, dialogs show and dismiss.

---

## Task 7: Create `FlashWorkflowDetailMiuix.kt` — workflow detail sub-page

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/flash/FlashWorkflowDetailMiuix.kt`

```kotlin
@Composable
fun FlashWorkflowDetailScreenMiuix(
    vm: MainViewModel,
    route: Route.FlashWorkflowDetail,
    outerPadding: PaddingValues
) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val runId = route.runId
    val group = /* find from workflowGroups */
    val activeRun = /* check if building */
    // ... state management, LaunchedEffect for workflow polling

    Miuix Scaffold {
      TopAppBar(
        title = "Workflow #$runId",
        navigationIcon = { BackIconButton { navigator.pop() } }
      )
      LazyColumn {
        if (building) {
          item: MiuixBuildingWorkflowDetail(run, group, progress, cancelling, ...)
        } else {
          item: MiuixWorkflowDetailHeader(group, onShowParameters, onDelete)
          items: MiuixWorkflowCategorySection for each category
        }
      }
    }
}
```

**MiuixBuildingWorkflowDetail**: MIUIX `Card` with progress info, current step, cancel button. Category progress cards below.

**MiuixWorkflowDetailHeader**: MIUIX `Card` with run info, workflow name, created/finished timestamps, parameter details button, delete button.

Uses `MiuixWorkflowCategorySection` from FlashArtifactsMiuix.

LaunchedEffect for workflow status polling (same polling logic as MD3 version — refresh artifacts while building, detect completion/cancellation/failure).

Crossfade between building and completed states using `AnimatedVisibility` with `fadeIn/expandIn(TopStart)` and `fadeOut/shrinkOut(TopStart)`.

**Verify**: Workflow detail renders for both building and completed workflows, category sections display correctly.

---

## Task 8: Create `FlashPrebuiltDetailMiuix.kt` — prebuilt detail sub-page

**New file**: `app/src/main/java/com/abk/kernel/miuix/ui/screens/flash/FlashPrebuiltDetailMiuix.kt`

```kotlin
@Composable
fun FlashPrebuiltDetailScreenMiuix(
    vm: MainViewModel,
    route: Route.FlashPrebuiltDetail,
    outerPadding: PaddingValues
) {
    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val releaseId = route.releaseId
    val release = state.prebuiltGkiReleases.firstOrNull { it.id == releaseId }
    // ... filter state, asset loading

    Miuix Scaffold {
      TopAppBar(
        title = "Prebuilt GKI",
        navigationIcon = { BackIconButton { navigator.pop() } }
      )
      LazyColumn {
        item: MiuixReleaseInfoCard(release)
        item: MiuixPrebuiltFilterCard(filter, onFilterChange, assetCount)
        item: MiuixAssetCountHeader(visibleCount, totalCount)
        items: MiuixPrebuiltAssetCard list (download/copy/flash actions)
      }
    }
}
```

**MiuixReleaseInfoCard**: Card with release tag, date, asset count, description snippet.

**MiuixPrebuiltFilterCard**: Card with minor version `OverlayDropdownPreference`, show-only-matching `SwitchPreference`.

**MiuixPrebuiltAssetCard**: Card per asset with name, type, size, download button, progress, local file rows.

Triggers `vm.loadPrebuiltGkiAssets(release)` on composition if not already loaded.

**Verify**: Prebuilt detail renders, filter works, download/flash actions available.

---

## Task 9: Wire up `MainActivity.kt`

**File**: `app/src/main/java/com/abk/kernel/MainActivity.kt`

Changes:

1. **MIUIX branch** (~line 887): Replace `FlashScreen(...)` with:
```kotlin
AbkTab.Flash -> com.abk.kernel.miuix.ui.screens.FlashScreenMiuix(
    vm = vm,
    outerPadding = contentPadding,
    onDetailPageVisibleChange = { flashDetailPageVisible = it }
)
```

2. **Entry provider** (after `Route.RuntimeModuleRepoSettings` entry): Add two new entries:
```kotlin
entry<Route.FlashWorkflowDetail> {
    val route = it as Route.FlashWorkflowDetail
    com.abk.kernel.miuix.ui.screens.flash.FlashWorkflowDetailScreenMiuix(
        vm = vm, route = route, outerPadding = contentPadding
    )
}
entry<Route.FlashPrebuiltDetail> {
    val route = it as Route.FlashPrebuiltDetail
    com.abk.kernel.miuix.ui.screens.flash.FlashPrebuiltDetailScreenMiuix(
        vm = vm, route = route, outerPadding = contentPadding
    )
}
```

3. **childPageVisible**: The `AbkTab.Flash` case already uses `flashDetailPageVisible`. No change needed — `FlashScreenMiuix` updates it via callback when pushing/popping Routes.

**Verify**: MIUIX theme loads FlashScreenMiuix, MD3 theme still loads original FlashScreen. Navigation3 routes registered.

---

## Task 10: Compile verification

- Run `./gradlew :app:compileDebugKotlin`
- Fix any import or type errors
- Pay special attention to:
  - MIUIX progress indicator API (`progress: Float?` not lambda)
  - `ButtonDefaults.buttonColors()` vs `textButtonColors()` distinction
  - `expandIn(Alignment.TopStart)` not `expandIn(Alignment.Top)` for AnimatedVisibility
  - `WindowDialog` import from `top.yukonga.miuix.kmp.window`
  - `Checkbox` still from `material3` (MIUIX has no Checkbox)
  - No `material3.*` imports in MIUIX files (except Checkbox and `Icons.Default.*`)

**Verify**: Build succeeds with exit code 0.

---

## Task 11: Integration verification

- **MIUIX theme path**:
  - Flash/Files tab shows MIUIX-styled hero card, tabs, workflow cards, prebuilt cards
  - Filter works with OverlayListPopup
  - Download management card shows progress
  - WorkflowRunCard tap → pushes FlashWorkflowDetail with slide animation + predictive back
  - PrebuiltReleaseCard tap → pushes FlashPrebuiltDetail with slide animation + predictive back
  - All dialogs render as WindowDialog
  - Terminal dialog shows monospace output in dark container
  - Bottom bar blur backdrop animates when detail page is pushed
- **MD3 theme path**:
  - Flash tab still shows original Material 3 Expressive screens
  - NavHost/NavController navigation unchanged
  - All existing dialogs and operations identical
- **Data operations**:
  - Download triggers correctly, progress shows
  - Flash/install delete operations work via root shell
  - Copy path copies to clipboard + shows snackbar
  - Cancel build sends cancel to GitHub
  - Delete workflow removes local files + cached records

---

## Execution Order

1. **Task 1** (Routes) — must be first, other tasks depend on Route existence
2. **Tasks 2-3** (shared components + dialogs) — independent of each other, can run in parallel
3. **Tasks 4-6** (main list page + state + operations) — sequential, each builds on previous
4. **Tasks 7-8** (detail sub-pages) — independent of each other, depend on Tasks 2-3
5. **Task 9** (MainActivity wiring) — depends on Tasks 1-8
6. **Task 10** (compile) — depends on all above
7. **Task 11** (verification) — depends on compile success

## Parallelization

- Tasks 2 and 3 can be done in parallel (independent files)
- Tasks 7 and 8 can be done in parallel (independent detail pages)
- Tasks 4-6 must be sequential (same file, each adds to it)
