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
stale-cache results. Alpaca Basic is available as an opt-in chart provider
using encrypted user credentials and explicit Live IEX, non-consolidated feed
disclosure. Its paginated split-adjusted bars now flow into the canonical
`PriceBar` model and Room v2 cache. Completed 5-minute bars are now derived
locally from exchange-time-aligned 1-minute IEX bars and persisted atomically
with their source range. The local domain layer now calculates MA50, MA200, and
Wilder RSI(14) from completed valid daily bars and exposes a freshness-aware
snapshot through the repository. Chart UI, support/resistance, screening, and
ONNX inference remain to be implemented.

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

## Disclaimer

This project is for personal research and education. Model outputs, analyst
targets, and AI summaries can be delayed, incomplete, or wrong and are not
investment advice.
