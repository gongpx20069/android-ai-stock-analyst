# Analysis and AI Methods

> This document owns the app's **deterministic analysis logic**, **screening
> rules**, the selected **3+1 AI interpretation flow**, and the **LightGBM
> prediction contract**. Hard indicators in §1-§2 are facts with stable
> formulas. AI in §3 interprets those facts. ML in §4 provides probability
> references only. For provider contracts and freshness, see
> [`data-sources.md`](data-sources.md). For prompt schemas and the canonical AI
> disclaimer, see [`ai-prompt.md`](ai-prompt.md).

---

<a id="1-valuation-metrics-support-and-resistance-and-technicals"></a>
## 1. Valuation metrics, support and resistance, and technicals

| Category | Indicators | Discipline purpose | Data source |
|---|---|---|---|
| **Valuation** | Current price, **median target price**, upside percent, forward P/E, rating, analyst count | Primary axis: upside plus low forward P/E | Fundamental snapshot |
| **Support and resistance** | Pivot points, swing highs/lows, Fibonacci, moving averages, 52-week highs/lows | Show room above and likely support below | Price history |
| **Technicals** | MA50/200, RSI, MACD, ATR, Bollinger Bands | Secondary context for overheating, trend, and volatility | Price history |

### 1.1 Valuation as the discipline backbone

| Metric | Formula | Field |
|---|---|---|
| Upside percent | `(median target price / current price - 1) * 100%` | `targetMedianPrice / currentPrice` |
| Forward P/E | Direct field | `forwardPE` |
| Rating | Normalized bucket or recommendation label | `recommendationKey` and related bucket counts |
| Analyst count | Total analysts covering the stock | `numberOfAnalystOpinions` |

> Use the **median** target price, not the mean. Median reduces the influence
> of outliers and preserves the product's valuation discipline.

<a id="12-support-and-resistance-levels-four-required-methods"></a>
### 1.2 Support and resistance levels: four required methods

Support and resistance are not predictions. They are reusable reference levels
derived from price history. The product requires **four methods**, and any
level confirmed by at least two methods is treated as a stronger signal.

#### 1. Classic pivot points for short-term and intraday use

Use the previous daily candle's high `H`, low `L`, and close `C`:

```text
PP (pivot) = (H + L + C) / 3
R1 = 2 * PP - L      S1 = 2 * PP - H
R2 = PP + (H - L)    S2 = PP - (H - L)
R3 = H + 2*(PP - L)  S3 = L - 2*(PP - H)
```

- Best for short-term and intraday reference.

#### 2. Swing highs and lows for medium-term structure

Find local extrema across a rolling window:

```text
local maxima = highs that equal the rolling-window maximum
local minima = lows that equal the rolling-window minimum
```

- Best for medium-term watchlist review and range awareness.
- Nearest highs above current price become resistance; nearest lows below it
  become support.

#### 3. Fibonacci retracement for pullback zones

Use the chosen swing high and low:

```text
Level = High - ratio * (High - Low)
```

- Track at least 23.6%, 38.2%, 50%, and 61.8%.

#### 4. Moving averages plus 52-week highs and lows for dynamic levels

- **MA50 / MA200:** dynamic support or resistance that moves with trend.
- **52-week high / 52-week low:** annual ceiling and floor; proximity to the
  high feeds anti-chasing warnings.

#### Combined presentation on the detail page

```text
Resistance: nearest levels above current price, labeled by method and distance
Current price: centered reference marker
Support: nearest levels below current price, labeled by method and distance
Median target: separate valuation reference, not a support/resistance level
```

The main UI summary is a pair of numeric cards:
`distance to nearest support` and `distance to nearest resistance`.

<a id="13-technicals-as-a-supporting-role"></a>
### 1.3 Technicals as a supporting role

| Indicator | Meaning | Reference line |
|---|---|---|
| MA50 / MA200 | Medium and long-term trend | Golden cross / death cross |
| RSI(14) | Overbought or oversold | `>70` overbought / `<30` oversold |
| MACD | Momentum | Histogram crossing from negative to positive suggests momentum improvement |
| ATR | Volatility range | Useful for distance and risk framing |
| Bollinger Bands | Price channel | Upper band = richer, lower band = cheaper |

> The MVP includes only **MA50/200, RSI(14), and 52-week positioning**.
> MACD, Bollinger Bands, and ATR are phase-two additions.

### 1.4 Calculation pipeline and inputs for AI and ML

```text
[Android provider clients]
  ├─ Tencent / Sina -> quotes
  ├─ User-selected Alpaca Basic -> Live IEX, non-consolidated OHLCV
  └─ Yahoo Finance -> valuation and analyst snapshot
        ▼
[Room repositories]
        ▼
[Local Kotlin use cases]
  ├─ Valuation snapshot
  ├─ Support/resistance: price history -> 4 methods -> level set
  ├─ Technicals: price history -> deterministic calculations
  └─ Shared analysis snapshot for UI, screening, AI, and ML
```

- Support/resistance and technical indicators are pure calculations.
- They are calculated locally in Kotlin; there is no project-server endpoint for
  indicator calculation.
- AI inputs from §3 include upside percent, forward P/E, rating, analyst count,
  support/resistance distances, RSI, moving averages, and freshness warnings.
- ML features from §4 should include valuation and level-distance signals so the
  model learns from the same discipline used in the UI.

---

## 2. Stock-selection pipeline and valuation scoring

The product ranks candidates by deterministic rules first and uses AI only for
interpretation after ranking. A price drop alone never qualifies as value.

<a id="21-stock-selection-pipeline-four-stage-funnel"></a>
### 2.1 Stock-selection pipeline: four-stage funnel

```text
[Configured universe] -> [Hard filters] -> [Valuation scoring and ranking] -> [AI review of Top N]
```

**Step 1: universe**

- Load the NASDAQ, NYSE, and NYSE American universe from
  [`data-sources.md` §2.5](data-sources.md#25-us-exchange-screening-universe).
- Keep common stocks and data-complete ADRs; exclude ETFs, funds, warrants,
  rights, units, and preferred shares before valuation scoring.

**Step 2: hard filters**

- Market cap >= $10B
- Current price >= $5
- Average daily volume >= 500K
- Non-empty sector or industry
- Optional sector whitelist or blacklist

**Step 3: valuation scoring and ranking**

| Dimension | Field | Rule | Suggested weight |
|---|---|---|---|
| **Upside percent** | `targetMedianPrice / currentPrice - 1` | Higher is better; remove values below 0 | 40% |
| **Forward P/E** | `forwardPE` | Lower is better; penalize negative or extreme values | 30% |
| **Analyst confidence** | `numberOfAnalystOpinions` | Penalize coverage below 5 analysts | 10% |
| **Rating** | `recommendationKey` | Reward `buy` or `strong_buy`; remove `sell` | 10% |
| **Positioning / anti-chasing** | Current price vs 52-week high or 200-day average | Penalize names too close to the 52-week high, such as above 95% | 10% |

The ranked output should include symbol, name, current price, median target
price, upside percent, forward P/E, rating, and analyst count.

**Step 4: AI interpretation of the Top N**

- Send only the top 5-10 names and their structured facts to the user's Azure
  OpenAI configuration.
- AI explains valuation credibility, technical context, and risk. It does not
  re-rank the universe.
- Prompt schemas and output validation live only in
  [`ai-prompt.md`](ai-prompt.md).

<a id="22-two-stock-selection-entry-points-as-product-behavior"></a>
### 2.2 Two stock-selection entry points as product behavior

| Entry point | Scenario | Implementation |
|---|---|---|
| **A. Watchlist monitoring** | Reviewing tracked names already saved by the user | Run the ranking and interpretation steps on a fixed list |
| **B. Three-exchange screening** | Finding new candidates across NASDAQ, NYSE, and NYSE American operating companies | Run the full funnel locally in the MVP |

Both entry points are part of the MVP. Watchlist monitoring remains the user's
daily cockpit, while Screening is a fully working discovery surface.

### 2.3 Performance and quota reality

Three-exchange screening becomes expensive when large survivor lists require
heavier fundamental lookups. The mitigation plan is:

1. Use a staged funnel so light filters eliminate most names before the
   expensive fields are needed.
2. Cache slower-moving fundamentals and analyst fields locally in Room on a
   daily cadence.
3. Run refreshes in WorkManager batches with resumable stage markers and page
   cursors.
4. Page results so the UI can show ranked names quickly without waiting for the
   full universe.

Target price and forward P/E must come from the selected fundamental snapshot.
The screening engine should query normalized cached data, not live source
responses, for ranked results.

---

<a id="3-multi-agent-ai-interpretation"></a>
## 3. Multi-agent AI interpretation

AI is used for **structured interpretation**, not for replacing formulas,
screening, or the data contract. The selected design keeps token cost low,
keeps agent roles legible, and aligns with the canonical schemas in
[`ai-prompt.md`](ai-prompt.md).

<a id="31-streamlined-3-plus-1-flow"></a>
### 3.1 Streamlined 3 plus 1 flow

```text
1. Fundamental / valuation agent
2. Technical agent
3. Risk / counter-argument agent
        ↓ three parallel viewpoints
4. Arbiter agent
```

| Agent | Required focus |
|---|---|
| Fundamental / valuation | Upside, forward P/E, analyst coverage, rating strength, and valuation-state interpretation |
| Technical | Support/resistance distances, MA context, RSI, 52-week position, and anti-chasing signals |
| Risk / counter-argument | Data freshness, overheating, conflicting evidence, model weakness, and reasons not to over-trust the setup |
| Arbiter | Agreements, conflicts, confidence, valuation state, key evidence, risk flags, watch conditions, and freshness warnings |

- The first three agents may run in parallel.
- The arbiter consumes only the shared input snapshot plus the three structured
  agent outputs.
- Output schemas, validation, and the canonical disclaimer belong only to
  [`ai-prompt.md`](ai-prompt.md).
- LightGBM outputs from §4 may inform the risk agent and arbiter, but ML never
  becomes a standalone recommendation engine.

---

<a id="4-ml-support-signals-lightgbm-direction-models"></a>
## 4. ML support signals: LightGBM direction models

ML is an auxiliary signal. It should surface uncertainty honestly and must never
replace the valuation backbone.

### 4.1 Layering and model choice

| Tier | Method | Prediction target | Explainability | Positioning |
|---|---|---|---|---|
| **T1 Time series** | Prophet / ARIMA | Price range over the next N days with confidence bands | High | Backup option only |
| **T2 Tree models** | XGBoost / LightGBM | **Direction probability** over the next N days | High | **Selected starting point** |
| **T3 Deep learning** | LSTM / GRU / Transformer | Price sequence or direction | Low | Optional phase-two path |

LightGBM is the selected MVP path because it offers the best trade-off between
speed, explainability, and operational simplicity for direction classification.

### 4.2 Implementation details by method

**T1: time series - backup only**

- Input: one stock's historical close series.
- Output: projected range with uncertainty bands.
- Limitation: trend extrapolation only; not the primary product signal.

**T2: LightGBM classification - selected**

- Use separate models for:
  - `intraday`: after each completed 5-minute bar, predict the probability of
    direction over the next 30 minutes.
  - `daily`: after market close, predict the probability of direction over the
    next 5 trading days.
- Prefer LightGBM over XGBoost for faster training and lower operational cost.
- Feature groups:
  - Short-horizon returns, volume change, VWAP deviation, and volatility
  - Daily trend and technical features such as MA deviation and RSI
  - Valuation features: upside percent, forward P/E, rating
  - Support/resistance features: distance to nearest support and resistance
  - Volatility features such as ATR or historical volatility
- Development and release tooling may use `lightgbm` plus `scikit-learn` for
  splitting, evaluation, calibration, export, and validation. None of that
  training runtime ships as an in-app dependency.
- Required evaluation discipline: strict time splits, rolling backtests, and
  calibration checks.
- Runtime contract:
  - No project server training
  - No on-device LightGBM training in the MVP
  - Train and export during development or release tooling
  - Convert the selected model to ONNX `TreeEnsemble`
  - Bundle a versioned `.onnx` asset in the APK for MVP
  - Run inference with ONNX Runtime Android
  - Allow signed local ONNX model packages after installation
  - Verify package signature, model checksum, feature-schema version, horizons,
    input shape, and runtime compatibility before activation
  - Install atomically and retain the last valid model for rollback

**T3: deep learning - phase two**

- Revisit only after the T2 pipeline is stable and demonstrably useful.

<a id="43-live-inference-and-visualization-contract"></a>
### 4.3 Live inference and visualization contract

#### Inference timing

| Signal | Input | Inference moment | Training cadence | Display |
|---|---|---|---|---|
| `30m` live direction | Completed local 5-minute OHLCV plus intraday features and the latest cached fundamental snapshot | After every completed 5-minute bar | Evaluated during release tooling; updated by APK release or verified signed import | Probability line on the market chart plus summary card |
| `5d` medium-term direction | Daily technical, valuation, and support/resistance features | After each market close | Evaluated during release tooling; updated by APK release or verified signed import | ML card or gauge |

The app may poll snapshots more frequently, but it must update the visible
prediction only when `asOf` changes. Calculations use exchange-time-correct bar
boundaries, while displayed timestamps are converted to the device local
timezone.

The local model layer must expose at least this contract:

```kotlin
data class PredictionSnapshot(
    val symbol: String,
    val horizon: String,
    val asOf: Instant,
    val probabilityUp: Double,
    val confidence: String,
    val modelVersion: String,
    val featureWindow: String,
    val dataFreshness: String,
    val walkForwardAccuracy: Double?,
    val calibrationError: Double?
)
```

The client must reject any snapshot that omits `horizon`, `asOf`, or
`modelVersion`, and it must surface stale-state warnings clearly.

#### Visualization

- Chart placement and interaction live in [`design.md`](design.md).
- Visual tokens live in
  [MASTER](design-system/ai-stock-analyst/MASTER.md).
- Model-facing text should reuse the canonical disclaimer from
  [`ai-prompt.md`](ai-prompt.md).

Allowed model visuals:

1. A probability gauge or badge labeled with horizon and snapshot time
2. Feature-importance bars for explainability
3. Backtest comparison charts
4. A time-series probability line for the `30m` classifier, rendered on its own
   labeled scale or sub-panel rather than as a fabricated future price path

Not allowed:

- Fake future candles
- Deterministic price paths from a classifier
- Hidden backtest quality

**Runtime architecture**

```text
[Development / release tooling]
  -> train and validate LightGBM models offline
  -> export selected models to ONNX TreeEnsemble
  -> package versioned .onnx assets with the APK
  -> optionally produce signed import packages
        ▼
[Android app]
  -> verify and atomically activate bundled or imported model packages
  -> build completed local 5-minute bars
  -> run ONNX Runtime Android inference after each completed bar or market close
  -> persist versioned PredictionSnapshot records in Room
  -> render probability, uncertainty, freshness, and backtest quality
```

**Real-time display policy**

1. Show every structurally valid `30m` and `5d` prediction without applying an
   accuracy or calibration threshold.
2. Always show model version, freshness, walk-forward quality, calibration
   quality, and the canonical disclaimer beside the prediction.
3. Use `Model temporarily unavailable` only when the model package is invalid,
   required features are missing, inference fails, or the snapshot is stale.
4. Prediction output must remain subordinate to valuation and risk
   interpretation.
5. The `30m` prediction line must remain a probability visualization, not a
   fake future price line or future-candle preview.

**Signed model package contract**

A locally imported package must include the ONNX model plus a signed manifest
containing at least:

- model identifier and version
- supported horizon
- feature-schema version and ordered feature list
- input shape and ONNX Runtime compatibility
- training cutoff
- walk-forward and calibration metrics
- model checksum
- package signature and signer identifier

The app verifies the signature against a trusted local public key, validates
the checksum and schema, installs the package atomically, and preserves the
previous valid model for rollback.
