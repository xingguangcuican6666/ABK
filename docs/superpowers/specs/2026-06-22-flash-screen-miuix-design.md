# Flash Screen MIUIX Refactoring Design

## Overview

Refactor the `FlashScreen` (both Flash mode with root granted and Files mode without root)
from Material 3 Expressive style to MIUIX style. Create parallel MIUIX screen files while
preserving the original MD3 version intact. Migrate detail page navigation from
NavHost/NavController to Navigation3 sub-pages (Route-based). Convert all dialogs to MIUIX
WindowDialog.

## Scope

- **Flash mode** (root granted): Build artifact management — workflow run list,
  workflow detail with category sections, download/flash/install/delete operations,
  terminal output, building progress.
- **Files mode** (root not granted): File browsing — same artifact/download list but
  with flash/install actions gated behind root checks.
- **Prebuilt GKI tab**: Release list, asset browsing with filter, download/flash.

Both modes are refactored together. The original `FlashScreen.kt` (MD3) and its sub-files
in `ui/screens/flash/` are preserved as-is for the MD3 theme path.

## File Changes

### New Files

| File | Purpose |
|---|---|
| `app/src/.../miuix/ui/screens/FlashScreenMiuix.kt` | MIUIX main list page (workflows + prebuilt tabs) |
| `app/src/.../miuix/ui/screens/flash/FlashWorkflowDetailMiuix.kt` | MIUIX workflow detail sub-page |
| `app/src/.../miuix/ui/screens/flash/FlashPrebuiltDetailMiuix.kt` | MIUIX prebuilt GKI detail sub-page |
| `app/src/.../miuix/ui/screens/flash/FlashArtifactsMiuix.kt` | MIUIX artifact cards, download rows, local file cards |
| `app/src/.../miuix/ui/screens/flash/FlashDialogsMiuix.kt` | MIUIX WindowDialog conversions (flash confirm, install confirm, delete, terminal, etc.) |

### Modified Files

| File | Change |
|---|---|
| `app/src/.../ui/navigation3/Routes.kt` | Add `FlashWorkflowDetail(runId: Long)` and `FlashPrebuiltDetail(releaseId: Long)` Route data classes |
| `app/src/.../MainActivity.kt` | Wire MIUIX branch to `FlashScreenMiuix`; register new Route entries in `entryProvider` |

### Untouched Files

- `FlashScreen.kt` (original MD3 version)
- All `ui/screens/flash/*.kt` sub-files (MD3 composables preserved)
- `MainViewModel.kt` and all data model/repository files
- `FlashWorkflowFilter.kt` (pure filter logic, shared)

## Architecture

```
FlashScreenMiuix (entry point — AbkTab.Flash in MIUIX branch)
├── Miuix Scaffold + TopAppBar (blur) + LazyColumn
├── Hero Card (build status, available/downloaded counts)
├── Tab Switcher (Workflows / Prebuilt GKI) — Miuix segmented buttons
├── Workflow Tab
│   ├── Refresh + Filter row (MIUIX OverlayListPopup)
│   ├── WorkflowDownloadManagementCard (active downloads + pending auto-download)
│   └── WorkflowRunCard list (MIUIX Card) / empty states
└── Prebuilt Tab
    ├── PrebuiltReleaseListHeader
    └── PrebuiltReleaseCard list (MIUIX Card) / empty states + local files

navigator.push(Route.FlashWorkflowDetail(runId))
└── FlashWorkflowDetailScreenMiuix
    ├── Building state: progress, elapsed time, category breakdown, cancel
    └── Completed state: category sections, artifact cards with actions

navigator.push(Route.FlashPrebuiltDetail(releaseId))
└── FlashPrebuiltDetailScreenMiuix
    ├── Release info header card
    ├── Filter card (minor version, show-only-matching)
    └── Asset card list (download/copy/flash actions)
```

## Navigation3 Routes

```kotlin
// Routes.kt — add 2 parameterized routes
@Parcelize
@Serializable
data class FlashWorkflowDetail(val runId: Long) : Route

@Parcelize
@Serializable
data class FlashPrebuiltDetail(val releaseId: Long) : Route
```

## Component Mapping

| MD3 Component | MIUIX Replacement | Notes |
|---|---|---|
| `Scaffold` (MD3) | `top.yukonga.miuix.kmp.basic.Scaffold` | |
| `ExpressiveTopBar` | `TopAppBar` + `MiuixScrollBehavior` | With blur via `BlurredBar` + `rememberBlurBackdrop` |
| `Card` / `CardDefaults` (MD3) | `top.yukonga.miuix.kmp.basic.Card` | |
| `Button` / `OutlinedButton` / `FilledTonalButton` | MIUIX `Button` + `ButtonDefaults.buttonColors()` | Primary: `color = MiuixTheme.colorScheme.primary, contentColor = Color.White` |
| `AlertDialog` (MD3) | `WindowDialog` from `top.yukonga.miuix.kmp.window` | |
| `ExpressiveSectionCard` | MIUIX `Card` with internal title/description | |
| `AssistChip` / `ElevatedFilterChip` | MIUIX styled `Box` + `Text` (colored background pill) | Same tag chip pattern as ModuleRepository |
| `Checkbox` | Keep `material3.Checkbox` | MIUIX lacks Checkbox; tint via `MiuixTheme.colorScheme.primary` |
| `DropdownMenu` / `ExposedDropdownMenuBox` | `OverlayListPopup` from MIUIX | For filter dropdowns |
| `CircularProgressIndicator` (MD3) | `top.yukonga.miuix.kmp.basic.CircularProgressIndicator` | `progress: Float?` (not lambda), `colors: ProgressIndicatorColors` |
| `LinearProgressIndicator` (MD3) | `top.yukonga.miuix.kmp.basic.LinearProgressIndicator` | Same API difference |
| `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` | `MiuixScrollBehavior()` | |
| `NavHost` / `NavController` / `composable()` | Navigation3 `navigator.push(Route)` | Predictive back gesture support |
| `FlashDetailBackSurface` (custom) | Removed — Navigation3 handles back natively | |
| `BoxWithConstraints` + child page overlay | Removed — Navigation3 routing handles this | |

### Text Typography Mapping

| MD3 Style | MIUIX Style |
|---|---|
| `MaterialTheme.typography.titleLarge` | `MiuixTheme.textStyles.title4` |
| `MaterialTheme.typography.titleMedium` | `MiuixTheme.textStyles.subtitle` |
| `MaterialTheme.typography.bodyMedium` | `MiuixTheme.textStyles.main` |
| `MaterialTheme.typography.bodySmall` | `MiuixTheme.textStyles.body2` |
| `MaterialTheme.typography.labelSmall` | `MiuixTheme.textStyles.body2` (adjusted size) |

### Color Mapping

| MD3 Token | MIUIX Token |
|---|---|
| `MaterialTheme.colorScheme.primary` | `MiuixTheme.colorScheme.primary` |
| `MaterialTheme.colorScheme.onSurface` | `MiuixTheme.colorScheme.onSurface` |
| `MaterialTheme.colorScheme.onSurfaceVariant` | `MiuixTheme.colorScheme.onSurfaceVariantSummary` |
| `MaterialTheme.colorScheme.error` | `MiuixTheme.colorScheme.error` |
| `MaterialTheme.colorScheme.surfaceContainer` | `MiuixTheme.colorScheme.surface` |
| `MaterialTheme.colorScheme.secondaryContainer` | `MiuixTheme.colorScheme.secondaryContainer` |

## Main List Page

### Page Skeleton

```
Miuix Scaffold {
  TopAppBar(
    title = if (rootGranted) "刷写 / 安装" else "文件",
    scrollBehavior = MiuixScrollBehavior(),
    actions = { /* refresh icon optional */ }
  )
  LazyColumn(
    contentPadding bottom = 80.dp + outerPadding,
    verticalArrangement = spacedBy(10.dp),
    modifier = .scrollEndHaptic() .overScrollVertical() .nestedScroll(scrollBehavior)
  ) {
    item: HeroCard
    if (prebuiltEnabled && loggedIn) item: TabSwitcher
    if (Workflows tab) {
      item: Refresh + Filter row
      if (activeDownloads) item: WorkflowDownloadManagementCard
      items: WorkflowRunCard list (or empty states)
    }
    if (PrebuiltGki tab) {
      item: PrebuiltReleaseListHeader
      items: PrebuiltReleaseCard list (or empty states)
      items: LocalPrebuiltFiles
    }
    item: Spacer(80.dp)
  }
}
```

### Hero Card

```
Card {
  Row {
    Icon(status icon, 48.dp, tinted by build status color)
    Spacer(16.dp)
    Column(weight=1) {
      Text("产物中心" / "文件中心", title4)
      Text("$available 源产物 / $downloaded 已下载", body2, onSurfaceVariantSummary)
    }
  }
}
```

### Tab Switcher

MD3 uses two `ElevatedFilterChip`. MIUIX version uses a `Row` with two MIUIX
`Button` components (compact style, `insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)`):
- Selected: `ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary, contentColor = Color.White)`, `showIndication = false`
- Unselected: `ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surface, contentColor = MiuixTheme.colorScheme.onSurfaceSecondary)`, standard border

Implementation: two `Button` composables in a `Row` with `horizontalArrangement = Arrangement.spacedBy(8.dp)`, each with `Modifier.weight(1f)` for equal width.

### WorkflowRunCard

```
Card(onClick = { navigator.push(Route.FlashWorkflowDetail(runId)) }) {
  Column(padding = 16.dp) {
    Row {
      Column(weight=1) {
        Text(runTitle, title4, onSurface)
        Text(meta — workflow name, run number, timestamp, body2, onSurfaceVariantSummary)
      }
      StatusChip(building/completed/failed — colored Text)
    }
    if (showKernelBuildChips) {
      Row(horizontalScroll) {
        TagChip(kernel variant, primary color)
        TagChip(SUSFS, secondary color)
        if (showParameters) ArrowRight icon (clickable → parameter dialog)
      }
    }
    if (hasCategories) {
      HorizontalDivider
      CategoryProgressCard sections (compressed)
    }
  }
}
```

### PrebuiltReleaseCard

```
Card(onClick = { navigator.push(Route.FlashPrebuiltDetail(releaseId)) }) {
  Column(padding = 16.dp) {
    Row {
      Column(weight=1) {
        Text(release tag, title4)
        Text(published date + asset count, body2, onSurfaceVariantSummary)
      }
      Icon(ArrowForwardIos, 16.dp)
    }
  }
}
```

### Filter

MD3 uses `DropdownMenu` + `DropdownMenuItem`. MIUIX uses `OverlayListPopup`
anchored to a filter `IconButton` in the toolbar row. Same filter dimensions:
- Kernel type (ReSukiSU / SukiSU / Official / None)
- Manager type (Release / Dev)
- Workflow state (running / finished)

### Empty States

```
Card {
  Column(centered, padding = 24.dp) {
    Icon(Inbox/CloudDownload/CloudOff, 48.dp, onSurfaceSecondary)
    Spacer(12.dp)
    Text(title, subtitle)
    Spacer(4.dp)
    Text(hint, body2, onSurfaceVariantSummary)
  }
}
```

### Download Management Card

```
Card {
  Column {
    Text("当前下载", title4)
    Text(hint, body2, onSurfaceVariantSummary)
    tasks.forEach { task →
      Row {
        Column(weight=1) {
          Text(artifact name, main)
          LinearProgressIndicator(progress = task.progress / 100f)
        }
        IconButton(Cancel) { vm.cancelDownload(task.key) }
      }
    }
    if (pendingAutoDownload) {
      Row {
        Text("等待自动下载", body2)
        Button("停止", onClick = { vm.cancelAutoDownloads(runId) })
      }
    }
  }
}
```

## Workflow Detail Sub-Page

### Route

```kotlin
entry<Route.FlashWorkflowDetail> {
    FlashWorkflowDetailScreenMiuix(vm = vm, route = it as Route.FlashWorkflowDetail)
}
```

### Page Structure

```
Miuix Scaffold {
  TopAppBar(
    title = "Workflow #$runId",
    navigationIcon = { BackIconButton { navigator.pop() } }
  )
  LazyColumn {
    if (building) {
      item: BuildingWorkflowDetail
    } else {
      item: WorkflowDetailHeader
      items: CategorySection for each artifact category
    }
  }
}
```

### Building State

All composables below are **new MIUIX rewrites** (not imports from MD3 `FlashWorkflow.kt`).

```
Column {
  Card {
    Text("正在构建", title4)
    Text(workflow name + elapsed time, body2, onSurfaceVariantSummary)
    Miuix LinearProgressIndicator(progress = buildProgress.overall / 100f)
    Text(current step name, body2)
    if (cancelling) Text("正在取消…", body2, error color)
  }
  // Per-category progress — new Miuix composable in FlashWorkflowDetailMiuix.kt
  buildProgress.categories.forEach { cat →
    Card {
      Row {
        Icon(category icon)
        Text(category name, subtitle)
        Text("$completed/$total", body2, end-aligned)
      }
      Miuix LinearProgressIndicator(progress = cat.progress / 100f)
    }
  }
  Button("取消构建", ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error, contentColor = Color.White))
}
```

### Completed State — Category Sections

```
Card {
  Column {
    Row {
      Icon(category icon)
      Text(category name, subtitle)
      Text(elapsed time, body2, end-aligned)
    }
    HorizontalDivider
    remoteArtifacts.forEach { source →
      ArtifactSourceCard(source, matchedLocal, actions...)
    }
    localOnly.forEach { artifact →
      LocalOnlyArtifactCard(artifact, actions...)
    }
  }
}
```

### ArtifactSourceCard

```
Card {
  Column {
    Row {
      Icon(artifact icon, 20.dp)
      Column(weight=1) {
        Text(artifact name, main)
        Text(type + size + source, body2, onSurfaceVariantSummary)
      }
      if (autoDownload) Badge("自动")
    }
    // Download progress (if active)
    if (downloading) LinearProgressIndicator(progress%)
    // Action buttons
    Row(end-aligned) {
      if (downloadAvailable) Button("下载", icon=Download)
      if (flashable && rootGranted) Button("刷写", primary)
      if (installable && rootGranted) Button("安装")
    }
    // Downloaded files
    downloadedFiles.forEach { file →
      DownloadedOutputRow(file, copyPath, install, flash, delete)
    }
  }
}
```

## Prebuilt Detail Sub-Page

### Route

```kotlin
entry<Route.FlashPrebuiltDetail> {
    FlashPrebuiltDetailScreenMiuix(vm = vm, route = it as Route.FlashPrebuiltDetail)
}
```

### Page Structure

```
Miuix Scaffold {
  TopAppBar(
    title = "Prebuilt GKI",
    navigationIcon = { BackIconButton { navigator.pop() } }
  )
  LazyColumn {
    item: ReleaseInfoCard (tag, date, description snippet)
    item: FilterCard (minor version dropdown, show-only-matching toggle)
    item: VisibleAssetCountHeader
    items: PrebuiltAssetCard list
  }
}
```

### PrebuiltAssetCard

```
Card {
  Column {
    Row {
      Icon(artifact icon, 20.dp)
      Column(weight=1) {
        Text(asset name, main)
        Text(type + size + release tag, body2)
      }
    }
    if (downloading) {
      LinearProgressIndicator(progress%)
      Text("下载 $progress%", body2, primary)
    }
    Row(end-aligned) {
      if (downloadAvailable) Button("下载", icon=Download)
      if (flashable && rootGranted) Button("刷写", primary)
      if (localFile) CopyPathButton
    }
    downloadedFiles.forEach { file →
      DownloadedOutputRow(file, copyPath, install, flash, delete)
    }
  }
}
```

## Dialogs

All MD3 `AlertDialog` → MIUIX `WindowDialog` + `Card` content + `Row` with `TextButton`.

| Dialog | Title | Content | Actions |
|---|---|---|---|
| Flash Confirm | "确认刷写" | Warning message | Cancel + Confirm (primary) |
| Install Manager Confirm | "确认安装" | Warning message | Cancel + Confirm (primary) |
| Cancel Build Confirm | "取消构建？" | Message | Cancel + "Yes, cancel" (error) |
| Delete File | "删除文件" | File path | Cancel + Delete (error) |
| Delete Workflow | "删除工作流" | Workflow info + remote checkbox | Cancel + Delete (error) |
| Dismiss Failed Run | "移除失败记录" | Message + files checkbox | Cancel + Remove |
| Build Parameters | "参数详情" | Key-value table in Card | Close |
| Prebuilt Parameters | "参数详情" | Key-value table in Card | Close |
| Terminal | "终端" / "正在执行" | Dark monospace log container | Close + Reboot (if success) |

### Terminal Dialog

```
WindowDialog(title = running ? "正在执行 · $label" : "终端") {
  Card {
    Surface(darkContainer based on theme) {
      Column(verticalScroll, monospace) {
        logLines → Text(labelSmall, monospace)
        // "$ command" lines in primary color
        // Other lines in onSurface
      }
    }
  }
  Row {
    if (running) Text("执行中", disabled)
    else {
      Button("关闭")
      if (success) Button("重启", error color, icon=RestartAlt)
    }
  }
}
```

## MainActivity Integration

### MIUIX Branch

```kotlin
AbkTab.Flash -> FlashScreenMiuix(
    vm = vm,
    outerPadding = contentPadding,
    onDetailPageVisibleChange = { flashDetailPageVisible = it }
)
```

### Entry Provider

```kotlin
entry<Route.FlashWorkflowDetail> {
    FlashWorkflowDetailScreenMiuix(vm = vm, route = it as Route.FlashWorkflowDetail)
}
entry<Route.FlashPrebuiltDetail> {
    FlashPrebuiltDetailScreenMiuix(vm = vm, route = it as Route.FlashPrebuiltDetail)
}
```

### Bottom Bar

The `flashDetailPageVisible` state already exists and is used for the blur backdrop
slide-out animation. `FlashScreenMiuix` updates it via the callback when
pushing/popping Routes — same pattern as `ModuleRepositoryScreenMiuix`.

### MD3 Branch

**Untouched.** MD3 continues using `FlashScreen` with its own `NavHost`/`NavController`.

## Data Layer

No changes. All ViewModel methods, data models, repositories, and logic are reused as-is:

- `vm.uiState.artifacts`, `vm.uiState.downloadedArtifacts`, `vm.uiState.activeDownloadTasks`
- `vm.uiState.recentRuns`, `vm.uiState.prebuiltGkiReleases`, `vm.uiState.prebuiltGkiAssetsByReleaseId`
- `vm.loadRecentRuns()`, `vm.downloadArtifact()`, `vm.cancelDownload()`, `vm.cancelAutoDownloads()`
- `vm.loadWorkflowJobs()`, `vm.refreshWorkflowArtifacts()`, `vm.cancelWorkflowRun()`
- `vm.dismissFailedWorkflow()`, `vm.deleteDownloadedArtifact()`, `vm.clearAllDownloadedArtifacts()`
- Flash/install/delete operations via `RootUtils` (same shell commands as MD3)
- `DownloadUtils.classifyCategory()`, `classifyArtifact()`, `formatSize()`, `shouldAutoDownload()`
- `buildWorkflowGroups()` and all routing/classification helpers from `FlashRouting.kt`
- `FlashWorkflowFilter` data model and filter logic

## Shared Code (from existing flash/ package)

These imports work directly in MIUIX files because they are pure logic (no composable dependency):

- `com.abk.kernel.ui.screens.flash.buildWorkflowGroups`
- `com.abk.kernel.ui.screens.flash.artifactIcon`
- `com.abk.kernel.ui.screens.flash.artifactTypeLabelRes`
- `com.abk.kernel.ui.screens.flash.flashButtonLabelRes`
- `com.abk.kernel.ui.screens.flash.hasDownloadedFilesForRun`
- `com.abk.kernel.utils.FlashWorkflowFilter` (full object)
- `com.abk.kernel.utils.DownloadUtils` (full object)
- `com.abk.kernel.viewmodel.mergeWorkflowActiveDownloads`

## Success Criteria

1. MIUIX theme shows fully MIUIX-styled Flash/Files screens (both root and no-root modes)
2. MD3 theme continues showing original Material 3 Expressive screens unchanged
3. Detail pages navigate via Navigation3 with predictive back gesture support
4. All dialogs use MIUIX WindowDialog style
5. All existing operations (download, flash, install, copy path, delete, cancel build) work identically
6. Bottom bar blur backdrop animates correctly when detail sub-page is pushed
7. No regression in MD3 theme path
8. Filter system works with MIUIX dropdown (same filter options and visual indication)
