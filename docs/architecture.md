# 架构设计与决策台账

> 本文是本项目的**决策中枢**,只做三件事:①讲清核心矛盾 ②汇总**全项目所有决策**(已定 / 待拍板)③索引各子文档。
> **单一出处原则**:任何子文档产生的"待拍板"问题,一律登记到本文 §3 决策台账,子文档内不再各自散落决策清单。

---

## 1. 一句话目标

安卓 App:输入股票代码 → 拉行情 + 算量化指标 → 调**用户自己的 Azure OpenAI** 做解读 → 输出一份"按我的估值纪律"写的分析报告。(项目定位见 [`../README.md`](../README.md))

---

## 2. 核心矛盾:数据从哪来(驱动全盘技术选型)

手机端做股票分析,最大难点**不是 AI,而是数据**:

- yfinance 是 Python 库,**安卓上不能直接跑**;
- 2026 年免费行情 API 的"分析师目标价"几乎全转付费,**只有 yfinance 免费给全**(实测验证);
- 腾讯/新浪 API 国内直连稳、免 key,但**只有价格,无目标价/前瞻PE**。

→ 结论:**技术栈 + 数据源是一对绑定决策**,且"分析师目标价"这一核心指标决定了必须有一层 yfinance 后端。详见 [`data-sources.md`](data-sources.md)。

---

## 3. 决策台账(全项目唯一决策出处)

### 3.1 已定 ✅

| 编号 | 决策点 | 结论 | 详见 |
|------|--------|------|------|
| **A** | 技术栈 | **Flutter / Dart**。理由:复用 mochi-pet 打包/签名经验;本 App 重 UI(卡片/估值表/K线/AI排版),`fl_chart` 顺手;一套代码可扩 iOS。排除 Java、原生 Kotlin、Python 套壳、PWA。 | 本文 |
| **U2** | 配色语义(灵魂) | **反直觉**:🔴红=贵/追高(非"跌")、🟢绿=有上行空间(非"涨");红绿**必配 ↑↓ 图标+文字标签**。对冲"跌了就想买/追涨"本能。 | [MASTER §2](design-system/ai-stock-analyst/MASTER.md) |
| **I1** | 支撑压力算法 | **4 种全做**(枢轴点/波段高低/斐波那契/均线),**波段+均线为主轴**(贴中线风格),枢轴/斐波作"更多"展开。纯 numpy,已用 NVDA 实测。 | [indicators §2](indicators.md) |
| **I2** | 支撑压力呈现 | 醒目数字卡"距最近支撑 −X% / 距最近压力 +Y%",与上行空间%、前瞻PE 并列。 | [indicators](indicators.md) |
| **I3** | 共振高亮 | 被 ≥2 种算法命中的价位加粗标可靠度。 | [indicators](indicators.md) |
| **I4** | 技术面 MVP | MA50/200 + RSI(14) + 52周位置;MACD/布林/ATR 放二期。 | [indicators §3](indicators.md) |

### 3.2 待拍板 ❓

| 编号 | 决策点 | 选项 / 建议 | 详见 |
|------|--------|------------|------|
| **B** | 数据源方案 | **③国内价+yfinance基本面(⭐荐)** / ①纯yfinance后端 / ②纯直连Finnhub(缺目标价,不荐) | [data-sources §6](data-sources.md) |
| **C** | 量化指标 MVP 增减 | 现集=估值+支撑压力+技术+盈亏;是否加资金流/情绪/板块对比 | [indicators](indicators.md) |
| **D** | AI 厂商兼容 | Azure 之外要不要也兼容普通 OpenAI / DeepSeek | 本文 |
| **E** | 数据/安全边界 | key/自选/成本全本地存不上云(已倾向);开源协议 GPL-3.0 vs MIT 待定 | 本文 |
| **S1** | 选股入口 | MVP 先做**自选监控(A,⭐荐)** vs 直接上全市场筛选(B) | [stock-screening §2](stock-screening.md) |
| **S2** | 选股打分权重 | 上行40 / PE30 / 置信10 / 评级10 / 位置10,是否调 | [stock-screening §1](stock-screening.md) |
| **S3** | 硬过滤门槛 | 市值≥$10B / 价≥$5 / 量≥500K;要不要行业白/黑名单 | [stock-screening §1](stock-screening.md) |
| **S4** | 防追高硬规则 | 现价>52周高95% 是否直接标红/降权 | [stock-screening](stock-screening.md) |
| **Q1** | AI 层架构 | **精简多智能体 3+1(⭐荐)** vs 单次AI解读(1 agent) | [github-benchmark §4](github-benchmark-and-plan.md) |
| **Q3** | 定时分析+持仓追踪 | 是否把现有每日 WeChat 监控整合进 App | [github-benchmark](github-benchmark-and-plan.md) |
| **Q4** | 省钱模式 | AI 页"只跑 1 个 agent"省 token 开关 | [github-benchmark](github-benchmark-and-plan.md) |
| **M1** | MVP 是否带 ML | 带 vs ML 放二期(先做好估值分析) | [ml-prediction §6](ml-prediction.md) |
| **M2** | ML 起步层 | T1时间序列(预测带) / **T2树模型LightGBM(⭐荐,涨跌概率)** | [ml-prediction §1](ml-prediction.md) |
| **M3** | ML 预测窗口 | 未来 5天 / 20天 / 都给 | [ml-prediction](ml-prediction.md) |
| **U1** | 底部 4 Tab 结构 | 监控/筛选/AI/我的,认可还是想更少 | [ux-design §1](ux-design.md) |
| **U3** | 行为对冲默认开 | 追高预警/小亏不焦虑/卖出信号自检,默认开启 | [ux-design §7](ux-design.md) |
| **U4** | 详情页折叠 | 估值/支撑压力默认展开,其余收起 | [ux-design §3](ux-design.md) |
| **U5** | AI 页展示方式 | 逐 agent 流式(更可信稍慢) vs 只出最终决策卡 | [ux-design §3](ux-design.md) |
| **V1** | 可视化 MVP 范围 | 只做 P0 估值图(上行空间/区间/前瞻PE)+盈亏,K线二期 | [visualization §2](visualization.md) |

> **已在子文档中给出定位、默认采纳(如无异议即执行)**:ML 只做**辅助信号不单独下结论**、深度学习 T3 放二期、把"上行空间%/前瞻PE"作为 ML 特征(见 ml-prediction.md);图表库 fl_chart 为主 + K线用 candlesticks/syncfusion(见 visualization.md)。

---

## 4. 关联文档索引

| 文档 | 内容 | 状态 |
|------|------|------|
| [`data-sources.md`](data-sources.md) | 行情数据源调研(2026 实测横评) | ✅ 调研完成 |
| [`indicators.md`](indicators.md) | 量化指标(估值/支撑压力4算法/技术面) | ✅ 草案+实测,部分已定 |
| [`stock-screening.md`](stock-screening.md) | 选股流水线(漏斗+估值打分) | ✅ 草案 |
| [`ux-design.md`](ux-design.md) | UX 交互设计(页面/旅程/行为对冲) | ✅ 草案 |
| [`design-system/ai-stock-analyst/MASTER.md`](design-system/ai-stock-analyst/MASTER.md) | 设计系统底座(配色/字体/间距/触控/图表规格) | ✅ 已定 |
| [`visualization.md`](visualization.md) | 可视化设计(估值图为主) | ✅ 草案 |
| [`ml-prediction.md`](ml-prediction.md) | ML 股价预测(可选,辅助信号) | ✅ 草案 |
| [`github-benchmark-and-plan.md`](github-benchmark-and-plan.md) | GitHub 对标 + 最合适方案 | ✅ 调研完成 |
| `ai-prompt.md` | AI prompt 模板(内嵌估值纪律) | ⏳ 待写 |

---

## 5. 推进顺序

1. 定 §3 的 **B 数据源**(其它都依赖它,技术栈 A 已定)
2. 写死 demo:脚本拉 1 只股票全字段 → 验证数据通路
3. 接 Azure OpenAI,跑通"数据 → AI 报告"
4. 包 UI(Flutter 4+1 页 + MASTER 设计 token)
5. 打包 APK / 部署薄后端
