# Architecture and Decision Ledger

> This document is the project's **engineering decision hub**: it defines
> system boundaries, records locked and pending decisions, and indexes the
> specialized documents. For the product overview, see
> [`../README.md`](../README.md) or [`../README.zh-CN.md`](../README.zh-CN.md).
> For agent working constraints, see [`../CLAUDE.md`](../CLAUDE.md).

---

## 1. Goals and boundaries

Native Android app: build watchlists and screen major US exchanges -> fetch
quotes and fundamentals directly on-device -> calculate deterministic
indicators locally -> display TradingView-inspired market charts and LightGBM
probability signals -> optionally call the user's own Azure OpenAI setup
directly from Android to generate structured interpretation.

Boundaries:

- Output data, probabilities, explanations, and non-transactional research
  suggestions, but never order placement or personalized trading instructions.
- The product is **watchlist-only**. It does not model holdings, portfolio
  lots, cost basis, staged buys, partial sells, trade execution, or brokerage
  workflows.
- No project server exists. Runtime networking, caching, screening,
  calculations, and model inference all stay inside the Android app.
- Market calculations use the exchange calendar and exchange time, including
  DST, holidays, and completed-bar boundaries. Every timestamp displayed to the
  user is converted to the Android device's local timezone.
- The Azure Key is stored on-device and sent only to the user-configured Azure
  endpoint.
- "TradingView-inspired" refers only to interaction quality and information
  organization, not to copying its brand, proprietary code, or assets.

## 2. System overview

```text
┌──────────────────── Native Android App ────────────────────┐
│ Kotlin + Jetpack Compose + Material 3                      │
│ Watchlist / Screening / AI / Me                            │
│ Direct provider clients: Tencent / Sina / Yahoo HTTP       │
│ Room cache + repositories                                  │
│ WorkManager refresh and screening pipeline                 │
│ Local indicator engine + screening engine                  │
│ ONNX Runtime Android for LightGBM inference                │
│ On-device 3+1 Azure orchestration                          │
│ Vico + Compose overlays: candlesticks, volume, probability │
│ line, crosshair, pan/zoom, landscape                       │
└───────────────┬──────────────────┬──────────────────┬───────┘
                │ HTTPS             │ HTTPS            │ HTTPS
                ▼                   ▼                  ▼
        Tencent / Sina        Yahoo Finance      Azure OpenAI
        quotes + OHLCV        quoteSummary       user-configured endpoint
```

### 2.1 Android runtime boundary

- Kotlin, Jetpack Compose, Material 3, and single-activity.
- MVVM plus unidirectional data flow; Coroutines and Flow manage streaming
  state.
- Hilt for dependency injection; Retrofit and OkHttp for networking; Room for
  structured cache; DataStore for settings; Android Keystore for Azure Key
  encryption.
- Direct Android clients fetch:
  - Tencent primary and Sina fallback live quotes and OHLCV
  - Yahoo Finance `quoteSummary` modules for valuation and analyst fields
- Room stores normalized quote, bar, fundamental, screening, and prediction
  snapshots with source and freshness metadata.
- WorkManager handles batched screening refreshes, model/data maintenance, and
  resumable background work under network and battery constraints.
- Deterministic indicators and support/resistance calculations run locally in
  Kotlin.
- ONNX Runtime Android runs bundled LightGBM models on-device. The `30m` model
  infers after each completed local 5-minute bar. The `5d` model infers after
  market close.
- The app calls three analysis agents in parallel on-device, then calls the
  arbiter agent. The Azure Key is included only in requests sent directly to
  the user-configured Azure endpoint.
- Vico Compose provides the candlestick, line, and column base layers; Compose
  overlays and custom drawing add the crosshair, labeled probability line,
  indicator layers, and TradingView-inspired interactions.

### 2.2 External dependency boundary

- Tencent is the primary source for live quotes and chart bars.
- Sina is the fallback source for live quotes when Tencent fails.
- Yahoo Finance `quoteSummary` is the direct source for target median price,
  forward P/E, analyst coverage and rating, moving averages, and 52-week
  fields. It is unofficial and may change without notice.
- Azure OpenAI is called directly from Android only when the user has
  configured BYOK credentials.
- No project server mediates these dependencies.

<a id="3-decision-ledger-as-the-single-decision-source"></a>
## 3. Decision ledger as the single decision source

### 3.1 Locked decisions

| ID | Decision | Conclusion | See |
|---|---|---|---|
| A1 | Android stack | **Native Kotlin plus Jetpack Compose**, replacing the earlier cross-platform direction | §2.1 |
| A2 | Android architecture | Material 3 + MVVM/UDF + Coroutines/Flow + Hilt + Retrofit/OkHttp + Room/DataStore + Keystore | §2.1 |
| A3 | Build baseline | Gradle Wrapper 9.4.1, Android Gradle Plugin 9.2.0, JDK 17, compile/target SDK 36, and minimum SDK 26 | Repository build files |
| B | Data approach | **Direct Android provider clients**: Tencent primary and Sina fallback for quotes/OHLCV; direct Yahoo Finance HTTP `quoteSummary` for valuation and analyst fields; Room caches all normalized snapshots | [`data-sources.md`](data-sources.md) |
| B2 | Canonical symbol and time model | Use provider-specific symbol mapping at the edges; keep exchange-time-correct timestamps internally for DST, holidays, and bar boundaries; convert every displayed timestamp to the device local timezone | [`data-sources.md`](data-sources.md) |
| B3 | Project-server boundary | No project server exists for the MVP or current architecture; runtime networking and processing stay local to Android | §2 |
| Q1 | AI architecture | Streamlined **3 plus 1**: fundamentals, technicals, risk, arbiter | [`analysis.md` §3.1](analysis.md#31-streamlined-3-plus-1-flow) |
| Q2 | AI integration | BYOK Azure OpenAI four-part configuration; prompts and outputs follow a shared contract; requests go directly from Android to Azure | [`ai-prompt.md`](ai-prompt.md) |
| S1 | Screening scope | The MVP includes both watchlist monitoring and full screening across NASDAQ, NYSE, and NYSE American | [`analysis.md` §2.2](analysis.md#22-two-stock-selection-entry-points-as-product-behavior) |
| S5 | Screening universe | NASDAQ, NYSE, and NYSE American operating-company securities. Include common stocks and ADRs only when required valuation fields are complete; exclude ETFs, funds, warrants, rights, units, and preferred shares | [`data-sources.md` §2.4](data-sources.md#24-us-exchange-screening-universe) |
| M1 | ML in the MVP | **Yes, LightGBM**, but only as an auxiliary probability signal | [`analysis.md` §4](analysis.md#4-ml-support-signals-lightgbm-direction-models) |
| M2 | Intraday prediction | Infer the probability of direction over the **next 30 minutes** after every completed local 5-minute bar; the model is trained offline and shipped for on-device inference | [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| M3 | Medium-horizon prediction | Infer the probability of direction over the **next 5 trading days** after each market close | [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| M4 | ML display policy | Show valid `30m` and `5d` predictions in real time without an accuracy threshold; always expose model version, freshness, walk-forward metrics, calibration metrics, and the disclaimer | [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| M5 | LightGBM runtime format | Train and export during development or release tooling, convert to ONNX `TreeEnsemble`, bundle a versioned ONNX model in the APK, and infer with ONNX Runtime Android; no runtime training | [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| M6 | Signed local model import | Allow signed ONNX model packages to be imported locally after installation; verify signature, checksum, feature schema, horizons, and compatibility before atomic activation, with rollback to the last valid model | [`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract) |
| U1 | Navigation structure | Four active bottom tabs: Watchlist / Screening / AI / Me | [`design.md` §2](design.md#2-information-architecture-and-page-map) |
| U2 | Valuation color semantics | Red = expensive or chasing highs, green = valuation upside; always pair color with icon and text | [MASTER §2](design-system/ai-stock-analyst/MASTER.md#2-semantic-layer-reversed-valuation-colors-the-projects-core) |
| U6 | Screening MVP behavior | Screening is a full, visible MVP tab with working NASDAQ, NYSE, and NYSE American refresh, progress, and results; it is not hidden or deferred | [`design.md` §4.3](design.md#43-screening-page) |
| V1 | Market chart | TradingView-inspired reference for interaction density and information hierarchy; never copy proprietary code, assets, or branding | [`design.md` §6](design.md#6-chart-design-and-visualization-checklist) |
| V2 | Chart implementation | Vico Compose base layers plus Compose overlay and custom drawing; do not integrate the proprietary TradingView Charting Library | [`design.md` §6.1](design.md#61-kotlin-compose-chart-technology-choice) |
| V3 | Exact chart timeframes and layers | Support minute/hour/day/month families with `1m / 5m / 15m / 30m / 1h / 4h / 1D / 1M`; show candlesticks, volume histogram, and a labeled LightGBM probability line; do not promise weekly in the MVP | [`design.md` §6](design.md#6-chart-design-and-visualization-checklist) |
| I1 | Support and resistance | Four methods: pivot points, swing highs and lows, Fibonacci, and moving-average or 52-week positioning | [`analysis.md` §1.2](analysis.md#12-support-and-resistance-levels-four-required-methods) |
| I2 | Support/resistance presentation | "Distance to nearest support/resistance" sits beside upside and forward P/E | [`design.md` §4.2](design.md#42-stock-detail-page-secondary-level-highest-information-density) |
| I3 | Resonance | Any level hit by at least two methods is bolded and tagged with reliability | [`analysis.md` §1.2](analysis.md#12-support-and-resistance-levels-four-required-methods) |
| I4 | Technical MVP scope | MA50/200, RSI(14), and 52-week positioning; MACD, Bollinger Bands, and ATR later | [`analysis.md` §1.3](analysis.md#13-technicals-as-a-supporting-role) |
| P1 | Product account scope | Watchlist-only. Explicitly exclude holdings, lots, cost basis, staged buys, partial sells, trade execution, and brokerage functionality | [`design.md` §4.1](design.md#41-watchlist-page-home-page-and-daily-cockpit) |
| Q5 | Forecast and analyst presentation | AI provides evidence-based outlook summaries for the existing `30m` and `5d` horizons; analyst low/median/high target prices and the AI summary are displayed in cards outside the market chart, never drawn as chart price lines | [`ai-prompt.md`](ai-prompt.md) |

### 3.2 Open decisions

| ID | Decision point | Current recommendation | See |
|---|---|---|---|
| C | Quant factor expansion | Do not add money flow, sentiment, or sector comparison in the MVP | [`analysis.md` §1](analysis.md#1-valuation-metrics-support-and-resistance-and-technicals) |
| D | AI vendor compatibility | MVP supports Azure OpenAI only; standard OpenAI and DeepSeek come later | This document |
| E | Open-source license | Choose Apache-2.0 or MIT before publishing code | This document |
| S2 | Screening weights | Upside 40 / P/E 30 / confidence 10 / rating 10 / positioning 10 | [`analysis.md` §2.1](analysis.md#21-stock-selection-pipeline-four-stage-funnel) |
| S3 | Hard filters | Market cap >= $10B, price >= $5, average daily volume >= 500K | [`analysis.md` §2.1](analysis.md#21-stock-selection-pipeline-four-stage-funnel) |
| S4 | Anti-chasing rule | When the current price is above 95% of the 52-week high, down-rank it and warn | [`design.md` §8](design.md#8-behavioral-counterweights) |
| Q3 | Scheduled watchlist analysis | Integrate in phase two | This document |
| Q4 | Cost-saving mode | Keep a single-agent toggle | [`design.md` §4.4](design.md#44-ai-interpretation-page-multi-agent) |
| U3 | Behavioral reminders | On by default, user can disable | [`design.md` §8](design.md#8-behavioral-counterweights) |
| U4 | Detail-page folding | Valuation, support/resistance, and market chart expanded by default | [`design.md` §4.2](design.md#42-stock-detail-page-secondary-level-highest-information-density) |
| U5 | AI presentation | Stream each agent, then show the final arbiter card | [`design.md` §4.4](design.md#44-ai-interpretation-page-multi-agent) |

## 4. Key data and timing

| Data | Source | Freshness | App expression |
|---|---|---|---|
| Current price and live quote snapshot | Tencent primary, Sina fallback | 30-60 second polling | Show quote timestamp |
| 1-minute bars and local completed 5-minute bars | Tencent chart endpoints plus local Room aggregation | Every completed bar | Drive charts, indicators, screening signals, and `30m` inference |
| 15-minute, 30-minute, 1-hour, 4-hour, daily, and monthly candles | Tencent chart endpoints, local aggregation, and Room cache | Refresh after vendor publication | Multi-timeframe chart history |
| Analyst low/median/high targets, forward P/E, rating, analyst count, averages, and 52-week fields | Direct Yahoo Finance HTTP `quoteSummary`, cached locally in Room | Daily cache cadence | Show analyst range and AI summary outside the chart; degrade only valuation-dependent features on failure |
| Screening refresh state | WorkManager plus Room | Background batched refreshes | Show progress, resumable status, and cache freshness |
| 30-minute direction probability | ONNX Runtime Android intraday model | After each completed local 5-minute bar | Show horizon, probability, model version, and freshness |
| 5-day direction probability | ONNX Runtime Android daily model | After each market close | Display separately from intraday probability |
| AI report | Azure OpenAI direct from Android | User-triggered | Show input snapshot time |

All calculations use exchange time internally. The UI converts user-visible
timestamps to the Android device's local timezone at render time.

The local prediction snapshot must retain `asOf`, `horizon`, `probabilityUp`,
`confidence`, `modelVersion`, `featureWindow`, `dataFreshness`, and backtest
metrics. For the full contract, see
[`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract).

## 5. Repository and document index

| Document | Content | Status |
|---|---|---|
| [`../README.md`](../README.md) | English product overview and documentation navigation | Maintained |
| [`../README.zh-CN.md`](../README.zh-CN.md) | Chinese product overview and documentation navigation | Maintained |
| [`../CLAUDE.md`](../CLAUDE.md) | Agent harness guide, repository routing, and workflow rules | Maintained |
| [`data-sources.md`](data-sources.md) | Selected providers, endpoint contracts, freshness, and fallback | Maintained |
| [`analysis.md`](analysis.md) | Formulas, screening, 3+1 AI interpretation, and LightGBM contract | Maintained |
| [`design.md`](design.md) | Pages, interactions, chart placement, and behavioral UX rules | Maintained |
| [`design-system/ai-stock-analyst/MASTER.md`](design-system/ai-stock-analyst/MASTER.md) | Exact visual tokens, accessibility, and chart semantics | Maintained |
| [`ai-prompt.md`](ai-prompt.md) | 3+1 agent prompts and structured output contract | Maintained |
| [`../app/`](../app/) | Android application, four-tab Compose shell, and Hilt graph | Implemented foundation |
| [`../core/data/`](../core/data/) | Local-first repository and stale-cache refresh behavior | Implemented quote/valuation slice |
| [`../core/database/`](../core/database/) | Room cache, DAOs, schema, and model mappings | Implemented quote/valuation slice |
| [`../core/model/`](../core/model/) | Canonical market-domain models | Implemented foundation |
| [`../core/domain/`](../core/domain/) | Deterministic valuation and time calculations | Implemented foundation |
| [`../core/designsystem/`](../core/designsystem/) | Compose design tokens and theme | Implemented foundation |
| [`../core/network/`](../core/network/) | Direct quote and valuation provider clients | Implemented quote/valuation slice |

## 6. Delivery order

Steps 1 and 2 are implemented for quote and valuation snapshots. Historical
bar ingestion and completed 5-minute aggregation begin with step 3.

1. Create the Kotlin/Compose Android skeleton with the locked local-only
   module boundaries.
2. Bring up Tencent and Sina quote clients, the Yahoo Finance HTTP client,
   Room caches, and the canonical exchange-time model.
3. Implement deterministic indicators, support/resistance, and the
   TradingView-inspired detail-page chart with
   `1m / 5m / 15m / 30m / 1h / 4h / 1D / 1M`
   shortcuts, candlesticks, volume, and probability-line placement.
4. Implement the full three-exchange screening pipeline with WorkManager
   batching, resumable progress, pagination, and freshness reporting.
5. Package versioned ONNX LightGBM models, run local inference for both
   horizons, and implement signed model import with validation and rollback.
6. Integrate the Azure OpenAI 3+1 flow and the structured arbiter card.
7. Finish security, accessibility, offline/error states, APK signing, and
   release work.
