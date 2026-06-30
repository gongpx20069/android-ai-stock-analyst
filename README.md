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
- 后续:`docs/data-sources.md`、`docs/ai-prompt.md`、`docs/mvp-scope.md`(随讨论补充)
