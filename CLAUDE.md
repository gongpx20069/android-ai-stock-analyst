# CLAUDE.md

Repository operating guide for coding agents. Keep this file concise and
universally applicable; put subsystem detail in `docs/` and follow links only
when the task requires it. `README.md` is the English user-facing entry point,
and `README.zh-CN.md` is the Chinese user-facing entry point.

## 1. Mission and current state

AI Stock Analyst is a self-use, free, BYOK US-stock research Android app. It
prioritizes valuation discipline (median target-price upside plus low forward
P/E) over generic price prediction. It is data-driven and never a buy/sell
recommender.

Implementation is in progress. The repository contains a native
Kotlin/Compose multi-module project with an application shell, design tokens,
market-domain models, and deterministic unit tests. The locked product
architecture remains serverless and local-only: there is no project server,
and the shipped app must not depend on a Python runtime unless the user
explicitly reopens that decision.

## 2. Source-of-truth hierarchy

Resolve conflicts in this order:

1. `docs/architecture.md` - locked decisions and open questions.
2. The owning subsystem document listed below.
3. `docs/design-system/ai-stock-analyst/MASTER.md` - visual and accessibility
   values.
4. `README.md` and `README.zh-CN.md` - user-facing summaries, never engineering
   specifications.

Update the owner document instead of duplicating detailed rules elsewhere.

## 3. Complete repository map and quick routing

| File | Owner / when to read |
|---|---|
| [`README.md`](README.md) | English user entry point; product value, status, and docs navigation |
| [`README.zh-CN.md`](README.zh-CN.md) | Chinese user entry point; product overview and docs navigation |
| [`CLAUDE.md`](CLAUDE.md) | Agent harness entry point; routing, guardrails, workflow, and validation |
| [`.gitignore`](.gitignore) | Android build output, local tooling artifacts, local config, keystores, and BYOK secret exclusions |
| [`docs/architecture.md`](docs/architecture.md) | **Start here** for stack, boundaries, decisions, and delivery order |
| [`docs/data-sources.md`](docs/data-sources.md) | Direct Android provider clients, endpoint quirks, freshness, and fallback |
| [`docs/analysis.md`](docs/analysis.md) | Formulas, screening, 3+1 interpretation flow, and local LightGBM inference |
| [`docs/design.md`](docs/design.md) | Screens, UX flows, TradingView-inspired chart interactions, and state design |
| [`docs/design-system/ai-stock-analyst/MASTER.md`](docs/design-system/ai-stock-analyst/MASTER.md) | Exact visual tokens, accessibility rules, and chart semantics |
| [`docs/ai-prompt.md`](docs/ai-prompt.md) | Azure OpenAI prompt contract, agent roles, safety rules, and JSON output |
| [`settings.gradle.kts`](settings.gradle.kts) and [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | Module graph, repositories, plugins, and dependency versions |
| [`app/`](app/) | Android application, four-tab Compose shell, resources, and Hilt dependency graph |
| [`core/data/`](core/data/) | Local-first market repository, refresh results, and stale-cache behavior |
| [`core/database/`](core/database/) | Room entities, DAOs, schema, and market-model mappings |
| [`core/datastore/`](core/datastore/) | Independent quote, chart, and valuation provider preferences |
| [`core/model/`](core/model/) | Canonical symbols, exchanges, quotes, bars, valuation snapshots, and predictions |
| [`core/domain/`](core/domain/) | Deterministic calculations and exchange/device-time conversion |
| [`core/designsystem/`](core/designsystem/) | Compose theme, design tokens, typography, and semantic colors |
| [`core/network/`](core/network/) | Tencent/Sina quote clients, Yahoo valuation client, parsing, and provider fallback |

Routing shortcuts:

- Stack, modules, dependencies, or roadmap -> `docs/architecture.md`
- HTTP fields, direct-provider contracts, exchange-time normalization, freshness, and fallback -> `docs/data-sources.md`
- Indicator math, screening, or local ML contract -> `docs/analysis.md`
- Page behavior or chart gestures -> `docs/design.md`
- Exact color, type, spacing, or chart semantics -> `MASTER.md`
- LLM inputs, outputs, or disclaimer text -> `docs/ai-prompt.md`

## 4. Agent guardrails

- Do not override locked decisions in
  [`docs/architecture.md`](docs/architecture.md).
- Do not introduce a project server, any other server-side service, or Python runtime into
  the shipped product unless the user explicitly reopens that decision in
  [`docs/architecture.md`](docs/architecture.md).
- Median target-price logic and reversed valuation colors are defined in
  [`docs/analysis.md` §1.1](docs/analysis.md#11-valuation-as-the-discipline-backbone)
  and
  [MASTER §2](docs/design-system/ai-stock-analyst/MASTER.md#2-semantic-layer-reversed-valuation-colors-the-projects-core).
- ML stays auxiliary: never fabricate future candles, deterministic price
  paths, or stronger claims than the model contract allows. See
  [`docs/analysis.md` §4.3](docs/analysis.md#43-live-inference-and-visualization-contract)
  and
  [MASTER §5](docs/design-system/ai-stock-analyst/MASTER.md#5-chart-color-rules-for-vico-plus-compose-overlays).
- Never activate an imported ONNX model without signature, checksum,
  feature-schema, horizon, and runtime-compatibility validation plus rollback.
- Never commit or expose API keys, `.env`, `local.properties`, keystores,
  signing properties, generated APK/AAB files, caches, or unapproved training
  artifacts. Versioned ONNX models intentionally shipped with the APK are
  source assets and must remain committed.
- Every Markdown file must stay English-only except `README.zh-CN.md`.
- When adding, removing, or renaming a document, update `README.md`,
  `README.zh-CN.md`, this repository map, and `docs/architecture.md` in the
  same change.

## 5. Harness workflow

1. Read `docs/architecture.md`, then only the owning documents for the task.
2. Search before adding helpers, rules, files, or dependencies.
3. Make focused changes and preserve unrelated work in a dirty worktree.
4. Keep one source of truth; add cross-links rather than copied specifications.
5. Record newly locked or reopened decisions in `docs/architecture.md`.
6. Validate the smallest relevant surface and report failures plainly.

## 6. Validation and completion

Documentation stage:

- Re-read changed sections.
- Verify every relative Markdown link resolves.
- Search for superseded stack references and invalid links to missing files.

```powershell
.\gradlew.bat lint test
```

A change is complete only when implementation, directly related docs, secret
handling, and the relevant validation agree.

## 7. Git conventions

- Default branch: `main`.
- Commits: Conventional Commits with Chinese-language descriptions.
- Do not amend, force-push, or discard user changes unless explicitly asked.
