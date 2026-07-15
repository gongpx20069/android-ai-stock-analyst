# Data Source Implementation Specification

> This document owns the selected **direct Android provider clients**, exact
> endpoint quirks, freshness behavior, fallback rules, local cache metadata,
> and implementation checks. It does not compare the broader vendor market.
> For system boundaries, see [`architecture.md`](architecture.md). For chart
> placement and AI/ML use of these fields, see [`design.md`](design.md) and
> [`analysis.md`](analysis.md).

---

## 1. Selected local-only data architecture

| Responsibility | Selected source | Notes |
|---|---|---|
| Live quote snapshot | User choice: Auto, Tencent, or Sina | Auto uses Tencent first and Sina only on failure; explicit choices never switch silently |
| Intraday and higher-timeframe OHLCV | Not configured pending provider validation | Canonical Room storage is ready; no unverified endpoint is activated |
| Screening universe | Nasdaq public screener | Operating-company securities listed on NASDAQ, NYSE, and NYSE American |
| Fundamental and analyst snapshot | Independently selected valuation provider; Yahoo Finance currently available | Direct HTTP `quoteSummary` is unofficial and revocable; cached locally in Room |
| Derived indicators | Local Kotlin calculation engine | Deterministic indicators are calculated on-device |
| Prediction snapshots | ONNX Runtime Android | LightGBM inference runs on-device from bundled model assets |

The app uses a split design on purpose: fast quote access comes from domestic
quote endpoints, while slower-moving valuation data is normalized separately in
local storage. No project server exists between the app and these providers.

Provider choices are separate settings rather than one global source:

- Quotes: `Auto` (default), Tencent-only, or Sina-only.
- Charts: no provider is currently configured. The setting remains independent
  so a validated chart client can be added without changing quote behavior.
- Valuation: Yahoo Finance is the only implemented option; the setting is
  independent so valuation availability never controls quote or chart refresh.

All choices are persisted in Android DataStore. Explicit provider selection
must surface that provider's failure and use the last good Room cache; it must
not silently switch providers. Only `Auto` authorizes quote fallback.

---

## 2. Quote endpoints and contracts

### 2.1 Tencent quote endpoint and symbol quirks

- Endpoint: `https://qt.gtimg.cn/q=usNVDA`
- Symbol contract:
  - Prefix US symbols with `us`
  - Keep the ticker uppercase
  - Do **not** append exchange suffixes such as `.OQ`
- Response shape: raw `~`-delimited text that must be mapped by positional
  index
- Required mapped fields:
  - current price
  - daily change and change percent
  - open
  - previous close
  - volume
  - TTM P/E when present
  - market cap when present
  - 52-week high and low when present

Tencent may be selected directly. It is also the first source used by the
default `Auto` quote mode.

### 2.2 Sina fallback endpoint, header, and decoding

- Endpoint: `https://hq.sinajs.cn/list=gb_aapl`
- Symbol contract:
  - Prefix US symbols with `gb_`
  - Keep the ticker lowercase
- Required request header:
  - `Referer: https://finance.sina.com.cn`
- Response shape:
  - comma-delimited text
  - GBK-encoded; decode to UTF-8 before parsing

Sina may be selected directly. In `Auto` quote mode it is used only when
Tencent fails, times out, or returns an invalid payload.

<a id="23-tencent-chart-endpoints-and-5-minute-aggregation"></a>
### 2.3 Rejected Tencent chart endpoints and 5-minute requirement

- Same-day intraday line:
  `GET https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=usAAPL`
- Daily / monthly candles needed for the committed MVP timeframes:
  `GET https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=usAAPL,day|month,,,N,qfq`
- Minute candles for supported higher intraday intervals:
  `GET https://web.ifzq.gtimg.cn/appstock/app/kline/kline?param=usAAPL,m60|m30|m15,,,N`
- Four-hour candles are aggregated locally from completed 1-hour bars using
  exchange-time session boundaries.

Live verification found that `minute/query` exposes minute price plus
cumulative volume, not complete minute OHLC. The undocumented `m1` and `m5`
queries also returned date-only day aggregates during verification. They are
not activated as OHLC sources. Tencent's nominal `m15 / m30 / m60` queries
returned the same date-only session aggregate, while daily/monthly queries did
not return trustworthy recent US history. Tencent is therefore quote-only.
A validated chart provider contract is required before any bar ingestion or
local 5-minute aggregation can be implemented honestly.

For the MVP, the Android app must build **completed 5-minute bars** locally by
aggregating verified 1-minute data in exchange time. Re-check any undocumented
direct `m5` endpoint before release; if it proves stable, it may be used as a
replenishment or fallback source, not as an unverified assumption.

Implementation requirements:

- Persist bars by `(symbol, exchange, interval, barStart)`
- Deduplicate source overlap
- Never use an unfinished bar as a model label or inference input
- Normalize timestamps according to the canonical symbol and exchange-time model
  in [`architecture.md`](architecture.md)
- Keep exchange-time grouping and bar boundaries in storage; convert visible
  time labels to the device local timezone in the UI layer

---

<a id="24-us-exchange-screening-universe"></a>
### 2.4 US exchange screening universe

- Fetch each exchange separately:
  - NASDAQ:
    `GET https://api.nasdaq.com/api/screener/stocks?exchange=nasdaq&download=true`
  - NYSE:
    `GET https://api.nasdaq.com/api/screener/stocks?exchange=nyse&download=true`
  - NYSE American:
    `GET https://api.nasdaq.com/api/screener/stocks?exchange=amex&download=true`
- The Android client must send realistic `User-Agent` and `Accept` headers and
  treat the endpoint as revocable web infrastructure rather than a guaranteed
  public API.
- Include:
  - common stocks listed on NASDAQ, NYSE, or NYSE American
  - ADRs on those exchanges only when the required valuation and analyst fields
    are available
- Exclude:
  - ETFs and other funds
  - warrants and rights
  - units
  - preferred shares
  - rows without a usable symbol, supported primary exchange, or
    operating-company classification
- Normalize the exchange identifier and deduplicate by canonical symbol plus
  primary exchange before screening.
- Persist the normalized universe and its `fetchedAt` time in Room so screening
  can continue from the last good list when refresh fails.

---

## 3. Required fundamental fields from direct Yahoo Finance HTTP `quoteSummary`

Yahoo Finance is used directly from Android through an encapsulated
`quoteSummary` client, for example:

```text
GET https://query1.finance.yahoo.com/v10/finance/quoteSummary/AAPL?modules=price,financialData,summaryDetail,defaultKeyStatistics,recommendationTrend,assetProfile
```

This is an unofficial, revocable interface. Cookie, crumb, header, or session
handling can change without notice and must stay isolated inside the local
client. The app must request only the modules it needs and tolerate missing
subtrees without crashing unrelated features.

The app must normalize and cache at least these fields:

| Field group | Required fields |
|---|---|
| Valuation core | `targetLowPrice`, `targetMedianPrice`, `targetHighPrice`, `forwardPE`, `currentPrice` when needed for reconciliation |
| Analyst coverage | `numberOfAnalystOpinions`, `recommendationKey`, and any bucket counts needed to express rating strength |
| Positioning context | `fiftyDayAverage`, `twoHundredDayAverage`, `fiftyTwoWeekLow`, `fiftyTwoWeekHigh` |
| Screening support | `marketCap`, `averageDailyVolume3Month` or equivalent, sector and industry when available |

These fields are required because they feed:

- upside calculation
- analyst low/median/high target-range presentation outside the market chart
- screening weights
- anti-chasing checks
- AI interpretation input
- ML feature engineering

This document does not define the prompt schema or scoring rules; those belong
to [`analysis.md`](analysis.md) and [`ai-prompt.md`](ai-prompt.md).

When Yahoo fails, valuation-dependent features may become stale or unavailable,
but live quotes, charts, local technicals, and existing watchlists must
continue to work.

---

## 4. Freshness, fallback, local cache, and implementation checklist

### 4.1 Freshness and fallback behavior

| Data surface | Freshness expectation | Fallback / stale behavior |
|---|---|---|
| Live quote snapshot | Poll every 30-60 seconds or refresh manually | Auto falls back from Tencent to Sina; explicit Tencent/Sina modes do not switch. On failure, show stale status and keep the last good timestamp visible |
| Intraday bars for charting and ML | Use only completed bars | If a gap cannot be repaired, mark intraday predictions stale rather than guessing |
| Daily / monthly candles | Refresh after market close or when the vendor publishes the new bar | Keep the last good history and show the chart timestamp |
| Fundamental snapshot | Refresh on a daily cache cadence | Keep the last good cache in Room and show freshness warnings when analyst data is stale |
| Screening cache | Refresh in WorkManager batches under network and battery constraints | Resume from the last successful stage or page instead of restarting the universe every time |

Prediction timing and response schema are owned by
[`analysis.md` §4.3](analysis.md#43-live-inference-and-visualization-contract).

### 4.2 Local cache contract

Every normalized quote, bar, fundamental snapshot, and prediction snapshot
stored in Room should retain:

- `symbol`
- `source`
- `fetchedAt`
- `asOf`
- `exchangeTimestamp` or `barStart`
- `parseStatus`
- `staleAfter` when applicable

The repository layer should expose exchange-time-correct models to calculations
and screening, while the presentation layer is responsible for converting
timestamps to the device local timezone for display.

Completed 5-minute bars are derived locally from 1-minute data and persisted as
first-class records so screening, indicators, and the intraday model can all
reuse the same canonical bar set.

### 4.3 Implementation checklist

- [x] Map Tencent and Sina fields explicitly by positional index; do not rely on
      undocumented field names.
- [x] Persist quote, chart, and valuation provider choices independently in
      DataStore; allow fallback only in explicit Auto quote mode.
- [x] Encapsulate Yahoo cookie, crumb, session, and module handling inside the
      direct Android client; do not leak provider quirks into UI code.
- [x] Normalize quote timestamps using the canonical exchange-time model from
      [`architecture.md`](architecture.md).
- [ ] Select and validate a US OHLCV provider for every promised timeframe.
- [x] Record `source`, `fetchedAt`, parse status, and `staleAfter` for normalized
      quote and fundamental snapshots.
- [x] Distinguish quote freshness from fundamental freshness in local models and
      repository results.
- [x] Keep quote refresh and cached watchlist data available when Yahoo
      valuation refresh fails.
- [ ] Build completed 5-minute bars locally and never infer from unfinished
      bars.
- [x] Define canonical bar persistence with source, fetch time, parse status,
      interval, and exchange metadata; deduplicate by the Room primary key.
- [ ] Persist screening progress, page cursors, and stage markers so background
      refreshes can resume cleanly.
- [ ] Revalidate undocumented endpoints, headers, response shapes, and practical
      anti-bot limits before release.
- [ ] Treat undocumented vendor behavior as revocable; add monitoring and
      backoff instead of assuming stability.

Yahoo may return a regional unavailability page instead of a crumb from
networks where its services are blocked. The client rejects that response
explicitly, and the repository retains the last good valuation cache; the app
must not present this condition as a successful refresh.
