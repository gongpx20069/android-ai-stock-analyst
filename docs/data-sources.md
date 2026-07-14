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
| Live quote snapshot | Tencent primary | China-friendly quote access without keys |
| Live quote fallback | Sina fallback | Used only when Tencent fails or returns unusable data |
| Intraday and higher-timeframe OHLCV | Tencent chart endpoints | Powers live charts, local bar aggregation, and feature generation |
| Screening universe | Nasdaq public screener | Operating-company securities listed on NASDAQ, NYSE, and NYSE American |
| Fundamental and analyst snapshot | Direct Yahoo Finance HTTP `quoteSummary` client in Android | Unofficial and revocable; cached locally in Room |
| Derived indicators | Local Kotlin calculation engine | Deterministic indicators are calculated on-device |
| Prediction snapshots | ONNX Runtime Android | LightGBM inference runs on-device from bundled model assets |

The app uses a split design on purpose: fast quote access comes from domestic
quote endpoints, while slower-moving valuation data is normalized separately in
local storage. No project server exists between the app and these providers.

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

Use Tencent as the default real-time quote source.

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

Use Sina only as a fallback when Tencent fails, times out, or returns an
invalid payload.

<a id="23-tencent-chart-endpoints-and-5-minute-aggregation"></a>
### 2.3 Tencent chart endpoints and 5-minute aggregation

- Same-day intraday line:
  `GET https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=usAAPL`
- Daily / monthly candles needed for the committed MVP timeframes:
  `GET https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=usAAPL,day|month,,,N,qfq`
- Minute candles for supported higher intraday intervals:
  `GET https://web.ifzq.gtimg.cn/appstock/app/kline/kline?param=usAAPL,m60|m30|m15,,,N`
- Four-hour candles are aggregated locally from completed 1-hour bars using
  exchange-time session boundaries.

For the MVP, the Android app must build **completed 5-minute bars** locally by
aggregating verified 1-minute data in exchange time. Re-check any undocumented
direct `m5` endpoint before release; if it proves stable, it may be used as a
replenishment or fallback source, not as an unverified assumption.

Implementation requirements:

- Persist bars by `(symbol, barStart)`
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
| Live quote snapshot | Poll every 30-60 seconds or refresh manually | Fall back from Tencent to Sina; if both fail, show stale quote status and keep the last good timestamp visible |
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
- [x] Encapsulate Yahoo cookie, crumb, session, and module handling inside the
      direct Android client; do not leak provider quirks into UI code.
- [x] Normalize quote timestamps using the canonical exchange-time model from
      [`architecture.md`](architecture.md).
- [ ] Normalize candle timestamps after historical bar ingestion is added.
- [x] Record `source`, `fetchedAt`, parse status, and `staleAfter` for normalized
      quote and fundamental snapshots.
- [x] Distinguish quote freshness from fundamental freshness in local models and
      repository results.
- [x] Keep quote refresh and cached watchlist data available when Yahoo
      valuation refresh fails.
- [ ] Build completed 5-minute bars locally and never infer from unfinished
      bars.
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
