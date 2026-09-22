# Yuma UI Kit Component Guide

This guide defines the standard workflow and technical requirements for building, documenting, and testing UI components in the Yuma Design System (YDS 2.1).

---

## 1. Component Lifecycle & Creation Workflow

When creating a new UI Kit component (located under `ui/component/`), follow this 6-step workflow:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ 1. Use Tokens   ├────►│ 2. Add Motion   ├────►│3. Accessibility │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                         │
┌─────────────────┐     ┌─────────────────┐              │
│ 6. Documentation│◄────┤  5. Add Sample  │◄─────────────┘
└─────────────────┘     └─────────────────┘
```

---

## 2. Component Design Principles

### 2.1 Pure Statelessness
All Yuma UI components must be 100% stateless:
- **No Internal Business State:** Accept immutable state models or primitives (`title: String`, `isSelected: Boolean`).
- **Callback Hoisting:** Emit user interactions via lambda parameters (`onClick: () -> Unit`).
- **No Repositories or ViewModels:** Components must never reference ViewModels, UseCases, or data sources.

### 2.2 Token Usage Only
- **Colors:** Use YDS color tokens (`LocalYumaColors.current.*`, `MaterialTheme.colorScheme.*`). Never hardcode hex colors (`0xFF...`).
- **Typography:** Use YDS typography tokens (`MaterialTheme.typography.*`). Never use raw `TextStyle(...)` overrides.
- **Shapes & Radius:** Use YDS shape tokens (`SettingsDimensions.*CornerRadius`, `segmentedSettingsItemShape`, `segmentedPreferenceItemShape`).
- **Spacing & Outlines:** Use YDS dimension tokens (`SettingsDimensions.GlassBorderThickness` = 0.5.dp, `SettingsDimensions.SegmentedItemGap` = 1.5.dp).

---

## 3. Step-by-Step Implementation Guide

### Step 1: Define Stateless Interface & Parameters
```kotlin
@Composable
fun YumaSegmentedItem(
    title: String,
    subtitle: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Component rendering
}
```

### Step 2: Integrate YDS Motion, Group Lighting, & Modifier Chain
Always chain `.yumaClickable(...)` **before** `.yumaGlassCard(...)` so the entire card structure scales as a single cohesive unit, and supply `position = yumaSegmentPosition(index, count)` for seamless group lighting:
```kotlin
val shape = remember(index, count) { segmentedSettingsItemShape(index, count) }
val position = remember(index, count) { yumaSegmentPosition(index, count) }

Box(
    modifier = modifier
        .fillMaxWidth()
        .yumaClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
        .yumaGlassCard(
            shape = shape,
            position = position,
            strokeWidth = SettingsDimensions.GlassBorderThickness,
        )
        .clip(shape)
        .padding(horizontal = 18.dp, vertical = 12.dp)
) {
    // Row content
}
```

### Step 3: Enforce Accessibility
- High-contrast touch target: Minimum **48x48dp** (recommended **56dp+** for core player actions, **72dp** minimum height for preference rows).
- Semantics: Provide clear `contentDescription` for non-text components or decorative icons (`contentDescription = null`).

### Step 4: Add Multipreview Declarations
Provide Compose `@ThemePreviews` annotations for Light/Dark themes:
```kotlin
@ThemePreviews
@Composable
private fun YumaSegmentedItemPreview() {
    TestThemeWrapper {
        YumaSegmentedItem(
            title = "Account",
            subtitle = "Manage account settings",
            index = 0,
            count = 3,
            onClick = {}
        )
    }
}
```

### Step 5: Create Interactive Component Sample
Place usage examples in component documentation or sample code blocks showing state binding.

### Step 6: Register in the Component Inventory
Yuma UI Kit (`designsystem/.../ui/component/`) currently provides, beyond the preference family
(`PreferenceGroup`, `PreferenceEntry`, `SwitchPreference`, `EditTextPreference`, `SliderPreference`,
`NumberPickerPreference` in `Preference.kt`): `YumaMorphingHeader` (scroll-linked `lerp3` header morph),
`FloatingNavigationToolbar` with internal `FluidTabsContainer`, `GlassScaffold`, `ExpressivePullToRefreshBox`,
`LibraryFilterChipBar`, `YumaPreferences`, `SearchBar`, bottom-sheet family (`BottomSheet`, `BottomSheetPage`,
`BottomSheetMenu`), shimmer placeholders (`shimmer/`), haptics (`YumaHaptics`), and safe-area helpers (`YdsInsets`).
Reuse them before creating a new component.

---

## 4. Anti-Patterns Checklist

- ❌ Hardcoding `Color(0xFF121212)` or `dp` values outside `SettingsDimensions`.
- ❌ Placing `.yumaClickable(...)` after `.yumaGlassCard(...)` or inside inner layout containers (causes disconnected text-only scaling).
- ❌ Omitting `YumaSegmentPosition` when building grouped segmented rows (causes bright blinding joint lines).
- ❌ Putting ViewModel calls, Room DB operations, or Coroutine launches inside Composables.
- ❌ Missing `key` in `LazyColumn`/`LazyRow` items inside list components.
