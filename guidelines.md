# Material 3 Expressive — Design Guidelines

> A practical, consolidated reference for designing with **Material 3 Expressive (M3 Expressive / M3E)**, the evolution of Material Design 3 announced by Google at I/O 2025. It covers color, shape, typography, motion, components, layout, accessibility, and the seven expressive design tactics.
>
> **M3 Expressive is not a new version of the system.** It is an evolution of M3 — M3 is not deprecated, and this is not "M4." You can adopt it incrementally and it works with the existing M3 token architecture.
>
> Source references: [m3.material.io](https://m3.material.io), [Material 3 Expressive blog post](https://m3.material.io/blog/building-with-m3-expressive), [Google Design research](https://design.google/library/expressive-material-design-google-research), [Motion physics system](https://m3.material.io/styles/motion/overview/how-it-works), [Android Developers — Wear M3E design language](https://developer.android.com/design/ui/wear/guides/get-started/design-language).

---

## Table of Contents

1. [What is Material 3 Expressive?](#1-what-is-material-3-expressive)
2. [The Seven Expressive Design Tactics](#2-the-seven-expressive-design-tactics)
3. [Color](#3-color)
4. [Shape](#4-shape)
5. [Typography](#5-typography)
6. [Motion & Animation](#6-motion--animation)
7. [Components](#7-components)
8. [Layout, Spacing & Elevation](#8-layout-spacing--elevation)
9. [Accessibility](#9-accessibility)
10. [Tools & Resources](#10-tools--resources)
11. [Sources](#11-sources)

---

## 1. What is Material 3 Expressive?

**Material 3 Expressive** is Google's 2025 evolution of Material Design 3. It is a set of new features, updated components, and design tactics for creating **emotionally impactful UX** — experiences that feel alive, personal, and distinct rather than generic.

### Why it exists

- M3 Expressive is backed by **more user research than any previous Material update**: 46 studies, hundreds of designs, and **18,000+ participants** worldwide.
- In eye-tracking tests, participants spotted key UI elements **up to 4× faster** in M3 Expressive designs, and time-to-tap on key actions dropped by seconds.
- People generally **preferred** M3 Expressive designs across all age groups.

### The fundamentals of expressive design

The building blocks are: **color, shape, size, motion, and containment**. These are used to draw attention to what matters, make key actions stand out, and group like elements together.

### Design principles (from Google's research)

- **Be intentional and start from user need** — a strong minority of users prefer calmer, less intense versions. Don't crank up expressiveness for its own sake.
- **Prioritize functionality** — no amount of emotion compensates for a lack of clarity. Don't break established UI patterns (e.g., don't replace a scrolling playlist with scattered album art; don't remove text labels from actions).
- **Follow accessibility standards** — contrast, screen-reader support, navigation, and reduced-motion must always win.
- **Iterate** — find the balance between freshness and familiarity, playfulness and professionalism.

### Platform availability

| Platform | Status |
|---|---|
| Jetpack Compose (Android) | **Available** — components, motion springs, emphasized type, new shape system |
| Android Views (MDC-Android) | Available (springs in Views; not yet applied to all components) |
| Flutter | Partial — check your Material version for parity |
| Web | **Not implemented** (Material Web is in maintenance mode) — use CSS conversions of spring tokens or custom JS |
| XR (AR/VR) | New XR app bars, dialogs, spatial panels, orbiters, spatial elevation |

---

## 2. The Seven Expressive Design Tactics

Google defines **seven tactics** to apply expressive design. They can be combined, and are especially powerful when used together for "hero moments."

| # | Tactic | What it means | Example |
|---|---|---|---|
| 1 | **Use a variety of shapes** | Mix rounded, pill, and asymmetric/morphing containers to create rhythm and distinct controls | A "flower" shape that expands for a breathing exercise |
| 2 | **Apply rich and nuanced colors** | Use the full role system — primary/secondary/tertiary plus containers and fixed roles — for hierarchy, not just decoration | A vivid primary button on a soft surface |
| 3 | **Guide attention with typography** | Emphasized styles, large numerals, and variable-font axes (weight, width) steer the eye | Big numbers for key metrics, emphasized display styles |
| 4 | **Contain content for emphasis** | Put elements in containers to define regions and signal interactivity | Each email/setting in its own rounded container |
| 5 | **Add fluid and natural motion** | Use the spring-based motion physics system; motion is a first-class expressive tool | Buttons and containers morph on press, cards expand with a bounce |
| 6 | **Leverage component flexibility** | Adapt components to context — screen size, foldables, input mode | Docked vs. floating toolbars, adaptive button groups |
| 7 | **Combine tactics to create hero moments** | Stack several tactics for a stand-alone, editorial focal point | A full-screen breathing visualization with shape + color + motion + type |

### Applying tactics well (usability best practices)

- **Use clear scale and placement** — avoid crowding the screen with many equally large elements. Create one focal point.
- **Reinforce with consistent color roles** — use *different* roles for actions vs. data so users can instantly tell what's interactive.
- **Create calm, balanced layouts** — uniform shapes/sizes, even spacing, consistent rhythm. Avoid overlapping or chaotic containers.
- **Motion should communicate** — motion has meaning (guidance, state change, hierarchy), not just decoration.

---

## 3. Color

### 3.1 How the M3 color system works

M3 generates colors in **HCT color space** (Hue, Chroma, Tone), which is perceptually uniform — unlike HSL/HSV. A single **seed color** produces a complete **tonal palette** of 13 tones (0–100), organized into **five key palettes**:

- **Primary** — your main brand color
- **Secondary** — supporting, complements primary
- **Tertiary** — contrasting accent for balance and expression
- **Neutral** — surfaces and backgrounds
- **Neutral Variant** — lower-emphasis surfaces and outlines

### 3.2 Color roles (semantic tokens)

Color roles are **tokenized** (e.g., `primary`, `onPrimary`, `primaryContainer`) and paired to guarantee accessible contrast. A dark surface is algorithmically paired with a light "on" color.

| Group | Roles | Typical use |
|---|---|---|
| **Primary** | `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer` | Brand actions — FABs, buttons, active states |
| **Secondary** | `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer` | Less prominent — filter chips, tonal buttons, secondary actions |
| **Tertiary** | `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer` | Contrasting accents — input fields, highlights, balance |
| **Error** | `error`, `onError`, `errorContainer`, `onErrorContainer` | Error states; static by default in dynamic schemes |
| **Surface** | `surface`, `onSurface`, `onSurfaceVariant`, `outline`, `outlineVariant` | Backgrounds + text/icons |
| **Surface containers** | `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` | Hierarchy and nested containers (cards, bars, sheets) |
| **Inverse** | `inverseSurface`, `inverseOnSurface`, `inversePrimary` | Inverse surfaces (snackbars on dark, etc.) |
| **Fixed (Expressive)** | `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` (+ same for `secondary`, `tertiary`) | Fixed/accent tones for expressive moments |

**Design guidance:**
- Use `surface` for the main background and `surfaceContainer` for navigation areas; the five container levels create hierarchy without changing elevation.
- Default container-to-role mappings: elevated button/card → `surfaceContainerLow`, top/bottom bar → `surfaceContainer`, FAB & basic dialog → `surfaceContainerHigh`, menus/panels → `surfaceContainerHighest`.
- Pair contrast targets: text vs. background ≥ **4.5:1**, large text ≥ **3:1**, UI components & graphics ≥ **3:1**.

### 3.3 Dynamic color

- **Dynamic color** derives a theme from the user's wallpaper or content via the **Material Theme Builder**, making personal devices feel personal.
- When enabled, keep brand-identifying moments using fixed roles or brand token overrides, while letting the rest of the UI adapt.
- Dynamic color is **a tool, not a shortcut** — it can dilute brand identity or create contrast issues if applied without intent. Define how the product should feel first.
- On devices without dynamic color (older Android, web), fall back to a **baseline scheme** generated from the same seed via `ColorScheme.fromSeed()`.

### 3.4 Expressive color updates

- The color system expands to adopt **deeper tonal palettes and a wider token set**, allowing more color to be applied across themes and in context.
- **Containerized color**: expressive designs use containers (tinted, rounded regions) to give interactive elements stronger, clearer signifiers — e.g., Gmail's compose button and per-email containment.
- Uses of color in M3E:
  - **Tint surfaces** to add warmth/personality.
  - **Emphasize the primary action** with high-contrast primary fills.
  - **Use secondary/tertiary** to distinguish data from actions and create visual hierarchy.

---

## 4. Shape

Shapes play a much stronger role in M3 Expressive — they are a **branding and rhythm tool**. The update added **35 new abstract shapes** to the Material Shapes Library (Figma + Jetpack Compose) and introduced **shape morphing**.

### 4.1 Corner-radius scale (shape tokens)

| Token | Value | Default components |
|---|---|---|
| `shapeCornerNone` | 0dp | (custom) |
| `shapeCornerExtraSmall` | 4dp | Snackbar, text field sides |
| `shapeCornerSmall` | 8dp | Chips, text fields (top), menus |
| `shapeCornerMedium` | 12dp | Cards |
| `shapeCornerLarge` | 16dp | FAB, extended FAB, nav drawer (end) |
| `shapeCornerExtraLarge` | 28dp | Dialogs, bottom sheets (top) |
| `shapeCornerExtraLargeIncreased` | 32dp | (custom / emphasized) |
| `shapeCornerExtraExtraLarge` | 48dp | (custom / new large containers) |
| `shapeCornerFull` | 9999px (`full`) | Buttons, icon buttons, badges, search bar, sliders, switches |

> **Note:** Fully rounded corners are now defined by a `full` token rather than "50% of component size." This makes pill shapes consistent regardless of component dimensions.

### 4.2 Component → shape mapping

| Component | Default shape |
|---|---|
| Buttons (all types) | `full` (pill) |
| Icon buttons | `full` |
| FAB / Extended FAB | `large` |
| Chips | `small` |
| Cards | `medium` |
| Dialogs | `extraLarge` |
| Text fields | `small` (top corners) |
| Menus | `small` |
| Navigation drawer | `large` (end corners) |
| Bottom sheets | `extraLarge` (top corners) |
| Snackbar | `extraSmall` |
| Badges | `full` |
| Slider handle / Switch track / Tabs indicator | `full` |
| Search bar | `full` |

### 4.3 Expressive shape behaviors

- **Shape morphing** — containers can morph their corner radius (and size) between states: buttons compress on press, selected chips change shape, loading indicators morph to show progress. Use **spatial springs** for these transitions.
- **Asymmetric rounding** — single-corner rounding (e.g., edge-hugging containers on circular devices) supports new expressive patterns.
- **Custom/abstract shapes** — use the 35-shape library for hero moments, loading animations, and playful data containers, but keep them functional and consistent.
- **Grouped containers** — related components share a container and adapt their distribution (symmetrical or hierarchy-led) to available space.

**Guidance:**
- Use shape consistently within a product to build recognizability.
- Use more dramatic rounding for primary/hero elements; reserve subtle rounding for background containers.
- Never let a custom shape obscure interactivity or readability.

---

## 5. Typography

### 5.1 Type roles

The M3 type scale organizes styles into **five roles × three sizes**:

| Role | Purpose |
|---|---|
| **Display** | Large, short, hero text; significant metrics; brand moments |
| **Headline** | High-emphasis, short text; section headers |
| **Title** | Page/section/card titles; way-finding |
| **Body** | Paragraphs, metadata, data, timestamps |
| **Label** | Component-level text (buttons, chips, tabs) |

### 5.2 The 30-style type scale (Expressive update)

M3 Expressive expands the type scale to **30 type styles: 15 baseline + 15 emphasized**.

- Baseline styles: `display-large … label-small`.
- **Emphasized styles** (e.g., `display-large-emphasized`, `body-medium-emphasized`) add weight/expression for **highlighted moments** — primary CTAs, hero numerals, selected states.
- Baseline and emphasized styles are **meant to be used together**: baseline for default content, emphasized for priority.
- Emphasized styles are available in **Jetpack Compose** and **MDC-Android**; not yet in Flutter or Material Web.

Baseline defaults (approximate reference values):

| Style | Size | Line height | Weight | Tracking |
|---|---|---|---|---|
| Display large | 57 | 64 | 400 | −0.25 |
| Display medium | 45 | 52 | 400 | 0 |
| Display small | 36 | 44 | 400 | 0 |
| Headline large | 32 | 40 | 400 | 0 |
| Headline medium | 28 | 36 | 400 | 0 |
| Headline small | 24 | 32 | 400 | 0 |
| Title large | 22 | 28 | 400 | 0 |
| Title medium | 16 | 24 | 500 | 0.15 |
| Title small | 14 | 20 | 500 | 0.1 |
| Body large | 16 | 24 | 400 | 0.5 |
| Body medium | 14 | 20 | 400 | 0.25 |
| Body small | 12 | 16 | 400 | 0.4 |
| Label large | 14 | 20 | 500 | 0.1 |
| Label medium | 12 | 16 | 500 | 0.5 |
| Label small | 11 | 16 | 500 | 0.5 |

### 5.3 Variable fonts

- **Google Sans Flex** — Google's brand typeface; six variable axes (weight, width, optical size, slant, + more) and is open source (SIL OFL).
- **Roboto Flex / Roboto Serif / Roboto Mono** — variable alternatives usable for expressive treatment.
- Variable axes can be **animated as motion**: dynamic font **weight** and **width** respond to interaction/state (e.g., a button's label thickens on press). Use `font-variation-settings: 'wght' …, 'wdth' …` on the web.
- A single variable font file replaces multiple static weights — faster load, more expressive range.

### 5.4 Applying type

- Map components to roles consistently (buttons → `labelLarge`, cards → `titleMedium`, body → `bodyMedium`, app bar title → `titleLarge`, dialog headline → `headlineSmall`, etc.).
- Limit body line length to ~**50–75 characters** for readability.
- Use optical sizing where available; large display sizes benefit from adjusted weight/width.
- Type and **shape should work in harmony** — expressive type pairs with expressive containers.

---

## 6. Motion & Animation

### 6.1 The motion physics system (the big change)

M3 Expressive replaces the old **easing + duration** system with a **physics-based spring system** (announced May 2025). Motion is now a first-class expressive tool.

- Springs have **no fixed duration** — they respond dynamically to input, velocity, interruptions, and retargeting. They handle gestures and mid-animation interruptions seamlessly.
- A spring is defined by **stiffness** (hardness; higher = resolves faster), **damping** (how fast bounce wears out; 1 = no bounce), and **initial velocity**.

### 6.2 Two motion schemes

| Scheme | Feel | Best for |
|---|---|---|
| **Expressive** | Bouncy, underdamped, overshoots | Hero moments, key interactions, important transitions, playful products |
| **Standard** | Utilitarian, controlled, minimal overshoot | Utility apps, data-heavy interfaces, form-filling, most defaults |

The scheme is applied **at the product level** and applies to all tokens — you can swap schemes without changing assigned tokens.

### 6.3 Spring tokens

Two token types × three speeds = six spring composites per scheme.

- **Spatial springs** — for movement: x/y position, rotation, size, **corner radius (shape morphing)**. These overshoot and bounce into place.
- **Effects springs** — for color and opacity; **no overshoot**.

Token naming: `md.sys.motion.spring.fast.spatial`, `md.sys.motion.spring.default.spatial`, `md.sys.motion.spring.slow.spatial` (+ `.effects` variants).

| Speed | Spatial use | Effects use |
|---|---|---|
| **Fast** | Small, snappy feedback (press, tap) | Quick color/opacity fades |
| **Default** | Most standard transitions | Standard color/opacity |
| **Slow** | Full-screen animations | Full-screen content refresh |

Springs are **device-adaptive** — the exact values differ between wearables, phones, and tablets, so motion always feels right in context.

### 6.4 Web conversion (springs → curves)

On the web, springs must be approximated with `cubic-bezier` curves (official table from m3.material.io):

| Spring token | Cubic-bezier | Duration |
|---|---|---|
| Expressive fast spatial | `0.42, 1.67, 0.21, 0.90` | 350ms |
| Expressive default spatial | `0.38, 1.21, 0.22, 1.00` | 500ms |
| Expressive slow spatial | `0.39, 1.29, 0.35, 0.98` | 650ms |
| Expressive fast effects | `0.31, 0.94, 0.34, 1.00` | 150ms |
| Expressive default effects | `0.34, 0.80, 0.34, 1.00` | 200ms |
| Expressive slow effects | `0.34, 0.88, 0.34, 1.00` | 300ms |
| Standard fast spatial | `0.27, 1.06, 0.18, 1.00` | 350ms |
| Standard default spatial | `0.27, 1.06, 0.18, 1.00` | 500ms |
| Standard slow spatial | `0.27, 1.06, 0.18, 1.00` | 750ms |
| Standard fast effects | `0.31, 0.94, 0.34, 1.00` | 150ms |
| Standard default effects | `0.34, 0.80, 0.34, 1.00` | 200ms |
| Standard slow effects | `0.34, 0.88, 0.34, 1.00` | 300ms |

Example web usage:
```css
.animate-expressive-default-spatial {
  transition: all 0.5s cubic-bezier(0.38, 1.21, 0.22, 1);
}
```

### 6.5 Motion guidance

- **21 Material components** use motion physics by default in Jetpack Compose.
- **Reduce motion**: respect `prefers-reduced-motion` and system animation settings — simplify or disable non-essential spring overshoot/bounce.
- **Motion has meaning**: use spatial motion for layout/state changes, effects motion for color/opacity. Don't apply bounce to color.
- **Variable-font motion**: animate type axes (weight/width) to signal feedback — subtle, performant, expressive.
- Keep interactions **interruptible** — springs excel here; don't lock animations.
- Use **expressive** schemes sparingly (hero moments); use **standard** for utilitarian flows.

---

## 7. Components

### 7.1 New components (Expressive)

| Component | Description |
|---|---|
| **Button group** | A container that groups buttons of mixed shapes/sizes; adapts width, shape, and motion to context; works with XS–XL button sizes |
| **FAB menu** | A menu panel that opens from a FAB; **replaces the M2 speed dial** and stacked small FABs; large items + contrasting colors; works with any FAB size/style |
| **Split button** | A button with a separate menu trigger that **spins and changes shape** when activated; elevated/filled/tonal/outlined styles; same sizes as label & icon buttons |
| **Loading indicator** | Shows progress for loads **under 5 seconds**; used in pull-to-refresh; **replaces most indeterminate circular progress indicators**; often uses shape morphing |
| **Toolbar (docked)** | Full-width bar; for **global actions consistent across pages**; **replaces the deprecated bottom app bar** |
| **Toolbar (floating)** | Floats above content; for **contextual actions** tied to a page/selection; can pair with a FAB; can be vertical on large screens; two color modes: **standard** (low emphasis) and **vibrant** (high emphasis / temporary mode like editing) |
| **Wide navigation rail** | Updated rail for larger screens with wider layout |
| **Toggle FAB** | FAB that toggles between states (e.g., play/pause, expand/collapse) |
| **Flexible bottom app bar** | The bottom app bar reworked to contain the FAB and be more flexible |

**Rules of thumb:**
- **Don't pair a toolbar and a bottom navigation bar** — show the nav bar on primary pages, toolbars on subsequent pages.
- Floating toolbars shouldn't exceed the window/pane edge; use an overflow menu for extra actions.
- Don't keep a FAB visible when a floating toolbar is open (avoid clutter) unless the pattern calls for it.

### 7.2 Updated components

- Top app bars (expressive app bars with more configuration, emphasized titles)
- Carousel (updated)
- Common buttons (larger expressive options, shape morphing on press)
- FAB / Extended FAB (updated sizes and shapes)
- Icon buttons (full-pill shape, larger touch targets)
- Navigation bar & navigation rail (updated)
- Progress indicators (loading indicator replaces indeterminate spinners)

### 7.3 Deprecated / replaced

- **Bottom app bar** → **Docked toolbar**
- **Speed dial / stacked small FABs** → **FAB menu**
- **Indeterminate circular progress** (most uses) → **Loading indicator**

### 7.4 XR components

- XR app bars and XR dialogs for AR/VR; spatial panels, orbiters, and spatial elevation bring M3 into 3D space.

---

## 8. Layout, Spacing & Elevation

### 8.1 Layout & breakpoints

Design adaptively across window sizes. Material defines window size classes:

| Class | Width | Behavior |
|---|---|---|
| **Compact** | 0–599dp | Single pane (phones) |
| **Medium** | 600–839dp | Two panes (tablets, foldables) |
| **Expanded** | 840–1199dp | Multi-pane (desktops) |
| **Large / Extra-large** | 1200dp+ | Full multi-column layouts |

- Use canonical layouts (list-detail, feed, supporting pane) and let containers group/distribute based on available space.
- Toolbars, rails, and FABs adapt their position/orientation by breakpoint.

### 8.2 Spacing

- M3 spacing is built on a **4dp grid**: 4, 8, 12, 16, 24, 32, 48, 64… Use the token scale (`space[0|1|2|3|4|5|6|7|8|9|10|11|12]`) instead of arbitrary values.
- Standard content padding: 16dp on mobile; 24dp+ on larger screens.
- Group related elements with consistent rhythm; use **grouping and spacing** as an expressive tactic (see §2).

### 8.3 Elevation

Elevation uses **tonal surface colors** (surface containers) plus shadow levels:

| Level | Components at rest |
|---|---|
| **0** | App bar (flat), filled/tonal/outlined buttons, button groups, filled/outlined cards, carousel, chips, icon buttons, lists, nav rail, segmented buttons, sliders, tabs |
| **1** | Banners, modal bottom sheet, elevated button, elevated card, elevated chips, modal nav drawer, modal side sheet |
| **2** | App bar (scrolled), menus, nav bar, rich tooltips, toolbar |
| **3** | Date pickers, modal dialogs, extended FAB, FAB, FAB menu close, search, time pickers |
| **4** | (hover/focus states, dragging) |
| **5** | (highest emphasis, e.g., pressed/dragged dialogs) |

- Hover/focus typically raises most interactive components by **+1 level** (e.g., FAB 3 → 4).
- Toolbars are deliberately **flat** (no shadow) in M3E to keep focus on the body content.

---

## 9. Accessibility

Accessibility is **built into the system**, not an add-on. M3E often *exceeds* baseline standards.

- **Contrast**: text ≥ **4.5:1** (large text ≥ 3:1); UI components/graphics ≥ **3:1**. Color roles are auto-paired to help achieve this.
- **Touch targets**: ≥ **48×48dp** (some expressive controls go larger). Never shrink below ~44dp.
- **Text scaling**: honor user font-size settings; type tokens scale (note: on Wear, styles ≥20sp don't scale due to screen space).
- **Reduced motion**: respect `prefers-reduced-motion` / system animation scale; replace springs with simple fades or remove bounce.
- **Screen readers**: components must expose proper semantics; don't rely on shape/color alone to convey meaning.
- **Intentional color usage**: don't use the same color role for both actions and data — it breaks hierarchy for everyone, especially low-vision users.
- **Never break patterns**: expressive design fails when familiar interaction paradigms are replaced by novel, unclear ones.

---

## 10. Tools & Resources

| Tool | Purpose |
|---|---|
| **Material Theme Builder** (web + Figma plugin) | Generate dynamic/baseline color themes from a seed color or image; export tokens |
| **Material 3 Design Kit (Figma)** | Official component library incl. new M3E components (button groups, FAB menus, split buttons, loading indicators), XR panels/dialogs, 35-shape library |
| **Material Shapes Library (Figma/Compose)** | 35 expressive abstract shapes + corner-radius tokens |
| **Material Motion plugin (Figma)** | Apply spring motion tokens to prototypes |
| **Google Fonts** | Google Sans Flex, Roboto Flex, Roboto Serif, Roboto Mono |
| **Material Symbols** | Expressive icon set that pairs with variable type |
| **Jetpack Compose Material3** | Full M3E implementation (springs, emphasized type, new shapes) |

**Export targets from Theme Builder:** Figma styles, Design System Package (DSP), Android code (Compose or Views).

**Adoption strategy (incremental):**
1. Start with **motion** — adopt the spring-based MotionScheme.
2. Review **shape** usage — apply corner-radius scale + morphing.
3. Review **color** application within components — containers, fixed roles, dynamic color.
4. Adopt **emphasized type** and variable fonts for hero moments.
5. Migrate deprecated components (bottom app bar → toolbar, speed dial → FAB menu, indeterminate spinner → loading indicator).

---

## 11. Sources

- Material Design 3 — official guidelines: https://m3.material.io
- Start building with Material 3 Expressive (blog): https://m3.material.io/blog/building-with-m3-expressive
- Motion physics system — How it works: https://m3.material.io/styles/motion/overview/how-it-works
- Motion physics system — Specs (web conversions): https://m3.material.io/styles/motion/overview/specs
- Color roles: https://m3.material.io/styles/color/roles
- Typography (30-style scale, variable fonts): https://m3.material.io/styles/typography
- Expressive Design: Google's UX Research: https://design.google/library/expressive-material-design-google-research
- Usability — Applying M3 Expressive (case study): https://m3.material.io/foundations/usability/applying-m-3-expressive
- M3E design language (Android Developers / Wear): https://developer.android.com/design/ui/wear/guides/get-started/design-language
- Material 3 Design Kit (Figma): https://www.figma.com/community/file/1035203688168086460
- Google I/O 2025 — Build next-level UX with Material 3 Expressive: https://io.google/2025/explore/technical-session-24/
- Google Sans Flex (Google Fonts): https://fonts.google.com/specimen/Google%20Sans%20Flex
