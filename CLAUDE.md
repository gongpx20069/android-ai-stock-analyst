# CLAUDE.md

Guidance for AI coding agents (Claude Code / Copilot / etc.) working in this repo.
Keep this file short and **universally applicable**; put task-specific detail in `docs/`
and reference it here (progressive disclosure). Loaded every session — prune ruthlessly.

## What this is (WHY)

**AI Stock Analyst** — a self-use, free, **BYOK** (user brings own Azure OpenAI key)
US-stock analysis **Android app**: quotes + basic quant indicators + AI interpretation.
It encodes the owner's *valuation discipline* (judge by upside room + low forward PE;
"falling ≠ cheap") instead of generic up/down prediction. Data-driven, **not** a
buy/sell recommender.

## Current state (READ FIRST)

**Design/documentation stage — there is NO application code yet.** The repo is
`README.md` + `docs/`. Do not assume Flutter source, tests, or a running backend
exist. If asked to build, scaffold from scratch per the locked decisions below.

## Repo map (WHAT)

Read the relevant doc before touching its subsystem; prefer these pointers over
restating their content.

- `docs/architecture.md` — decision log & open questions (**start here**)
- `docs/data-sources.md` — 2026 quote-API research (Tencent / Sina / yfinance)
- `docs/indicators.md` — valuation + 4 support/resistance algorithms + technicals
- `docs/stock-screening.md` — screening funnel + valuation scoring
- `docs/ux-design.md` — page architecture, user journey, behavioral hedging
- `docs/design-system/ai-stock-analyst/MASTER.md` — design tokens (color/type/spacing/charts)
- `docs/visualization.md` — chart specs (valuation-first)
- `docs/ml-prediction.md` — optional LightGBM direction probability (auxiliary only)
- `docs/github-benchmark-and-plan.md` — competitor benchmark + chosen AI architecture

## Locked decisions (do NOT silently override)

- **Tech stack = Flutter / Dart** (reuses mochi-pet packaging; Java/Kotlin/PWA rejected).
  Charts via `fl_chart`.
- **Data = 方案③ hybrid**: realtime price from Tencent/Sina domestic APIs (keyless, fast
  in China); analyst target price / forward PE from a thin **FastAPI + yfinance** backend
  with daily cache. yfinance is Python-only → cannot run on-device, hence the backend.
- **AI = BYOK Azure OpenAI** (Endpoint / Key / Deployment / API-Version — *not* a clean
  OpenAI drop-in). Keys stay on-device.
- **AI architecture = trimmed 3+1 multi-agent** (fundamental / technical / risk + arbiter),
  modeled on TradingAgents.
- **ML = LightGBM** (not XGBoost); direction probability is an *auxiliary* signal only.

## Non-obvious rules & gotchas (YOU MUST follow)

- **Reversed color semantics**: 🔴 red = *expensive / chasing-high* (NOT "price down");
  🟢 green = *has upside room* (NOT "price up"). Deliberate behavioral hedge against the
  owner's buy-the-dip instinct. Color must **never** carry meaning alone — always pair
  with ↑↓ icon + text label (accessibility). Details in `docs/ux-design.md`.
- **Use the median (not mean) analyst target price**; upside% = median target ÷ current − 1.
- **Never commit secrets**: BYOK keys, `.env`, `*.key`, `local.properties` are gitignored —
  keep it that way.
- Quote-API quirks (see `docs/data-sources.md`): Tencent `qt.gtimg.cn/q=usNVDA`
  (`us` prefix, **no** `.OQ` suffix); Sina `hq.sinajs.cn/list=gb_aapl` (`gb_` prefix +
  lowercase, needs `Referer: finance.sina.com.cn`, GBK→UTF-8).

## How to work here (HOW)

- Branch `main` (auto-deploy). Commits = **Conventional Commits with Chinese descriptions**,
  e.g. `docs(ux): 定稿反直觉配色语义`.
- Read a file before editing it; make focused patches; keep `docs/` orderly and cross-linked.
  When adding/removing a `docs/` file, update the index in `README.md`.
- **Verify, don't assert**: docs stage has no test suite — verify by re-reading changed
  files and checking cross-doc links. Once Flutter is scaffolded the check becomes
  `flutter analyze && flutter test`; a Python backend adds `pytest`. Show the command +
  output as evidence.
