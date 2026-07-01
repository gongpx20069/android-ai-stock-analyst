# AI Stock Analyst 📈🤖

一个**自用、免费、BYOK(填自己的 AI key)** 的美股分析工具:行情 + 基础量化指标 + AI 解读,落地为**安卓 App**。

> 软件本身免费开源,AI 能力由用户自己的 Azure OpenAI / OpenAI key 驱动(只花自己 token 的钱)。

## 这个项目要解决什么

市面上"免费 + 安卓 APK + 带 AI + 能填自己 key"的成熟美股分析 App 几乎不存在(成熟的如 go-stock 只有桌面版)。所以自己写一个,把**我自己的估值纪律**(看上行空间 + 低 PE、在跌≠便宜)直接编码进去,而不是用通用的"涨跌预测"。

## 核心原则

- **数据驱动、非荐股**:输出客观数据 + AI 解读,不做买卖预测。
- **BYOK**:不内置付费后端,用户填自己的 Azure OpenAI key,数据与密钥可控。
- **隐私优先**:行情数据本地处理,key 存在本地,不上传第三方服务器。
- **先 MVP,再迭代**:第一版只做"单只股票分析",跑通再扩展。

## 状态

🚧 设计讨论中 — 见 [`docs/architecture.md`](docs/architecture.md)

## 文档

- [`docs/architecture.md`](docs/architecture.md) — 架构选型与待定决策(讨论主战场)
- [`docs/data-sources.md`](docs/data-sources.md) — 行情数据源调研(2026 实测横评)
- [`docs/indicators.md`](docs/indicators.md) — 量化指标(估值/支撑压力4算法/技术面)
- [`docs/stock-screening.md`](docs/stock-screening.md) — 选股流水线(漏斗 + 估值打分)
- [`docs/ux-design.md`](docs/ux-design.md) — UX 交互设计(页面架构 + 用户旅程 + 行为对冲)
- [`docs/visualization.md`](docs/visualization.md) — 可视化设计(估值图为主)
- [`docs/ml-prediction.md`](docs/ml-prediction.md) — ML 股价预测(可选,辅助信号)
- [`docs/github-benchmark-and-plan.md`](docs/github-benchmark-and-plan.md) — GitHub 对标 + 最合适方案
- 后续:`docs/ai-prompt.md`(AI prompt 模板,随讨论补充)
