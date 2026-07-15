# AI Stock Analyst

English version: [README.md](README.md)

AI Stock Analyst 是一个免费、无项目服务器、完全本地运行、BYOK 的美股研究
Android App。它把实时行情、估值与技术指标、本机 LightGBM 概率信号和
Azure OpenAI 解读放在一起，但不把自己包装成买卖推荐工具。

## 核心特点

- **估值优先：**重点看分析师**中位目标价**对应的上行空间和前瞻 PE，而不是把
  “最近跌了”误判成“便宜了”。
- **原生 Android：**APK 使用 Kotlin + Jetpack Compose 开发。
- **TradingView 风格图表：**支持 K 线、成交量柱、平移缩放、十字光标、
  分钟/小时/日/月视图，以及明确标注的 LightGBM 概率线；不打包 TradingView
  的专有代码、品牌或素材。
- **完全本地运行：**抓取行情、缓存、筛选、指标计算和 ML 推理都在 Android
  App 内完成，不需要项目服务器。
- **用户选择数据源：**行情、图表和估值数据源分别配置，选择只保存在本机。
- **BYOK Azure OpenAI：**用户填写 Endpoint、Key、Deployment、API Version，
  Key 在本机加密保存，只发送给用户配置的 Azure Endpoint。
- **仅支持 watchlist：**产品只支持自选列表和研究建议，不提供持仓、成本价、
  分批买卖、交易执行或券商功能。
- **诚实的 ML：**LightGBM 只输出经过校准的概率和不确定性，不伪造确定性的未来
  K 线、未来价格路径，也不输出投资指令。

## 当前状态

项目已经开始实现。仓库现已包含原生 Kotlin/Compose 工程、四个底部页面的应用
骨架、共享设计系统、市场领域模型、可选择的腾讯/新浪行情路由、隔离的 Yahoo
Finance Cookie/Crumb 估值客户端、Room 行情与估值缓存、DataStore 数据源设置，
通过 Hilt 接入的本地数据仓库，以及规范化 `PriceBar`、Room v2 K 线表、去重
DAO、映射与可观察缓存接口。下一步必须先选定可信的美股图表数据源，再继续数据
抓取、周期聚合、指标、图表 UI、筛选和 ONNX 推理。后续实现将按
[架构与决策台账](docs/architecture.md)继续交付。除本文件外，其余技术文档均为英文。

## 文档导航

| 文档 | 用途 |
|---|---|
| [English README](README.md) | 英文产品介绍与导航 |
| [架构与决策](docs/architecture.md) | 系统边界、已锁定决策、开放问题和交付顺序 |
| [数据源说明](docs/data-sources.md) | Android 端直连数据源、接口契约、刷新与回退规则 |
| [分析与 AI](docs/analysis.md) | 估值、选股、3+1 解读流程和本地 LightGBM |
| [产品设计](docs/design.md) | 页面、用户旅程、TradingView 风格图表和状态设计 |
| [设计系统](docs/design-system/ai-stock-analyst/MASTER.md) | 视觉 token、无障碍、颜色、字体和图表语义 |
| [AI Prompt 契约](docs/ai-prompt.md) | Agent 职责、Prompt 不变量和结构化输出 |

## 免责声明

本项目仅用于个人研究和学习。模型输出、分析师目标价和 AI 解读都可能延迟、
缺失或出错，不构成投资建议。
