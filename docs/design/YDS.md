# Yuma Design System (YDS)

**Version:** 2.1  
**Status:** Stable Foundation  

---

## 1. Philosophy

Yuma is a design system centered around music as the primary content. It establishes a strict set of constraints and rules to produce a lightweight, deep, and aesthetically cohesive interface.

The interface avoids generic, monolithic Material 3 templates with flat, continuous lists. Yuma's visual identity is built on segmented glass geometry, accented matte icon badges, breathable whitespace, and tactile micro-interactions.

---

## 2. Visual Language & Character

* **Calmness:** The interface never overwhelms the user or distracts from the listening experience.
* **Depth & Geometry:** Depth is established via subtle translucent surfaces (`glassBackground`) and a 0.5dp hairline outline (`glassBorder`).
* **Soft Geometry:** Composite corner radii (22dp on outer group corners, 5dp on inner joints) visually unify distinct rows into a cohesive group.
* **Low Visual Noise:** Total elimination of divider lines (`Dividers`) in settings lists and selection sheets. Separation is achieved strictly through inter-row gaps (`SegmentGap = 1.5dp`) and structural padding.
* **Physicality:** Every interactive element delivers tactile press feedback via a spring-animated scale down to `0.96f`.

---

## 3. Design Principles

1. **Hierarchy Through Scale:**
   * **Preference Rows:** Fixed minimum height of **72dp**.
   * **Primary Actions:** Recommended height of **56dp+**.
   * **Standard Interactive Controls:** Minimum touch target of **48×48dp**.
   * **Icon Badges:** Container size of **32dp / 46dp**.
2. **Segmented Glass Structure:** Settings rows and integration cards are composed as independent segmented glass buttons, grouped into functional blocks separated by `SegmentGap`.
3. **Surface Hierarchy (Strict Separation of Surfaces):**
   * **Solid Surfaces:** Opaque tonal containers (`surface`, `surfaceContainer`) for main content surfaces where translucency is unnecessary.
   * **Static Translucent Glass:** Translucent fill (`glassBackground`) + 0.5dp outline (`glassBorder`, `SettingsDimensions.GlassBorderThickness`) **strictly without backdrop blur**. Applied to segmented settings lists and cards. Modal bottom sheets and selection dialogs utilize opaque solid surfaces (`surfaceContainerHigh`).
   * **Backdrop Blur Glass:** Background blur + tonal overlay. Applied **exclusively** to floating overlays (Bottom Player Bar, floating FAB, modal backdrops).
4. **Color Driven:** Dynamic Material 3 Expressive palette generation extracted from the current track's album art for accents, switch thumbs, and active indicators.
5. **No Divider Lines:** Divider lines are omitted in preference lists. Group boundaries are defined solely by card geometry and borders.

---

## 4. Foundations (Design Tokens)

All dimensions, paddings, and radii must be referenced directly from design tokens (`SettingsDimensions` / `SettingsAnimations` / `YdsInsets` in `:designsystem`, plus layout constants in `constants/Dimensions.kt`). Arbitrary hardcoded values are prohibited.

### Spacing
* **`SegmentGap`:** `1.5dp` (inter-row gap within a segmented group)
* **`Section`:** `12dp` (spacing between independent preference groups, `SectionSpacing`)
* **`Medium`:** `16dp` (screen and card horizontal padding, `ScreenHorizontalPadding`)
* **`Large`:** `24dp` (bottom screen padding, `ScreenBottomPadding`)

### Radius
* **`SegmentInner`:** `5dp` (inner joint corners between grouped rows)
* **`Small`:** `12dp`
* **`Medium`:** `16dp` (buttons, action chips)
* **`SegmentOuter`:** `22dp` (outer corners of first/last group rows and cards)
* **`SheetList`:** `24dp` (corner radius of inner sheet lists, `BottomSheetListCornerRadius`)
* **`Sheet`:** `28dp` (modal bottom sheets, `BottomSheetCornerRadius`)
* **`Max / Pill`:** `32dp` / `CircleShape` (FAB, toggle badges, circular indicators)

### Colors & Surfaces
* **`glassBackground`:** Static translucent matte fill for dark and light themes (no runtime blur shaders).
* **`glassBorder`:** 0.5dp subtle translucent outline (`SettingsDimensions.GlassBorderThickness`) for crisp hairline edge definition.
* **`primaryContainer` / `primary`:** Dynamic accent color for active toggle pills, badges, and switch thumbs.

---

## 5. Settings & Preferences (Pattern: Glass Segmented Rows)

### 5.1 Preference Group Anatomy

Each row within a group is an **independent glass card** (`Modifier.yumaGlassCard`), rather than an item inside a single shared container.


┌─────────────────────────────────────────────────────────┐  ◄── Top Corners: 22dp (SegmentOuter) | Top Alpha: 0.20f
│  [Badge]  Title & Description                 [Control] │
└─────────────────────────────────────────────────────────┘  ◄── Bottom Corners: 5dp (SegmentInner) | Bottom Alpha: 0.08f
▲
Gap: 1.5dp (SegmentGap, No Divider)
▼
┌─────────────────────────────────────────────────────────┐  ◄── All Corners: 5dp (SegmentInner) | Top Alpha: 0.08f
│  [Badge]  Title & Description                 [Control] │
└─────────────────────────────────────────────────────────┘  ◄── All Corners: 5dp (SegmentInner) | Bottom Alpha: 0.08f
▲
Gap: 1.5dp (SegmentGap, No Divider)
▼
┌─────────────────────────────────────────────────────────┐  ◄── Top Corners: 5dp (SegmentInner) | Top Alpha: 0.08f
│  [Badge]  Title & Description                 [Chevron] │
└─────────────────────────────────────────────────────────┘  ◄── Bottom Corners: 22dp (SegmentOuter) | Bottom Alpha: 0.04f


* **Corner Radii by Position (`PreferenceGroupPosition` / `YumaSegmentPosition`):**
  * **`Single`:** 22dp on all four corners.
  * **`First`:** Top corners 22dp, bottom corners 5dp.
  * **`Middle`:** All corners 5dp.
  * **`Last`:** Top corners 5dp, bottom corners 22dp.
* **Group Lighting (`YumaSegmentPosition` Gradient Alphas):**
  * To prevent blinding double-bright divider lines at inter-item joints, borders dynamically adapt their top/bottom gradient alphas based on position within the group:
    * **`Single`:** `0.20f to 0.04f` (full top-to-bottom light falloff).
    * **`First`:** `0.20f to 0.08f` (bright ceiling highlight, soft joint transition).
    * **`Middle`:** `0.08f to 0.08f` (uniform soft outline without harsh joint flashes).
    * **`Last`:** `0.08f to 0.04f` (soft joint top, subtle bottom fade).
* **Modifier Chaining Order:**
  * For tactile whole-card compression, `.yumaClickable(...)` must precede `.yumaGlassCard(...)` in the modifier chain:
    ```kotlin
    Modifier
        .fillMaxWidth()
        .yumaClickable(pressedScale = 0.96f, onClick = onClick)
        .yumaGlassCard(
            shape = shape,
            backgroundColor = backgroundColor,
            borderColor = borderColor,
            position = position,
        )
        .clip(shape)
        .padding(...)
    ```
* **Icon Badges:** `46dp` container (`SegmentedIconBoxSize`) with custom squircle/petal geometry and monochrome or accent fill.
* **Typography:**
  * Title: `titleMedium` Bold (W700), color `onSurface`.
  * Description: `bodyMedium`, color `onSurfaceVariant`, single-line truncated with ellipsis.
* **Navigation Items:** Display a trailing chevron `R.drawable.ic_arrow_right` with `RowChevronAlpha` opacity.

---

### 5.2 Provider Chip (Segmented Toggle)

Interactive music provider toggle (YouTube Music / Spotify):
* Implemented as a `yumaGlassCard` segmented card.
* Features a track with a sliding 36dp container (`primary`) that smoothly animates on provider change.
* Active label renders in `onPrimary`, inactive in `onSurfaceVariant`.

---

### 5.3 Dropdowns & Selection Dialogs

Dropdowns, context dialogs, and single-choice selectors follow **YDS 2.1 segmented glass patterns**:
* Each selection option is an independent segmented glass card with position-aware lighting (`YumaSegmentPosition`).
* Outer modal surface utilizes solid `surfaceContainerHigh` with 28dp corner radius and 0.5dp border (`SettingsDimensions.GlassBorderThickness`).
* Total elimination of `HorizontalDivider` between options; separation is maintained by `SegmentGap = 1.5dp`.
* Selected items feature a subtle `primary.copy(alpha = 0.16f)` container tint and a right-aligned checkmark icon.

---

## 6. Interaction Guidelines

| State | Visual Feedback |
| :--- | :--- |
| **Default** | Translucent `glassBackground` fill + 0.5dp hairline `glassBorder` with position-aware lighting. |
| **Pressed** | Whole-card scale down to `0.96f` (`SettingsAnimations.PressScale`) with `spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)` via `SettingsAnimations.pressSpring()`. When animations are disabled (`LocalAnimationsDisabled`), the press resolves via `snap()` with zero animation. |
| **Active / Selected** | Active indicator filled with `primary`, text colored `onPrimary` or tinted container. |
| **Disabled** | Row opacity set to `0.5f`, touch handling blocked. |

---

## 7. Component Checklist



Design Tokens (SettingsDimensions, SettingsAnimations, YdsInsets, LocalYumaColors)
↓
Primitive Modifiers
(Modifier.yumaGlassCard, Modifier.yumaClickable, yumaSegmentPosition / yumaSegmentAlphas)
↓
Composite Components
(PreferenceGroup, PreferenceEntry, SwitchPreference, EditTextPreference, SliderPreference, NumberPickerPreference,
YumaMorphingHeader, FloatingNavigationToolbar / FluidTabsContainer, GlassScaffold, ExpressivePullToRefreshBox,
LibraryFilterChipBar, shimmer placeholders)
↓
Shared Foundations
(YumaHaptics, YdsInsets safe-area helpers, ThemePreviews / TestThemeWrapper)
↓
Screens
(SettingsScreen, AccountSettings, AppearanceSettings, HomeScreen, player_0 sheets, etc.)


---

## 8. Do / Don't

### ✔ DO
* Use `Modifier.yumaGlassCard()` with `glassBackground` fill, 0.5dp `glassBorder`, and position-aware lighting for all preference rows and selection options.
* Precede `.yumaGlassCard(...)` with `.yumaClickable(...)` in modifier chains for tactile whole-card compression.
* Use the `SegmentGap` token (1.5dp) to separate grouped rows instead of `HorizontalDivider`.
* Apply segmented corner radii (22dp outer, 5dp inner) to unify items within a group.
* Use custom squircle/petal badge shapes for icons.

### ✘ DON'T
* Wrap an entire preference group into a single opaque container without individual segmented rows.
* Apply heavy runtime blur shaders (`BackdropBlur`) to list items or preference rows (blur is strictly reserved for floating overlays).
* Put `.yumaClickable(...)` after `.yumaGlassCard(...)` or inside inner layout containers, which causes disconnected content-only scaling.
* Introduce arbitrary corner radii or paddings outside the defined YDS tokens.
* Use generic circular solid chips from default Material 3.
