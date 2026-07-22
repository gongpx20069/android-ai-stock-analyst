# Product Design: Pages, Journeys, and Charts

> This document owns page structure, interaction flows, chart placement, and
> behavioral UX requirements. Exact visual tokens, accessibility values, and
> chart styling live only in
> [MASTER](design-system/ai-stock-analyst/MASTER.md). Prompt schemas and the
> canonical AI disclaimer live only in [`ai-prompt.md`](ai-prompt.md).

---

## 1. Design principles first

1. **Valuation before price:** the first visible answer should be upside and
   room to support or resistance, not short-term price excitement.
2. **Counter impulse instead of amplifying it:** the UX should add friction at
   chasing and panic moments rather than reward them.
3. **Judgment at a glance:** each card and chart should answer one decision
   question clearly on a small screen.
4. **Explainable AI and ML:** every AI or ML surface must show why it exists and
   must use the canonical disclaimer contract from
   [`ai-prompt.md`](ai-prompt.md).
5. **Plain-language support:** technical terms should include short help text so
   the app remains usable without outside research.
6. **Visual rules defer to MASTER:** valuation color semantics, typography,
   spacing, touch targets, motion, and chart styling are owned by
   [MASTER](design-system/ai-stock-analyst/MASTER.md).

---

<a id="2-information-architecture-and-page-map"></a>
## 2. Information architecture and page map

The information architecture is four active bottom-tab destinations in the MVP.

```text
┌─────────────────────────────────────────────┐
│                                             │
│              Current page content           │
│                                             │
├──────────┬────────────┬──────┬──────────────┤
│ Watchlist │ Screening │ AI   │ Me           │
└──────────┴────────────┴──────┴──────────────┘
```

| Tab | Page | Purpose | Frequency |
|---|---|---|---|
| **Watchlist** | Watchlist home page | Check valuation health for tracked names | High |
| **Screening** | Screening destination | Screen NASDAQ, NYSE, and NYSE American with local refresh, progress, and results | Medium |
| **AI** | AI interpretation page | Run deeper 3+1 interpretation for one stock | Medium |
| **Me** | Settings page | Configure BYOK Azure settings, watchlists, and preference toggles | Low |

A selected stock expands research content inside the **Watchlist page**. The
current implementation does not create a separate stock-detail navigation
destination.

---

## 3. Feature to UX mapping table and completeness check

| # | Feature from docs | Hosting page | Hosting component or interaction |
|---|---|---|---|
| 1 | Market data | Watchlist | Stock-code controls, current price row, quote timestamp, and live chart |
| 2 | Valuation snapshot | Watchlist | Upside summary, forward P/E, rating, and analyst count |
| 3 | Support and resistance | Watchlist selection | Numeric cards plus level chart with resonance emphasis |
| 4 | Technicals | Watchlist selection | Folded block for MA, RSI, and 52-week position |
| 5 | Screening funnel | Screening | Quick templates, optional controls, background refresh state, and ranked results |
| 6 | 3+1 multi-agent AI | AI page | Streaming agent cards plus a final arbiter card |
| 7 | ML direction probability | Watchlist selection | Collapsed ML reference block and chart probability-line placement |
| 8 | BYOK Azure settings | Me | Four-field form, test action, and local-only storage messaging |
| 9 | Live market chart | Watchlist selection | First data card after stock-code controls, with gesture model and probability-line placement |
| 10 | Behavioral counterweights | Watchlist, detail, AI, and settings | Context-specific reminders and a discipline toggle |
| 11 | Watchlist management | Watchlist and Me | Add/remove actions, list state, and valuation-health summary |

---

## 4. Core page design and chart placement

### 4.1 Watchlist page home page and daily cockpit

**Top-to-bottom layout**

```text
Stock-code input and exchange controls
Refresh / progress / provider error
Expanded market chart
Quote
Valuation
Support and resistance
Technicals
```

- Keep the stock-code input and exchange controls visible after selection.
- Prefill the selected symbol and exchange where practical; preserve keyboard
  submission, Change/clear, refresh, scrolling, and restored Compose state.
- Before selection, show the input card and concise guidance that charts need
  historical OHLCV.
- After selection, render refresh progress or provider error immediately below
  the input card, then the expanded market chart as the first data card.
- Quote, valuation, support/resistance, and technical cards follow the chart.
- Do not duplicate the stock input or chart and do not navigate to a separate
  detail destination.

<a id="42-stock-detail-page-secondary-level-highest-information-density"></a>
### 4.2 Selected-stock research blocks on Watchlist

Use folded blocks so the most important facts appear first:

```text
1. Live market chart, expanded
2. Quote
3. Valuation snapshot, expanded by default
4. Support and resistance, expanded by default
5. Technicals, collapsed
6. AI interpretation entry
7. ML reference, collapsed
```

Block details:

1. **Live market chart**
   - fixed immediately below the Watchlist input/status area
   - shortcuts are `1m / 5m / 15m / 30m / 1h / 4h / 1D / 1M`
   - minute, hour, day, and month views remain exchange-time correct while
     displayed timestamps follow the device local timezone
   - candlesticks and volume are shown together
   - disclosure follows actual bars or the selected provider: Eastmoney
     Experimental or Alpaca Live IEX; it must never be hardcoded to Alpaca
2. **Quote**
   - current price, previous close, source, and timestamp
3. **Valuation snapshot**
   - current price
   - analyst low / median / high targets
   - upside percent
   - forward P/E
   - rating
   - analyst count
4. **Support and resistance**
   - distance to nearest support
   - distance to nearest resistance
   - level chart with resonance emphasis
5. **Technicals**
   - MA50 / MA200
   - RSI
   - 52-week position
6. **AI interpretation entry**
   - opens the AI page using the user's Azure setup
7. **ML reference**
   - probability, horizon, version, freshness, and clear subordinate labeling
   - contract lives in [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract)
Each block should start with a short plain-language summary. Charts on this
page include the current-price vs target view, support/resistance level chart,
optional rating distribution, live market chart, and ML probability view.

Below the market chart, render two non-chart surfaces:

- **Analyst range card:** low, median, and high analyst targets, analyst count,
  freshness, and current-price distance to the median.
- **AI forecast summary card:** separate `30m` and `5d` outlook summaries,
  evidence, confidence, watch conditions, and disclaimer.

Neither surface may draw analyst targets or AI-generated statements as future
price lines on the chart.

### 4.3 Screening page

```text
Quick templates
Optional advanced filters
Background refresh status and progress
Ranked results list
```

- Quick templates reduce setup friction.
- Default sorting is upside percent, not raw momentum.
- Each result supports one-tap add to watchlist.
- Screening is fully functional in the MVP.
- Show local background-refresh progress, cache freshness, resumable state, and
  last successful update time.
- Results should support pagination so users can inspect early pages while later
  pages continue to load.
- Screening logic lives in
  [`analysis.md` §2](analysis.md#2-stock-selection-pipeline-and-valuation-scoring).

<a id="44-ai-interpretation-page-multi-agent"></a>
### 4.4 AI interpretation page multi-agent

This page implements the 3+1 flow defined in
[`analysis.md` §3.1](analysis.md#31-streamlined-3-plus-1-flow). The goal is to
show the reasoning process rather than only the final answer.

```text
Analysing SYMBOL...
  Fundamental agent -> stance and confidence
  Technical agent   -> stance and confidence
  Risk agent        -> stance and confidence

Arbiter card
  Summary
  Confidence
  Valuation state
  30m outlook
  5d outlook
  Key evidence
  Risk flags
  Watch conditions
  Freshness warnings
  Canonical disclaimer
```

- Stream each agent as it completes.
- A cost-saving mode may switch to a simpler pass if open decision `Q4`
  remains enabled.
- The final arbiter card stays fixed at the bottom.
- `watchConditions` may include non-transactional research suggestions such as
  monitor, caution, compare, or wait for fresher data, but never order
  placement or trading controls.
- Field names and validation rules come from [`ai-prompt.md`](ai-prompt.md).
- The analyst target-range card and final AI summary remain outside the market
  chart, even when the AI page is opened from chart context.

<a id="45-me-settings-page"></a>
### 4.5 Me settings page

- App language: Follow system, English, or Simplified Chinese. Changing the
  choice applies the Android per-app locale immediately and persists
  independently from provider settings.
- Independent market-data provider selectors:
  - Quotes: Auto (Tencent then Sina), Tencent-only, or Sina-only
  - Charts: Eastmoney Experimental (default, no account), Not configured, or
    Alpaca Basic with encrypted user credentials
  - Valuation: Yahoo Finance, Finnhub BYOK, or Financial Modeling Prep (FMP)
    BYOK; Yahoo Finance remains the default
- The Alpaca option always identifies its feed as Live IEX and
  non-consolidated. It must not imply that OHLCV or volume represents the full
  US SIP market.
- The Eastmoney option identifies the source, no-account design, unofficial
  experimental interface, short observed intraday retention, unknown
  quota/stability, open-source-derived client contract, non-open data license,
  and absence of silent fallback or source mixing.
- Alpaca onboarding links to the official free-account signup and Dashboard
  API-key surfaces, explains how to generate and paste the one-time-visible
  secret, and states that the app uses the credentials only for market data
  and never places orders. Credentials remain encrypted locally and never pass
  through a project server.
- Finnhub and FMP onboarding appears only when that valuation provider is
  selected. Each flow links to the provider's official HTTPS signup and API
  documentation, accepts one API key, shows saved or missing state, and
  requires confirmation before clearing the encrypted local key.
- The Finnhub and FMP onboarding text states only that the app uses the key for
  valuation endpoints and encrypts it locally. It does not claim OAuth or
  scoped-key access. It also warns that free endpoint entitlement and
  permission to display user-fetched data in a public BYOK app can vary and
  must be confirmed with the provider.
- FMP onboarding explains that the verified stable endpoints do not provide a
  canonical forward P/E or target-aligned analyst count, so FMP valuation can
  be partial.
- Provider choices persist locally. Explicit quote-provider modes surface
  failure and stale-cache state instead of silently switching providers.
- A visible reset action restores provider defaults when stored settings cannot
  be read or migrated.
- Azure four-part form: Endpoint, Key, Deployment, and API Version, plus
  `Test Connection`
- Watchlist, screening-refresh, and cache-management actions
- Signed ONNX model import, validation result, active model version, and
  rollback to the previous valid model
- Preference toggles for discipline reminders and cost-saving mode
- About, freshness, and disclaimer entry points
- App updates: automatic launch check plus a manual action. When a newer
  GitHub Release exists, show the version and an APK download button.
- Feedback: explicitly welcome feature requests, bug reports, improvement
  ideas, and development collaboration; link to this repository's new-Issue
  page and display a mail action for `gongpx20069@vip.qq.com`.

---

## 5. Key interaction flows and user journeys

### Flow A Daily open

```text
Open app -> Watchlist -> check summary -> open detail only when something needs attention -> run AI only if needed
```

### Flow B Evaluating a potential new name

```text
Search or screen -> Detail page -> check upside first -> check 52-week position and nearby resistance -> optionally run AI
```

### Flow C Handling a drawdown in a watched name

See the behavioral counterweights in §8.

---

<a id="6-chart-design-and-visualization-checklist"></a>
## 6. Chart design and visualization checklist

Every chart must answer one judgment question. Page placement belongs here;
exact style belongs in MASTER.

<a id="61-kotlin-compose-chart-technology-choice"></a>
### 6.1 Kotlin Compose chart technology choice

| Component | Use | Trade-off |
|---|---|---|
| [**Vico Compose**](https://github.com/patrykandpatrick/vico) | Candlestick, line, and column Cartesian layers with axes and zoom basics | Native Kotlin/Compose option for core charting |
| **Compose overlay / Canvas** | Crosshair, labels, probability line panel or scale, resonance marks, gesture linkage | Adds business-specific interactions and overlays |
| [**TradingView Lightweight Charts**](https://github.com/tradingview/lightweight-charts) through WebView | JavaScript charting | Apache-2.0, but WebView integration is costly for native state and accessibility |
| [**TradingView Charting Library**](https://www.tradingview.com/HTML5-stock-forex-bitcoin-charting-library/) | Proprietary charting package | Do not integrate or distribute |

Choice: Vico provides the base layer and Compose provides the product-specific
overlay and interaction model.

**Current implementation slice**

- Stock lookup expands the same Watchlist page while keeping the input controls
  visible. It observes quote, valuation, technical, support/resistance, and
  timeframe-specific source-isolated Room flows.
- The initial Vico chart supports all eight timeframe shortcuts, neutral
  hollow/filled candlesticks, device-local time labels, a dollar price axis,
  and an independently scaled selected-source volume end axis.
- Drag/pinch interaction comes from Vico, and the visible reset action restores
  the initial zoom and jumps to the latest bar.
- Crosshair-synchronized OHLCV, per-symbol timeframe persistence, landscape
  mode, and the LightGBM probability overlay remain later chart slices.

**TradingView-inspired acceptance checklist**

- Pinch to zoom, drag to pan, and double-tap to reset
- Long-press crosshair with synchronized OHLC, volume, and time readout
- Timeframe shortcuts `1m / 5m / 15m / 30m / 1h / 4h / 1D / 1M`, remembered per
  symbol
- Main chart and any sub-chart share the X-axis and crosshair
- Candlesticks and the volume histogram stay visible together
- The LightGBM probability line uses its own labeled probability scale or
  dedicated sub-panel and never impersonates future price candles
- Landscape full-screen mode with predictable back behavior
- Loading more history keeps the viewport anchor stable
- `Jump to latest` action without resetting manual zoom on live updates
- TalkBack reads the selected bar and prediction label

<a id="62-core-visualization-checklist-by-priority"></a>
### 6.2 Core visualization checklist by priority

**P0 - valuation visuals**

1. **Upside bar**
   - compares current price with median target price
   - answers whether room to target still exists
2. **Current price vs target positioning chart**
   - places current price, annual range, and target on one axis
   - answers whether upside remains while the name is already extended
3. **Support and resistance level chart**
   - shows nearest support below and resistance above
   - highlights resonance levels from
     [`analysis.md` §1.2](analysis.md#12-support-and-resistance-levels-four-required-methods)
4. **Forward P/E comparison bars**
   - compare relative cheapness across names

**P1 - market and technical visuals**

5. **Live market chart**
   - hosts the intraday or higher-timeframe market view
   - includes candlesticks, volume histogram, and the interaction model from
     §6.1
   - may host the `30m` probability line defined in
     [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract)
   - must not invent future candles or deterministic price paths
6. **LightGBM probability line**
   - shows direction probability over time, not a future price forecast
   - uses its own labeled probability scale or dedicated panel
7. **Rating distribution, optional**
   - summarizes analyst stance concentration

**P2 - secondary technical visuals**

8. **RSI or 52-week position view**
   - provides a secondary overheating or oversold check

ML-specific charts and the prediction contract remain owned by
[`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract).

### 6.3 Data to chart field mapping

| Chart | Required fields | Source |
|---|---|---|
| Upside bar | `currentPrice`, `targetMedianPrice` | Quote snapshot plus normalized fundamental snapshot |
| Positioning chart | `currentPrice`, 52-week high/low, 200-day average, target | Quote snapshot plus normalized fundamental snapshot |
| Analyst range card | `targetLowPrice`, `targetMedianPrice`, `targetHighPrice`, `numberOfAnalystOpinions`, freshness | Normalized fundamental snapshot |
| AI forecast summary card | `horizonOutlooks`, confidence, evidence, watch conditions | Validated arbiter output from [`ai-prompt.md`](ai-prompt.md) |
| Forward P/E comparison | `forwardPE` | Fundamental snapshot |
| Live market chart | Intraday and higher-timeframe OHLCV, volume, and optional `30m` probability line | User-selected Eastmoney Experimental bars under [`data-sources.md` §2.3](data-sources.md#23-eastmoney-experimental-us-chart-contract) or Alpaca Basic Live IEX bars under [`data-sources.md` §2.4](data-sources.md#24-alpaca-basic-live-iex-chart-contract), plus local prediction snapshots from [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| LightGBM probability line | `probabilityUp`, `asOf`, `horizon`, `modelVersion` | Local prediction snapshots from [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |

---

<a id="7-state-design-empty-loading-and-error"></a>
## 7. State design empty loading and error

| State | Design |
|---|---|
| First open / empty | Guide the user to add a first watchlist stock and configure Azure only if AI is wanted |
| Key not configured | The affected Azure or Alpaca feature stays disabled with a direct path to settings; no-account Eastmoney and unrelated cached facts remain independent |
| Loading | Show skeletons for valuation data and streaming progress for AI |
| Data temporarily unavailable | Keep the last good snapshot visible, show timestamps, and explain which layer failed |
| Screening refresh | Show WorkManager progress, last completed stage, resumable status, and cache freshness |
| Live chart refresh | Follow quote freshness and fallback rules in [`data-sources.md` §4](data-sources.md#4-freshness-fallback-local-cache-and-implementation-checklist) and update prediction overlays only when the snapshot in [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) changes |
| Azure error | Distinguish invalid key, exhausted quota, and network problems with recovery guidance |

---

<a id="8-behavioral-counterweights"></a>
## 8. Behavioral counterweights

The product should apply a gentle brake during high-risk moments instead of
acting like a momentum toy.

| High-risk moment | Counter-interaction |
|---|---|
| Seeing a stock fall and wanting to buy immediately | Restate upside and forward P/E before any bullish framing |
| Wanting to chase near the 52-week high | Show a warning when the stock is near annual highs and close to resistance |
| Feeling anxious over a small drawdown | Display the real percentage clearly and frame it against normal volatility instead of amplifying stress |
| Wanting to act impulsively on a headline or price spike | Restate the main risk conditions and ask what factual trigger actually changed |
| Repeatedly refreshing quotes with no purpose | Keep healthy states calm and low-noise rather than attention-seeking |

These reminders should stay optional, but enabled by default unless the related
open decision changes in [`architecture.md`](architecture.md).
