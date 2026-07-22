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
- **中英文界面：**默认跟随 Android 系统语言，也可在应用内固定为 English 或
  简体中文。
- **更新与反馈：**启动或手动检查本仓库最新 GitHub Release，并提供已签名 APK、
  GitHub Issue 和维护者邮箱入口。
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
通过 Hilt 接入的本地数据仓库，以及规范化 `PriceBar`、按来源隔离的 Room K 线
表、DAO、映射与可观察缓存接口。默认图表源现为无需账号的东方财富实验接口，
具备严格美股代码解析、未复权 K 线、短历史能力错误，以及非官方接口和数据许可
提示。Alpaca Basic 仍可作为加密 BYOK 图表源，并明确标注实时 IEX 单一交易所、
不是全美综合市场数据。应用会将 Alpaca 1 分钟 K 线本地聚合为 5 分钟 K 线；
东方财富保留原生 5 分钟 K 线，并按美股常规交易时段聚合已完成的 4 小时 K 线。
同时基于有效
且已完成的日线本地计算 MA50、MA200 与 Wilder RSI(14)，通过仓库暴露带新鲜度
信息的指标快照。相同历史数据现已支持 52 周位置，以及枢轴、摆动点、斐波那契、
移动平均线和 52 周高低位构成的支撑/阻力与跨方法共振。Watchlist 页面现已
在选中股票后仍保留顶部代码输入和交易所控件，并将图表放在行情、估值、
支撑/阻力和技术指标卡片之前。Vico 图表使用中性蜡烛样式和独立缩放的成交量柱，
时间按设备本地时区显示，支持平移、缩放、回到最新数据，并按实际来源显示
东方财富或 Alpaca 提示。自选列表持久化、筛选、概率线叠加和 ONNX 推理仍待
实现。界面现支持中英文并默认跟随系统，也可在“我的”中切换；同一页面提供
GitHub Release 更新检查、APK 下载、Issue 和邮件反馈入口。后续实现将按
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
| [APK 发布](docs/releasing.md) | 统一签名、自动 `1.0.x` 版本、本地发布和手动 Actions 发布 |

## 免责声明

本项目仅用于个人研究和学习。模型输出、分析师目标价和 AI 解读都可能延迟、
缺失或出错，不构成投资建议。
