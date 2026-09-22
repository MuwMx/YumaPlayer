# Yuma Design System (YDS)

**Version:** 2.1

**Status:** Stable Foundation

---

## 1. Philosophy

Yuma — дизайн-система, ориентированная на музыку как главный контент. Она задает четкий набор ограничений и правил, создавая легковесный, глубокий и эстетически цельный интерфейс.

Интерфейс отказывается от стандартных монолитных M3-шаблонов со сплошными плоскими списками. Визуальная айдентика Yuma строится на сегментированной стеклянной геометрии, акцентных матовых подложках под иконки, открытом пространстве и физичном микро-отклике на касания.

---

## 2. Visual Language & Character

* **Calmness:** интерфейс не перегружает зрение и не отвлекает от прослушивания музыки.
* **Depth & Geometry:** глубина создается через тонкие полупрозрачные поверхности (`glassBackground`) и контурную обводку в 0.5dp (`glassBorder`).
* **Soft Geometry:** составные скругления (22dp на внешних углах группы, 5dp на внутренних стыках) визуально объединяют разрозненные элементы в монолитный блок.
* **Low Visual Noise:** полное отсутствие полос-разделителей (`Dividers`) в списках настроек. Разделение элементов строится исключительно на межстрочных зазорах (`SegmentGap = 1.5dp`) и внешних отступах. В M3-диалогах и шторках выбора разделители допустимы.
* **Physicality:** каждый интерактивный элемент сжимается (scale down до 0.96) с мягкой пружинной анимацией при нажатии.

---

## 3. Design Principles

1. **Hierarchy Through Scale:**
* **Preference Rows:** фиксированная минимальная высота **72dp**.
* **Primary Actions:** высота **56dp+**.
* **Standard Interactive Controls:** минимальная область нажатия **48×48dp**.
* **Icon Badges:** размер контейнера **32dp / 46dp**.


2. **Segmented Glass Structure:** списки настроек и карточки интеграций строятся как независимые сегментированные стеклянные кнопки, объединенные в смысловые блоки через зазор `SegmentGap`.
3. **Surface Hierarchy (Строгое разделение поверхностей):**
* **Solid Surfaces:** непрозрачные тональные контейнеры (`surface`, `surfaceContainer`) для основного контента, где прозрачность не требуется.
* **Static Translucent Glass:** полупрозрачный фон (`glassBackground`) + обводка 0.5dp (`glassBorder`, `SettingsDimensions.GlassBorderThickness`) **строго без backdrop blur**. Применяется для сегментированных списков настроек и карточек. Модальные шторки и диалоги выбора используют непрозрачные solid-поверхности (`surfaceContainerHigh`).
* **Backdrop Blur Glass:** размытие фона + тональная подложка. Применяется **исключительно** для плавающих оверлеев (Bottom Player Bar, плавающий FAB, модальные шторки).


4. **Color Driven:** динамическая генерация палитры M3 Expressive на основе обложки текущего трека для акцентов, тумб свитчеров и активных индикаторов.
5. **No Divider Lines:** линии-разделители не используются. Границы групп определяются геометрией карточек и обводкой.

---

## 4. Foundations (Design Tokens)

Все размеры, отступы и радиусы в коде должны браться строго из токенов (`SettingsDimensions` / `SettingsAnimations` / `YdsInsets` в `:designsystem`, плюс layout-константы в `constants/Dimensions.kt`). Использование произвольных хардкодных значений запрещено.

### Spacing

* **`SegmentGap`:** `1.5dp` (межстрочный зазор внутри группы сегментированных карточек)
* **`Section`:** `12dp` (отступ между независимыми группами настроек, `SectionSpacing`)
* **`Medium`:** `16dp` (внутренние отступы экрана и карточек, `ScreenHorizontalPadding`)
* **`Large`:** `24dp` (нижний отступ экрана, `ScreenBottomPadding`)

### Radius

* **`SegmentInner`:** `5dp` (внутренние стыковочные углы строк внутри группы)
* **`Small`:** `12dp`
* **`Medium`:** `16dp` (кнопки, чипы действий)
* **`SegmentOuter`:** `22dp` (внешние углы первой/последней строки группы и карточек)
* **`SheetList`:** `24dp` (скругление списков внутри шторок, `BottomSheetListCornerRadius`)
* **`Sheet`:** `28dp` (модальные шторки, `BottomSheetCornerRadius`)
* **`Max / Pill`:** `32dp` / `CircleShape` (FAB, бейджи переключателей, круглые индикаторы)

### Colors & Surfaces

* **`glassBackground`:** статичная полупрозрачная матовая заливка для темной и светлой темы (без использования шейдеров размытия).
* **`glassBorder`:** контурная обводка 0.5dp (`SettingsDimensions.GlassBorderThickness`) с легкой прозрачностью для четкой фиксации границ элементов.
* **`primaryContainer` / `primary`:** динамический акцентный цвет для активных пилюль, бейджей и тумб свитчеров.

---

## 5. Settings & Preferences (Pattern: Glass Segmented Rows)

### 5.1 Анатомия группы настроек

Каждая строка в группе является **самостоятельной стеклянной карточкой** (`Modifier.yumaGlassCard`), а не элементом внутри одного общего контейнера.

```
┌─────────────────────────────────────────────────────────┐  ◄── Top Corners: 22dp (SegmentOuter)
│  [Badge]  Title & Description                 [Control] │
└─────────────────────────────────────────────────────────┘  ◄── Bottom Corners: 5dp (SegmentInner)
                             ▲
                Gap: 1.5dp (SegmentGap, No Divider)
                             ▼
┌─────────────────────────────────────────────────────────┐  ◄── All Corners: 5dp (SegmentInner)
│  [Badge]  Title & Description                 [Control] │
└─────────────────────────────────────────────────────────┘  ◄── All Corners: 5dp (SegmentInner)
                             ▲
                Gap: 1.5dp (SegmentGap, No Divider)
                             ▼
┌─────────────────────────────────────────────────────────┐  ◄── Top Corners: 5dp (SegmentInner)
│  [Badge]  Title & Description                 [Chevron] │
└─────────────────────────────────────────────────────────┘  ◄── Bottom Corners: 22dp (SegmentOuter)

```

* **Скругления по позиции (`PreferenceGroupPosition` / `YumaSegmentPosition`):**
* **`Single`:** 22dp со всех четырех сторон.
* **`First`:** верхние углы 22dp, нижние углы 5dp.
* **`Middle`:** все углы 5dp.
* **`Last`:** верхние углы 5dp, нижние углы 22dp.

* **Подсветка группы (`YumaSegmentPosition`, градиентные альфы):**
* Чтобы на стыках не возникали слепящие двойные линии, альфы верхней/нижней границы адаптируются под позицию в группе:
  * **`Single`:** `0.20f → 0.04f`.
  * **`First`:** `0.20f → 0.08f`.
  * **`Middle`:** `0.08f → 0.08f`.
  * **`Last`:** `0.08f → 0.04f`.
* **Порядок модификаторов:**
  * Для тактильного сжатия всей карточки `.yumaClickable(...)` ставится строго перед `.yumaGlassCard(...)`:
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


* **Иконные бейджи:** контейнер `46dp` (`SegmentedIconBoxSize`) с фигурной формой (сквиркл/лепесток) и монохромной либо акцентной заливкой.
* **Типографика:**
* Заголовок: `titleMedium` Bold (W700), цвет `onSurface`.
* Описание: `bodyMedium`, цвет `onSurfaceVariant`, обрезка в 1 строку с ellipsis.


* **Навигационные элементы:** содержат шеврон `R.drawable.ic_arrow_right` с прозрачностью `RowChevronAlpha` у правого края.

---

### 5.2 Provider Chip (Segmented Toggle)

Интерактивный переключатель источников (YouTube Music / Spotify):

* Выполнен в виде стеклянной сегментированной карточки `yumaGlassCard`.
* Содержит направляющую дорожку с плавающим 36dp контейнером (`primary`), плавно анимирующим позицию при смене провайдера.
* Активный лейбл окрашивается в `onPrimary`, неактивный — в `onSurfaceVariant`.

---

### 5.3 Выпадающие списки и диалоги выбора (Dropdowns & Dialogs)

Выпадающие списки, контекстные диалоги и селекторы единичного выбора строятся по сегментированным стеклянным паттернам **YDS 2.1**:
* Каждая опция выбора — независимая сегментированная стеклянная карточка с позиционной подсветкой (`YumaSegmentPosition`).
* Внешняя поверхность модалки — solid `surfaceContainerHigh` со скруглением 28dp и обводкой 0.5dp (`SettingsDimensions.GlassBorderThickness`).
* Никаких `HorizontalDivider` между опциями; разделение — зазор `SegmentGap = 1.5dp`.
* Выбранные пункты подсвечиваются тинтом `primary.copy(alpha = 0.16f)` и галочкой справа.

---

## 6. Interaction Guidelines

| Состояние | Визуальный отклик |
| --- | --- |
| **Default** | Заливка `glassBackground` + обводка 0.5dp `glassBorder` с позиционной подсветкой. |
| **Pressed** | Сжатие строки до `0.96f` (`SettingsAnimations.PressScale`) со спецификацией `spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)`; при отключенных анимациях — мгновенный `snap()`. |
| **Active / Selected** | Заливка активного индикатора в цвет `primary`, текст `onPrimary`. |
| **Disabled** | Прозрачность всей строки `0.5f`, блокировка обработки кликов. |

---

## 7. Component Checklist

```
Design Tokens (SettingsDimensions, SettingsAnimations, YdsInsets, LocalYumaColors)
                       ↓
Primitive Modifiers
(Modifier.yumaGlassCard, Modifier.yumaClickable, yumaSegmentPosition / yumaSegmentAlphas)
                       ↓
Composite Components
(PreferenceGroup, PreferenceEntry, SwitchPreference, EditTextPreference, SliderPreference, NumberPickerPreference,
YumaMorphingHeader, FloatingNavigationToolbar / FluidTabsContainer, GlassScaffold, ExpressivePullToRefreshBox)
                       ↓
Screens
(SettingsScreen, AccountSettings, AppearanceSettings, HomeScreen, player_0 sheets, etc.)

```

---

## 8. Do / Don't

### ✔ DO

* Использовать `Modifier.yumaGlassCard()` с фоном `glassBackground` и 0.5dp-бордером `glassBorder` для всех строк настроек.
* Ставить `.yumaClickable(...)` строго перед `.yumaGlassCard(...)` для тактильного сжатия всей карточки.
* Использовать токен `SegmentGap` (1.5dp) для разделения строк вместо `HorizontalDivider`.
* Применять сегментированные углы (22dp снаружи, 5dp внутри) для объединения элементов группы.
* Использовать кастомные фигурные подложки под иконки (сквирклы/лепестки).
* Диалоги и шторки выбора строить как сегментированные стеклянные списки на solid-поверхности `surfaceContainerHigh` (28dp, без разделителей).

### ✘ DON'T

* Заворачивать группу настроек в один сплошной непрозрачный контейнер без разделения на сегментированные строки.
* Использовать тяжелые шейдеры размытия (`BackdropBlur`) внутри элементов списков и настроек (размытие разрешено только для плавающих оверлеев).
* Вводить произвольные радиусы скругления и отступы вне зафиксированных токенов YDS.
* Использовать стандартные круглые цветные плашки из дефолтного Material 3.
