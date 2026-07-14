# Design System Master File - AI Stock Analyst

> **Logic:** check [`../../design.md`](../../design.md) first for page structure
> and interaction flow, then use this Master file for the concrete visual
> values. Page-level specifications may override only their own surface and must
> link back to this file.
>
> **Scope:** this file is the sole owner of exact visual tokens,
> accessibility requirements, and chart semantics.

---

**Project:** AI Stock Analyst, a US-stock valuation Android app built with
Kotlin and Jetpack Compose

**Category:** Fintech/Crypto

**Style:** Modern Dark (Cinema Mobile)

**Platform:** Mobile-first, primarily one-handed portrait use

---

## 1. Color palette for dark mode

Gold signals trust and CTA energy; purple signals technology and AI.

| Role | Hex | Compose token | Notes |
|------|-----|---------------|------|
| Page background | `#0F172A` | `AppColors.background` | Deep blue-black to avoid pure `#000000` OLED smearing |
| Card surface | `#222735` | `AppColors.surface` | Highest information-density zone; all text contrast is checked against it |
| Muted surface | `#272F42` | `AppColors.surfaceVariant` | Folded blocks and secondary backgrounds |
| Foreground text | `#F8FAFC` | `AppColors.onBackground` | Primary text, 14.24:1 contrast (AAA) |
| Muted foreground | `#94A3B8` | `AppColors.onSurfaceMuted` | Secondary price text and timestamps, 5.81:1 contrast (AA) |
| Border / outline | `#334155` | `AppColors.outline` | Dividers and input outlines |
| Primary CTA gold | `#F59E0B` | `AppColors.primary` | Main buttons and key actions; on-color is `#0F172A` |
| Secondary bright gold | `#FBBF24` | `AppColors.secondary` | Secondary emphasis; on-color is `#0F172A` |
| AI accent purple | `#8B5CF6` | `AppColors.aiAccent` | AI and multi-agent identity blocks; on-color is `#FFFFFF` |
| Destructive red | `#EF4444` | `AppColors.error` | Dangerous actions and delete states; on-color is `#FFFFFF` |
| Focus ring | `#F59E0B` | `AppColors.focusRing` | Input focus and keyboard focus ring |

<a id="2-semantic-layer-reversed-valuation-colors-the-projects-core"></a>
## 2. Semantic layer: reversed valuation colors, the project's core

> **Red does not mean down, and green does not mean up.** Color maps to
> **valuation**, not to price direction. Pair every semantic color with text and
> iconography so color never carries meaning alone.

| Semantic meaning | Text color on card | Block / border | Required icon + text | Trigger |
|------|----------------|---------|-------------------|------|
| **Has upside / cheap** | `#4ADE80` at 8.55:1 AAA | `#22C55E` | ▲ + `Upside XX%` | Median target price is materially above current price |
| **Expensive / chasing risk** | `#F87171` at 5.39:1 AA | `#EF4444` | ▼ + `At or above target` | Current price is at or above target |
| **Near target / cautious neutral** | `#FBBF24` at 8.92:1 AAA | `#FBBF24` | ● + `Near fair value` | Upside has narrowed |
| **AI / model signal** | `#A78BFA` at 5.47:1 AA | `#8B5CF6` | ✦ + `AI / probability` | Multi-agent output or ML signal |

## 3. Typography for dashboard data pairing

- **Numbers and data use Fira Code** so prices, P/E values, percentages, and
  target prices stay vertically aligned and do not visually jump.
- **Labels and body copy use Fira Sans** for interface text and longer
  interpretation blocks.

Package the font files as Android resources so the APK never depends on runtime
Google Fonts access:

```kotlin
val DataFontFamily = FontFamily(/* Fira Code font resources */)
val TextFontFamily = FontFamily(/* Fira Sans font resources */)
```

### Type scale

Use the modular scale `12 / 14 / 16 / 18 / 24 / 32` in `sp`. Avoid ad hoc
intermediate sizes. Body and form input text default to at least `16sp`. Helper
labels may use `12sp` or `14sp`, but they must remain readable and respect
system font scaling.

## 4. Spacing, radius, and elevation

| Token | Value | Use |
|-------|----|----|
| `spaceXs` | 4dp | Tight gaps |
| `spaceSm` | 8dp | Minimum spacing between touch targets, see §6 |
| `spaceMd` | 16dp | Standard inner padding |
| `spaceLg` | 24dp | Section padding |
| `spaceXl` | 32dp | Large gaps |
| `radius` | 12-16dp | Cards use 12dp, dialogs 16dp |
| `elevationMd` | 4dp | Cards |
| `elevationLg` | 10dp | Dialogs and dropdowns |

## 5. Chart color rules for Vico plus Compose overlays

> Chart direction is not the same as valuation, so chart styling **must stay
> decoupled from the valuation semantics in §2**.

| Chart | Preferred form | Color rule | Accessibility fallback |
|------|-------------|---------|-----------|
| **OHLC candle chart** | Candlestick | Neutral single hue plus hollow/filled difference using `#CBD5E1` outlines at strong contrast | OHLC data table plus daily percent move |
| **Current price vs target** | Bullet chart or gauge | Quality zones use cheap green -> fair yellow -> expensive red, with the value bar in gold `#F59E0B` and target marker in `#F8FAFC` | Always show the numbers and `distance to target XX%` text |
| **ML direction probability** | Gauge | Neutral track plus purple/gold arc instead of valuation red/green; keep the numeric probability visible | Number, percent label, and Compose `liveRegion` |
| **LightGBM probability line** | Line in a labeled sub-panel or on a clearly separated secondary scale | Use a purple line `#8B5CF6` with a gold latest-point marker `#F59E0B`; label horizon, `asOf`, and model version; do not draw fake future candles or future price paths | The layer can be turned off, and TalkBack reads the probability, time, and model version |
| **Screening ranking / comparison** | Horizontal descending bars | Color each row by the upside semantic from §2 and keep value labels visible | Visible value labels plus CSV export if needed |

## 6. Mobile UX rules

| Rule | Value | Severity |
|------|----|-------|
| **Touch target** | At least 44px; this project standardizes on **48dp** to satisfy both WCAG 44 and Material 48 | High |
| **Touch spacing** | At least 8px between adjacent tappable elements | Medium |
| **Gestures** | Vertical scrolling should win for main content; avoid horizontal swipe for main tabs and do not override system gestures | Medium |
| **Mobile keyboard** | Use Compose `KeyboardOptions`: `Number` for integer fields and `Decimal` for price thresholds | Medium |
| **Confirmation dialog** | Deleting watchlist entries or clearing an API key requires confirmation | High |
| **Color contrast** | Body text must stay at 4.5:1 or higher | High |
| **Not color only** | Red and green valuation states require icon plus text | High |
| **Reduced motion** | Respect system animation-scale and accessibility settings; prediction updates must not flash | High |
| **Loading state** | Use skeletons or a spinner; never freeze the screen with no feedback | High |
| **Empty state** | Empty watchlist should show guidance plus an `Add` button | Medium |
| **Real-time validation** | Validate API configuration fields on blur via `onFocusChanged`, not only on submit | Medium |
| **Disabled state** | Use roughly 50% opacity plus a clear non-interactive affordance | Medium |
| **Current location** | Highlight the current bottom-tab item with color plus filled icon | Medium |

## 7. Style and motion

- Top and bottom bars should prefer semi-transparent surface styling. On API
  31+, `RenderEffect` can be used carefully, but lower versions must have a
  no-blur fallback and text contrast must never be sacrificed.
- Standard transitions use `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`. Dialogs
  use Compose `spring`, and motion parameters should live in motion tokens
  rather than being scattered inside pages.
- Press feedback uses a scale from `0.97` to `1.0` plus Android
  `HapticFeedback`.
- Avoid pure `#000000` because of OLED smearing, and avoid overusing purple-pink
  AI gradients.

## 8. Anti-patterns

- ❌ Traditional stock colors where red means down and green means up
- ❌ Color carrying meaning without icon and text
- ❌ Emoji used as functional icons instead of Material Symbols or vector assets
- ❌ Body text below 4.5:1 contrast
- ❌ Horizontal swipe for switching main tabs or interactions that override the
  system back gesture
- ❌ Missing loading states, missing empty states, or missing delete confirmation
- ❌ Abusive purple-pink gradients or pure-black backgrounds

## 9. Pre-delivery checklist

- [ ] All text on card surfaces is at least 4.5:1 contrast
- [ ] Red and green valuation states include icon plus text label
- [ ] Touch targets are at least 48dp with spacing of at least 8dp
- [ ] Numeric values use monospaced Fira Code alignment
- [ ] Deleting items or clearing the key requires confirmation
- [ ] Loading uses skeletons, empty uses guided states, disabled uses reduced opacity
- [ ] Reduced-motion and animation-scale settings are respected
- [ ] The current bottom-tab page is clearly highlighted
- [ ] Chart red/green meaning never conflicts with valuation semantics
