# AI Stock Analyst

Looking for Chinese? See [README.zh-CN.md](README.zh-CN.md).

AI Stock Analyst is a free, serverless, local-only runtime, BYOK Android app
for US-stock research. No project server is required. It combines live quotes,
valuation and technical indicators, on-device LightGBM probability signals,
and Azure OpenAI interpretation without presenting itself as a buy/sell
recommender.

## Why it is different

- **Valuation first:** judge a stock by median analyst target upside and
  forward P/E, not by whether its price recently fell.
- **Native Android:** the APK will be built with Kotlin and Jetpack Compose.
- **TradingView-inspired charting:** candlesticks, volume histogram,
  pan/zoom, crosshair, minute/hour/day/month views, and a clearly labeled
  LightGBM probability line. TradingView code, branding, and proprietary
  assets are not bundled.
- **Local-only runtime:** quote fetching, caching, screening, indicator
  calculation, and ML inference run inside the Android app.
- **User-selected data sources:** quote, chart, and valuation providers are
  configured independently and stored only on-device.
- **English and Chinese UI:** follows the Android system language by default,
  with an in-app override for English or Simplified Chinese.
- **Direct update and feedback links:** checks this repository's latest GitHub
  Release on launch or on demand, and links to the signed APK, Issues, and
  maintainer email.
- **BYOK Azure OpenAI:** users provide their own Endpoint, Key, Deployment,
  and API Version. The key is encrypted on-device and sent only to the
  configured Azure endpoint.
- **Watchlist-only:** the product supports watchlists and research suggestions,
  not holdings, portfolio lots, cost basis, staged buys, partial sells, trade
  execution, or brokerage features.
- **Honest ML:** LightGBM outputs calibrated probabilities and uncertainty. It
  does not create deterministic future candles, future price paths, or
  investment instructions.

## Status

Implementation has started. The repository now contains the native
Kotlin/Compose project, four-tab application shell, shared design system,
market-domain models, user-selectable Tencent/Sina quote routing, an isolated
Yahoo Finance cookie/crumb valuation client, Room-backed quote and valuation
caches, DataStore provider settings, and a Hilt-wired repository with explicit
stale-cache results. Eastmoney Experimental is the default no-account chart
provider, with exact US-symbol resolution, unadjusted K-lines, short-retention
capability errors, and explicit unofficial-interface and licensing disclosure.
Alpaca Basic remains an opt-in encrypted-BYOK alternative with explicit Live
IEX, non-consolidated disclosure. Source-isolated bars flow into the canonical
`PriceBar` model and Room cache. Alpaca 5-minute bars are derived locally from
1-minute IEX history; Eastmoney keeps native 5-minute bars and locally
aggregates completed 4-hour session buckets. The local domain layer now calculates MA50, MA200, and
Wilder RSI(14) from completed valid daily bars and exposes a freshness-aware
snapshot through the repository. The same normalized history now drives
52-week positioning plus pivot, swing, Fibonacci, moving-average, and
52-week support/resistance levels with cross-method resonance. The Watchlist
tab keeps stock lookup controls visible after selection and places its chart
first, before quote, valuation, support/resistance, and technical cards. Its
Vico chart renders neutral candlesticks and an independently scaled volume
histogram with device-local timestamps, pan/zoom, reset-to-latest behavior, and
dynamic Eastmoney/Alpaca disclosure. Watchlist persistence, screening, chart probability overlays, and
ONNX inference remain to be implemented. The app UI now supports English and
Simplified Chinese, follows the system by default, and exposes GitHub Release
update checks plus Issue and email feedback links in Me.

## Documentation

| Document | Purpose |
|---|---|
| [Chinese user README](README.zh-CN.md) | Chinese-language product overview and navigation |
| [Architecture](docs/architecture.md) | System boundaries, locked decisions, open questions, and delivery order |
| [Data sources](docs/data-sources.md) | Direct Android provider clients, endpoint contracts, freshness, and fallback |
| [Analysis and AI](docs/analysis.md) | Valuation, screening, 3+1 interpretation, and local LightGBM |
| [Product design](docs/design.md) | Screens, journeys, TradingView-inspired chart behavior, and state design |
| [Design system](docs/design-system/ai-stock-analyst/MASTER.md) | Visual tokens, accessibility, colors, typography, and chart semantics |
| [AI prompt contract](docs/ai-prompt.md) | Agent responsibilities, prompt invariants, and structured output schema |
| [APK releases](docs/releasing.md) | Shared signing, automatic `1.0.x` versioning, local publishing, and manual Actions releases |

## Disclaimer

This project is for personal research and education. Model outputs, analyst
targets, and AI summaries can be delayed, incomplete, or wrong and are not
investment advice.
