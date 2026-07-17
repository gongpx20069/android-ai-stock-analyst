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
| Intraday and higher-timeframe OHLCV | User choice: Not configured or Alpaca Basic Live IEX | Alpaca is opt-in BYOK; IEX is one exchange and is never represented as consolidated SIP data |
| Screening universe | Nasdaq public screener | Operating-company securities listed on NASDAQ, NYSE, and NYSE American |
| Fundamental and analyst snapshot | Yahoo Finance (default), Finnhub BYOK, or FMP BYOK | Explicit selection never falls back; snapshots are cached by symbol and actual source |
| Derived indicators | Local Kotlin calculation engine | Deterministic indicators are calculated on-device |
| Prediction snapshots | ONNX Runtime Android | LightGBM inference runs on-device from bundled model assets |

The app uses a split design on purpose: fast quote access comes from domestic
quote endpoints, while slower-moving valuation data is normalized separately in
local storage. No project server exists between the app and these providers.

Vico is not a market-data company or account requirement. It is the in-app
open-source rendering library. A separate chart-data provider is still needed
because candlesticks require historical open, high, low, close, and volume
records. The validated Tencent and Sina integrations currently supply quote
snapshots, not a reliable multi-timeframe US OHLCV contract, so Alpaca Basic is
the current optional BYOK source for those records.

Provider choices are separate settings rather than one global source:

- Quotes: `Auto` (default), Tencent-only, or Sina-only.
- Charts: `Not configured` (default), or Alpaca Basic Live IEX with encrypted
  user credentials. The setting remains independent from quote behavior.
- Valuation: Yahoo Finance (default), Finnhub BYOK, or Financial Modeling Prep
  (FMP) BYOK. The setting is independent so valuation availability never
  controls quote or chart refresh.

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

<a id="23-alpaca-basic-live-iex-chart-contract"></a>
### 2.3 Alpaca Basic Live IEX chart contract

Alpaca Basic is an optional BYOK chart source. It is never an automatic
fallback and is not selected until the user saves credentials and explicitly
chooses it. Send credentials only through these request headers:

```text
APCA-API-KEY-ID: <user key ID>
APCA-API-SECRET-KEY: <user secret key>
```

The app encrypts both values with an AES-GCM key held by Android Keystore.
Credentials must not appear in URLs, logs, Room, DataStore, backups, or source
control.

The Me page guides the user through the direct BYOK setup:

1. Open Alpaca's official account-registration page.
2. Open the official Dashboard, find **API Keys**, and generate a new key pair.
3. Copy the API Key ID and one-time-visible Secret Key into the app.
4. Encrypt the pair locally and use it only with Alpaca Market Data requests.

The app uses these credentials only with Market Data endpoints. It never calls
trading or order endpoints and never sends the credentials to a project server.
Alpaca Connect OAuth is not used because its documented authorization-code
exchange requires `client_secret` and directs that exchange to a backend; its
Connect documentation does not define PKCE or another secretless native-client
flow. Embedding that shared secret in an open-source APK would expose it.

Historical bars use:

```text
GET https://data.alpaca.markets/v2/stocks/bars
```

Request contract:

- `symbols=<provider symbol>`
- `timeframe=1Min|5Min|15Min|30Min|1Hour|4Hour|1Day|1Month`
- explicit inclusive RFC 3339 `start` and `end`
- `limit=10000`
- `sort=asc`
- `feed=iex`
- `adjustment=split`
- the opaque `page_token` from the previous response when present

The client must follow `next_page_token` until it is absent even when a page
contains fewer rows than the requested limit. The response `bars` object maps
the provider symbol to arrays containing `t`, `o`, `h`, `l`, `c`, and `v`.
`t` is the bar's UTC start boundary. Missing symbol arrays and missing
no-trade intervals are valid empty results; malformed bars fail the refresh
rather than being silently dropped.

Only completed bars whose start and end lie inside the requested range are
returned. Upserts use `(symbol, exchange, interval, barStart)`, so a provider
correction replaces the earlier record. Alpaca's stream may publish
`updatedBars` after late trades; streaming correction ingestion is not yet
implemented and must be added before continuous live chart updates.

Alpaca Basic limitations are product-visible:

- Live equities are the IEX feed only, not consolidated SIP data.
- IEX represents only a subset of US trading activity. OHLCV and volume can
  differ materially from full-market values and must not support
  consolidated-volume claims.
- The Basic contract advertises historical data from 2016, 200 historical
  requests per minute, and 30 WebSocket symbol subscriptions.
- A user-owned account and credentials are required. Before public
  distribution, obtain provider confirmation that an independently
  distributed open-source BYOK client is permitted to render each user's own
  locally fetched data.

Official contract references:

- [Create an Alpaca account](https://app.alpaca.markets/signup)
- [Dashboard API Keys](https://app.alpaca.markets/brokerage/dashboard/overview)
- [Getting started with Market Data](https://docs.alpaca.markets/us/docs/getting-started-with-alpaca-market-data)
- [Alpaca Connect OAuth](https://docs.alpaca.markets/us/docs/using-oauth2-and-trading-api)
- [Historical stock bars](https://docs.alpaca.markets/us/reference/stockbars)
- [Market Data API plans](https://docs.alpaca.markets/us/docs/about-market-data-api)
- [Historical stock data and feeds](https://docs.alpaca.markets/us/docs/historical-stock-data-1)
- [Market-data FAQ](https://docs.alpaca.markets/us/docs/market-data-faq)
- [Real-time stock data](https://docs.alpaca.markets/us/docs/real-time-stock-pricing-data)

<a id="24-tencent-chart-endpoints-and-5-minute-aggregation"></a>
### 2.4 Rejected Tencent chart endpoints and 5-minute requirement

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
These endpoints remain rejected even though Alpaca is now available. They
must not be reintroduced as chart fallbacks.

For the MVP, the Android app builds **completed 5-minute bars** locally by
aggregating verified Alpaca IEX 1-minute data in exchange time. Alpaca's native
`5Min` interval is not used for the canonical five-minute refresh path. The
rejected Tencent `m5` endpoint must not be used as replenishment or fallback.

Local aggregation contract:

- Align each source minute to a five-minute wall-clock bucket in the
  symbol's exchange timezone.
- Emit a bucket only after its end boundary has passed.
- Use the first open, maximum high, minimum low, last close, and exact summed
  source volume.
- Preserve the Alpaca IEX source so every derived chart continues to disclose
  its non-consolidated feed.
- Treat an absent source minute as a possible no-trade interval; do not create
  a synthetic flat or zero-volume minute.
- When duplicate provider versions share a minute start, use the version with
  the latest `fetchedAt`.
- Expand unaligned refreshes to enclosing five-minute exchange-time boundaries
  before fetching and replacing cache ranges, then return only bars fully
  contained by the caller's original range.
- Route both one-minute and five-minute refresh requests through the coupled
  transaction so source corrections always regenerate affected derived bars.
- Replace the requested one-minute and five-minute Room ranges in one
  transaction so provider corrections cannot leave mixed generations.

Implementation requirements:

- Persist bars by `(symbol, exchange, interval, barStart)`
- Deduplicate source overlap
- Never use an unfinished bar as a model label or inference input
- Normalize timestamps according to the canonical symbol and exchange-time model
  in [`architecture.md`](architecture.md)
- Keep exchange-time grouping and bar boundaries in storage; convert visible
  time labels to the device local timezone in the UI layer

---

<a id="25-us-exchange-screening-universe"></a>
### 2.5 US exchange screening universe

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

## 3. Valuation provider contracts

Valuation clients normalize provider-specific responses into one snapshot but
do not invent unavailable fields. Explicit selection never switches providers
silently. Room keys valuation snapshots by `(symbol, source)`, and partial
refresh preservation compares only snapshots from the same source.

Finnhub and FMP are opt-in BYOK providers. Free endpoint entitlement is not
verified, and permission to display each user's fetched data in a publicly
distributed BYOK application must be confirmed with the provider before public
distribution. The app does not advertise complete free coverage.

Credentials use Android-Keystore-backed AES-GCM encryption. They never enter
ordinary DataStore, Room, backups, source control, logs, UI error messages, or
project servers.

### 3.1 Yahoo Finance HTTP `quoteSummary`

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

### 3.2 Finnhub valuation BYOK contract

Base URL:

```text
https://finnhub.io/api/v1
```

Send the user API key only as:

```text
X-Finnhub-Token: <user API key>
```

The three required requests are:

```text
GET /stock/price-target?symbol=AAPL
GET /stock/recommendation?symbol=AAPL
GET /stock/metric?symbol=AAPL&metric=all
```

Field mapping:

| Endpoint | Provider fields | Canonical use |
|---|---|---|
| `price-target` | `targetLow`, `targetMedian`, `targetHigh`, `numberAnalysts`, `lastUpdated` | Analyst range, target-aligned analyst count, and provider as-of date |
| `recommendation` | newest `period`, `strongBuy`, `buy`, `hold`, `sell`, `strongSell` | Canonical local rating only |
| `metric?metric=all` | `forwardPE`, `50DayMovingAverage`, `200DayMovingAverage`, `52WeekHigh`, `52WeekLow`, `marketCapitalization` | Forward P/E, positioning, and market cap |

`marketCapitalization` is documented by Finnhub in millions. Normalize it to
base currency units by multiplying by `1,000,000`, with range validation before
converting to the canonical integer field.

The recommendation bucket sum is **not** an analyst-count substitute. The
canonical rating uses the newest recommendation period and a deterministic
weighted mean: `strongBuy=5`, `buy=4`, `hold=3`, `sell=2`,
`strongSell=1`. Map scores `>=4.5` to `strong_buy`, `>=3.5` to `buy`,
`>=2.5` to `hold`, `>=1.5` to `sell`, and lower scores to `strong_sell`.
All-zero or absent buckets yield no rating.

Use `lastUpdated` at UTC start-of-day as `asOf` when it is a valid ISO date;
otherwise use `fetchedAt`. An empty optional endpoint object or recommendation
array may produce `ParseStatus.PARTIAL`. A present malformed field is a payload
error. Authentication, entitlement, and other HTTP failures remain provider
failures and never become empty success responses.

Official links:

- [Finnhub signup](https://finnhub.io/register)
- [Finnhub API documentation](https://finnhub.io/docs/api)

### 3.3 Financial Modeling Prep stable valuation BYOK contract

Base URL:

```text
https://financialmodelingprep.com/stable
```

FMP requires query-string authentication:

```text
apikey=<user API key>
```

The client must not attach a logging interceptor. It catches transport failures
at the provider boundary and emits a sanitized provider error without retaining
the request URL or underlying exception, so the query key cannot reach UI
errors or logs.

The three required requests are:

```text
GET /quote?symbol=AAPL&apikey=<redacted>
GET /price-target-consensus?symbol=AAPL&apikey=<redacted>
GET /grades-consensus?symbol=AAPL&apikey=<redacted>
```

Field mapping:

| Endpoint | Provider fields | Canonical use |
|---|---|---|
| `quote` | `yearHigh`, `yearLow`, `priceAvg50`, `priceAvg200`, `marketCap`, `timestamp` | Positioning, base-unit market cap, and `asOf` |
| `price-target-consensus` | `targetHigh`, `targetLow`, `targetConsensus`, `targetMedian` | Analyst range; prefer `targetMedian`, then `targetConsensus` |
| `grades-consensus` | `strongBuy`, `buy`, `hold`, `sell`, `strongSell`, `consensus` | Validate buckets and use provider consensus as rating |

These verified stable contracts do not expose a canonical forward P/E or a
target-aligned analyst count. Both fields remain null; EPS-estimate coverage or
grade-bucket sums must not be substituted or relabeled. FMP snapshots therefore
remain `ParseStatus.PARTIAL`.

Use the quote `timestamp` as `asOf` when it is a valid epoch-second value;
otherwise use `fetchedAt`. Empty optional endpoint arrays may yield partial
data. Present malformed values are payload errors. Authentication, entitlement,
and other HTTP failures remain provider failures.

Official links:

- [FMP signup](https://site.financialmodelingprep.com/register)
- [FMP API documentation](https://site.financialmodelingprep.com/developer/docs)

---

## 4. Freshness, fallback, local cache, and implementation checklist

### 4.1 Freshness and fallback behavior

| Data surface | Freshness expectation | Fallback / stale behavior |
|---|---|---|
| Live quote snapshot | Poll every 30-60 seconds or refresh manually | Auto falls back from Tencent to Sina; explicit Tencent/Sina modes do not switch. On failure, show stale status and keep the last good timestamp visible |
| Intraday bars for charting and ML | Use only completed Alpaca Live IEX bars when configured | If credentials, entitlement, rate limits, or gaps prevent refresh, retain Room history and mark dependent predictions stale rather than guessing |
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

MA50, MA200, and RSI(14) are calculated on demand from the complete valid,
completed Alpaca IEX daily history observed in Room. They are not persisted as
provider facts. Their normalized snapshot records the latest daily-bar
`fetchedAt` and becomes cache-stale after 24 hours without a fresher daily-bar
refresh.

Support/resistance and 52-week positioning are also calculated on demand from
the complete normalized daily history, combined with the cached current quote.
They remain local calculation results rather than provider facts and retain
both quote and bar provenance plus the earlier freshness deadline.

The stock-detail chart observes only the configured chart provider's Room
history. It maps timestamps to the device timezone at presentation time and
keeps the Live IEX volume layer on an independent scale so volume cannot alter
the price axis. Refresh failures retain visible cached bars and surface the
provider error instead of producing an empty success state.

### 4.3 Implementation checklist

- [x] Map Tencent and Sina fields explicitly by positional index; do not rely on
      undocumented field names.
- [x] Persist quote, chart, and valuation provider choices independently in
      DataStore; allow fallback only in explicit Auto quote mode.
- [x] Encapsulate Yahoo session handling and Finnhub/FMP auth, endpoint
      combination, parsing, and redaction inside direct Android clients.
- [x] Normalize quote timestamps using the canonical exchange-time model from
      [`architecture.md`](architecture.md).
- [x] Select an opt-in US OHLCV provider with an official interval,
      pagination, authentication, and adjustment contract.
- [x] Record `source`, `fetchedAt`, parse status, and `staleAfter` for normalized
      quote and fundamental snapshots.
- [x] Distinguish quote freshness from fundamental freshness in local models and
      repository results.
- [x] Keep quote refresh and cached watchlist data available when the selected
      valuation provider fails.
- [x] Store valuation caches by symbol and source; provider switches never
      relabel another provider's snapshot.
- [x] Encrypt independent Finnhub and FMP keys locally and keep FMP query-auth
      URLs out of surfaced exceptions.
- [x] Build completed 5-minute bars locally and never infer from unfinished
      bars.
- [x] Define canonical bar persistence with source, fetch time, parse status,
      interval, and exchange metadata; deduplicate by the Room primary key.
- [x] Present cached multi-timeframe bars with device-local labels, explicit
      Live IEX disclosure, and an independently scaled volume layer.
- [ ] Persist screening progress, page cursors, and stage markers so background
      refreshes can resume cleanly.
- [ ] Revalidate undocumented endpoints, headers, response shapes, and practical
      anti-bot limits before release.
- [ ] Probe Alpaca Basic with user-owned credentials across every timeframe,
      split boundaries, market sessions, pagination, and rate-limit responses.
- [ ] Obtain Alpaca confirmation for open-source third-party BYOK display
      before public distribution.
- [ ] Treat undocumented vendor behavior as revocable; add monitoring and
      backoff instead of assuming stability.

Yahoo may return a regional unavailability page instead of a crumb from
networks where its services are blocked. The client rejects that response
explicitly, and the repository retains the last good valuation cache; the app
must not present this condition as a successful refresh.
