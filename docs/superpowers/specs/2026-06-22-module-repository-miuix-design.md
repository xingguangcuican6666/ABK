# Module Repository Screen MIUIX Refactoring Design

## Overview

Refactor the `ModuleRepositoryScreen` (both BUILD_ABK and RUNTIME_STANDARD modes) from
Material 3 Expressive style to MIUIX style. Create parallel MIUIX screen files while
preserving the original MD3 version intact. Migrate the repository settings overlay
to Navigation3 sub-pages. Convert dialogs to MIUIX WindowDialog.

## Scope

- **BUILD_ABK mode**: Build-time module catalog for selecting external modules to add to
  kernel builds.
- **RUNTIME_STANDARD mode**: Runtime standard module repository for downloading and
  installing Magisk/SukiSU modules.

Both modes are refactored. The original `ModuleRepositoryScreen.kt` (MD3) is preserved
as-is for the MD3 theme path.

## File Changes

### New Files

| File | Purpose |
|---|---|
| `app/src/.../miuix/ui/screens/ModuleRepositoryScreenMiuix.kt` | MIUIX module repository screen (both modes) |

### Modified Files

| File | Change |
|---|---|
| `app/src/.../ui/navigation3/Routes.kt` | Add `BuildModuleRepoSettings` and `RuntimeModuleRepoSettings` Route data objects |
| `app/src/.../MainActivity.kt` | Wire MIUIX branch to `ModuleRepositoryScreenMiuix`; register new Route entries in `entryProvider` |

### Untouched Files

- `ModuleRepositoryScreen.kt` (original MD3 version)
- `MainViewModel.kt` and all data model/repository files
- `RuntimeCoordinator.kt`

## Architecture

```
ModuleRepositoryScreenMiuix (entry point)
├── mode == BUILD_ABK
│   └── BuildModuleRepositoryScreenMiuix
│       ├── Module list (LazyColumn with search, cards, empty state)
│       └── navigator.push(Route.BuildModuleRepoSettings)
│           └── BuildModuleRepoSettingsScreenMiuix
└── mode == RUNTIME_STANDARD
    └── RuntimeModuleRepositoryScreenMiuix
        ├── Module list (LazyColumn with search, cards, empty state)
        └── navigator.push(Route.RuntimeModuleRepoSettings)
            └── RuntimeModuleRepoSettingsScreenMiuix
```

## Component Mapping

| MD3 Component | MIUIX Replacement |
|---|---|
| `Scaffold` (MD3) | `top.yukonga.miuix.kmp.basic.Scaffold` |
| `ExpressiveTopBar` | `TopAppBar` + `MiuixScrollBehavior` |
| `Card` (MD3, custom shape/elevation) | `top.yukonga.miuix.kmp.basic.Card` |
| `Button` / `OutlinedButton` (MD3) | MIUIX `Button` + `ButtonDefaults` |
| `ExpressiveSectionCard` | MIUIX `Card` with internal title/description layout |
| `CompactModuleSearchField` | MIUIX `Card` wrapping `BasicTextField` + search icon |
| `CompactModuleActionButton` | MIUIX `Button` (compact, with icon) |
| `ModuleTagChip` | MIUIX `Text` inside colored `Box` with `MiuixTheme.colorScheme` |
| `ExpressiveStatusChip` | MIUIX `Text` with colored text from `MiuixTheme.colorScheme` |
| `ShimmerLinearProgress` / `LoadingIndicator` | Keep MD3 `CircularProgressIndicator` (MIUIX library lacks native equivalent; MIUIX theme context handles colors) |
| `AlertDialog` (MD3) | MIUIX `WindowDialog` |

### MD3 Compatibility Notes

- **Checkbox**: Keep `material3.Checkbox`. MIUIX library does not provide a Checkbox
  component. Color adapts via `MiuixTheme.colorScheme.primary` when in MIUIX context.
- **WindowDialog**: From `top.yukonga.miuix.kmp.extra`. Renders in MIUIX window style
  within MIUIX theme context. MD3 version never invokes this code path.
- **Loading indicators**: MD3 `CircularProgressIndicator` used with `MiuixTheme.colorScheme`
  tint for visual consistency.
- **Terminal dialog**: Dark container derived from `MiuixTheme.colorScheme` light/dark
  detection.

## Module List Page (Both Modes)

### Page Skeleton

```
Miuix Scaffold {
  TopAppBar(
    title = mode-specific title,
    scrollBehavior = MiuixScrollBehavior(),
    actions = {
      IconButton(settings icon) → navigator.push(settings Route)
    }
  )
  LazyColumn(contentPadding bottom = 80.dp) {
    item: SearchField (MIUIX Card + BasicTextField)
    item: RefreshIndicator (when refreshing)
    if (loading) { item: LoadingState }
    else if (empty) { item: EmptyState }
    else { items: ModuleCard list }
    item: Spacer(80.dp)  // bottom bar clearance
  }
}
```

### Module Card (MIUIX Card)

```
Card {
  Column {
    Row {
      Column(weight=1) {
        Text(name, title4, onSurface)
        Text(meta, body2, onSurfaceVariantSummary)  // version, author
      }
      // Multi-source indicator (if sources > 1)
    }
    if (description.isNotBlank()) {
      Text(description, body2, maxLines=3)
    }
    Row(horizontalScroll) {
      TagChip(id/name, primary color)
      TagChip(stage or API, secondary color)
      TagChip(verified/added, secondary)
      TagChip(source count, secondary)
    }
    Row(end-aligned) {
      Button(open repo, icon)
      Button(install/add, primary, icon)
    }
  }
}
```

### Tag Chip (MIUIX-styled)

```
Box(
  background = primary/secondary color with alpha,
  padding = horizontal 8, vertical 2
) {
  Text(label, body2, color = appropriate text color)
}
```

### Empty State

```
Card {
  Column(centered) {
    Icon(Extension, onSurfaceSecondary, 48.dp)
    Spacer(12.dp)
    Text(message, subtitle)
    Spacer(4.dp)
    Text(hint, body2, onSurfaceVariantSummary)
    Button(manage repos, text-only style)
  }
}
```

### Search Field

```
Card {
  Row(verticalCenter, padding=12) {
    Icon(Search, 18.dp)
    Spacer(10.dp)
    Box(weight=1) {
      if (empty) Text(placeholder)
      BasicTextField(singleLine, body2)
    }
  }
}
```

## Repository Settings Sub-Pages (Navigation3)

### Route Registration

```kotlin
// Routes.kt
@Parcelize @Serializable
data object BuildModuleRepoSettings : Route

@Parcelize @Serializable
data object RuntimeModuleRepoSettings : Route
```

### Screen Structure (Shared Skeleton)

```
Scaffold {
  TopAppBar(
    title = "Central Repository" / "Manage Repositories",
    navigationIcon = { BackIconButton { navigator.pop() } }
  )
  Column(verticalScroll, padding=20dp, spacing=16dp) {
    // Add repository section
    Card {
      SectionTitle(icon=Dns)
      SearchBar(URL input, singleLine)
      Row {
        Button("Add", primary)
        Button("Refresh All")
      }
    }
    // Existing repositories
    if (repositories.isEmpty()) {
      Card { EmptyHintSection }
    } else {
      repositories.forEach { repo →
        Card {
          Row { Icon(Dns) + Column(title=repo.name, subtitle=repo.url) }
          StatusText(module count, primary)
          if (skipped > 0) StatusText(skipped, error)
          if (error != null) Text(error, error color)
          Text(indexUrl, body2)
          Row {
            Button("Refresh", icon or CircularProgressIndicator)
            Button("Delete", error color)
          }
        }
      }
    }
    Spacer(80.dp)
  }
}
```

## Dialogs

### Install Confirm Dialog (RUNTIME_STANDARD)

```
WindowDialog(title = "Confirm Install") {
  Card {
    Text(module name, title4)
    Text(meta line, body2)
    Text(source, body2)
    Text(zipUrl, body2, maxLines=4)
    if (description) Text(description)
    Text(risk warning, warning color)
  }
  Row {
    Button("Cancel")
    Button("Install", primary, icon=UploadFile)
  }
}
```

### Install Progress Terminal Dialog (RUNTIME_STANDARD)

```
WindowDialog(title = running ? "Installing" : "Install Module") {
  // Status icon: running=none, success=CheckCircle, failure=Error
  Card {
    Surface(darkContainer based on theme) {
      Column(verticalScroll, monospace font) {
        logLines → Text(labelSmall, monospace)
        // "$ command" lines in primary color
        // Other lines in onSurface
      }
    }
  }
  Row {
    if (running) {
      Text("Running", disabled)
    } else {
      Button("Close")
      if (success) Button("Reboot", error color, icon=RestartAlt)
    }
  }
}
```

### Stage Selection Dialog (BUILD_ABK)

```
WindowDialog(title = "Select Stage") {
  Card {
    Text(module name, title4, SemiBold)
    if (version or description) Text(combined, body2)
    supportedStages.forEach { stage →
      Row {
        Checkbox(checked, enabled = !alreadyAdded)
        Text(stage + recommended? + added?, body2)
      }
    }
  }
  Row {
    Button("All Stages")
    Button("Cancel")
    Button("Add Selected", primary, enabled = selectedStages.isNotEmpty())
  }
}
```

### Module Set Multi-Select Dialog (BUILD_ABK)

```
WindowDialog(title = module set name) {
  Card {
    if (description) Text(description, body2)
    Column(heightIn max=420.dp, verticalScroll) {
      children.forEach { child →
        Row {
          Checkbox(child selected)
          Column(weight=1) {
            Text(child name, SemiBold)
            if (description) Text(child description, body2)
            if (selected) {
              child.supportedStages.forEach { stage →
                Row {
                  Checkbox(stage selected)
                  Text(stage + recommended?, body2)
                }
              }
            }
          }
        }
      }
    }
  }
  Row {
    Button("Cancel")
    Button("Add Selected", primary, enabled = any child selected with valid stage)
  }
}
```

## MainActivity Integration

### MIUIX Branch

```kotlin
AbkTab.Modules -> ModuleRepositoryScreenMiuix(
    vm = vm,
    mode = if (state.runtimeNavigationEnabled) {
        ModuleRepositoryMode.RUNTIME_STANDARD
    } else {
        ModuleRepositoryMode.BUILD_ABK
    },
    outerPadding = contentPadding,
    onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
)
```

### Entry Provider

```kotlin
entry<Route.BuildModuleRepoSettings> {
    BuildModuleRepoSettingsScreenMiuix(vm = vm)
}
entry<Route.RuntimeModuleRepoSettings> {
    RuntimeModuleRepoSettingsScreenMiuix(vm = vm)
}
```

### Bottom Bar Adaptation

The `moduleRepositoryPageVisible` state variable already exists in MainActivity.
`ModuleRepositoryScreenMiuix` updates it via `onRepositoryPageVisibleChange` when
pushing/popping settings Routes. This controls the blur backdrop slide-out animation
on the floating bottom bar.

List pages use `contentPadding(bottom = 80.dp)` + `outerPadding.calculateBottomPadding()`
for proper spacing under the floating MIUIX navigation bar.

## Navigation Flow

```
Tab: Modules
  └── ModuleRepositoryScreenMiuix
        ├── BUILD_ABK list
        │     └── push(BuildModuleRepoSettings) → BuildModuleRepoSettingsScreenMiuix
        └── RUNTIME_STANDARD list
              └── push(RuntimeModuleRepoSettings) → RuntimeModuleRepoSettingsScreenMiuix
```

Both settings pages use `navigator.pop()` for back navigation.
Navigation3 provides predictive back gesture, slide transitions, and state preservation.

## Data Layer

No changes. All ViewModel methods, data models, repositories, and merge/filter logic
are reused as-is:

- `vm.uiState.buildModuleRepositories`
- `vm.uiState.runtimeModuleRepositories`
- `vm.addBuildModuleRepository()` / `vm.addRuntimeModuleRepository()`
- `vm.deleteBuildModuleRepository()` / `vm.deleteRuntimeModuleRepository()`
- `vm.refreshBuildModuleRepository()` / `vm.refreshRuntimeModuleRepository()`
- `vm.refreshAllBuildModuleRepositories()` / `vm.refreshAllRuntimeModuleRepositories()`
- `vm.addCustomExternalModulesFromUrl()`
- `vm.replaceModuleSetSelection()`
- `vm.checkCustomExternalModuleMetadata()`

## Success Criteria

1. MIUIX theme shows MIUIX-styled module repository screens (both modes)
2. MD3 theme continues showing original Material 3 Expressive screens unchanged
3. Repository settings pages navigate via Navigation3 with predictive back gesture
4. All dialogs use MIUIX WindowDialog style in MIUIX theme
5. All existing module operations (add/delete/refresh/install/search) work identically
6. Bottom bar blur backdrop animates correctly when settings sub-page is pushed
7. No regression in MD3 theme path
