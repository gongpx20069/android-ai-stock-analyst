# GitHub 量化/AI 金融项目对标总结 + 最合适方案

> 目的:看清"AI 金融 + 机器学习量化"这件事,**GitHub 上大家到底怎么做的**,哪些值得抄、哪些是坑,最后收敛出**最适合本 App 的方案**。
> 方法:2026-06-30 用 GitHub API 拉了各项目真实 star/更新时间,并读了头部项目 README 架构。

---

## 1. 调研对象(按真实 star,2026-06 实拉)

### A. 旗舰量化框架(重武器)
| 项目 | ⭐ | 语言 | 是什么 | 对我们的价值 |
|------|----|----|--------|------------|
| **TauricResearch/TradingAgents** | **90000** | Python | 多智能体 LLM 交易框架(学术论文级,业界标杆) | ⭐ **架构范本** |
| **microsoft/qlib** | 45444 | Python | 微软 AI 量化平台(因子/模型/回测全家桶) | 太重,仅借鉴回测思路 |
| **FinGPT** | 20754 | Python | 开源金融大模型 | 偏研究,暂不用 |
| **stefan-jansen/ML-for-trading** | 19452 | Notebook | 《机器学习量化交易》全书代码 | ⭐ ML 特征工程教科书 |
| **FinRL** | 15559 | Notebook | 金融强化学习 | 强化学习太难落地,跳过 |
| **OpenBB** | 69878 | Python | 金融数据/分析平台(给分析师和AI agent用) | 数据层可参考 |

### B. LLM 多智能体投研(2026 最热方向)
| 项目 | ⭐ | 亮点 | 对我们的价值 |
|------|----|------|------------|
| **TradingAgents-AShare** | 629 | TradingAgents 的**A股本地化+Web UI**:自选股、定时分析、持仓追踪、BYOK多厂商 | ⭐⭐ **几乎就是我们想做的形态** |
| **FinMem-LLM-StockTrading** | 916 | 带分层记忆的 LLM 交易 agent | 记忆机制可借鉴 |
| **renee-jia/trading-bot** | 105 | 多因子打分 + 宏观多agent | 打分思路参考 |
| **1517005260/stock-agent** | 100 | 基于 LLM 的股票投资助手 | 轻量参考 |

### C. ML/深度学习预测模型库
| 项目 | ⭐ | 内容 | 现实评价 |
|------|----|------|---------|
| **huseinzol05/Stock-Prediction-Models** | 9408 | **30个深度模型(LSTM/GRU/Transformer)+23个RL agent+蒙特卡洛** | ⭐ 模型大全,但全是研究notebook |
| robertmartin8/MachineLearningStocks | 1953 | scikit-learn 基本面选股预测 | ⭐ 轻量可抄 |
| scorpionhiccup/StockPricePrediction | 1533 | ML 价格预测 | 教学向 |

---

## 2. 关键发现:大家其实分两条技术路线

### 🔵 路线一:LLM 多智能体(2026 主流、最火)
**代表:TradingAgents(90k⭐)。** 核心打法 = **把投研机构的分工搬给一群 LLM agent**:

```
分析师团队(并行)         研究员辩论          决策层
├─ 基本面分析师    ┐
├─ 技术面分析师    ├──→  多头 vs 空头  ──→  交易员 ──→ 风控三方 ──→ 组合经理
├─ 新闻分析师      │      (结构化辩论)                  (激进/稳健/中性)
└─ 情绪分析师      ┘
```
- **AShare 版**把它做成了产品:Web UI + 自选股 + 定时分析 + 持仓追踪 + **BYOK 多厂商(OpenAI/DeepSeek/Gemini/智谱…)** + REST API。**这几乎就是你想要的 App 形态**(只是它是 Web+A股,你要 Android+美股)。
- ✅ 优点:可解释(每个 agent 给理由)、贴合"基本面+估值"分析、天然适配你的 BYOK Azure key。
- ⚠️ 成本:一次分析要调 N 次 LLM(十几个 agent),**token 花得多**;你自己的 key 自己承担。

### 🟢 路线二:传统 ML 预测(LSTM/XGBoost/RL)
**代表:huseinzol05(9.4k⭐)收集了 30+ 深度模型。** 但**最重要的诚实发现**:
- 这些**几乎全是研究型 notebook**,回测好看,**实盘极少有人敢用**。
- huseinzol05 本人也只是"gather models",不是生产系统。
- 业界共识:**深度学习(LSTM)在单股日线上不比简单模型强多少,还难调难懂**。
- 连 90k 星的 TradingAgents 都白纸黑字写:"**designed for research purposes... not intended as financial advice**"(仅研究用途,非投资建议)。

---

## 3. 对标后的三个硬结论

1. **不要重造轮子**:qlib/FinRL/FinGPT 是重武器,不适合一个手机自用 App。**抄架构思想,别搬代码**。

2. **LLM 多智能体 > 纯ML预测**(对你这个场景):
   - 你的需求是"**按我的估值纪律解读个股**",不是"高频量化交易"。
   - LLM agent **可解释、贴基本面、天然吃 BYOK key**,正好配你的 Azure key。
   - 纯 ML 价格预测**可解释性差、实盘不可靠**,只能当辅助小信号。

3. **TradingAgents-AShare 是最好的参照物**:它已经验证了"BYOK 多智能体 + 自选股 + 定时分析 + 持仓追踪"这套产品形态可行——**你要做的约等于"它的 Android 美股版,且更轻"**。

---

## 4. ⭐ 最合适方案:轻量多智能体 + ML 辅助信号

给你一个**砍到适合手机自用、又吸收了头部项目精华**的方案:

### 4.1 AI 层:精简版多智能体(抄 TradingAgents,但砍到 3-4 个 agent)
TradingAgents 的 15+ agent 对自用太重、太烧 token。**砍成 3 个核心 agent + 1 个裁决**:

```
[你的Azure key 驱动]
┌─────────────────────────────────────────────┐
│  ① 基本面/估值 Agent ── 吃 yfinance 数据 +    │
│     你的纪律(上行空间%/前瞻PE/评级)         │
│  ② 技术面 Agent ──── 吃 MA/RSI/52周位置       │
│  ③ 风险/反方 Agent ── 专门唱反调,防止过度乐观 │
│         ↓ 三方意见                            │
│  ④ 裁决 Agent ─── 综合给:方向/置信度/        │
│     目标价区间/核心风险/一句话结论             │
└─────────────────────────────────────────────┘
```
- **为什么砍**:自用场景,3-4 个 agent 已能覆盖"基本面+技术+风险+裁决",token 成本可控(一次分析约 4-6 次 LLM 调用,而非十几次)。
- **可解释**:每个 agent 输出理由,你能看到"为什么看多/看空"。
- **内嵌你的纪律**:① 号 agent 的 prompt 直接写死"看上行空间+低PE、在跌≠便宜、三大卖出信号"(见 ai-prompt.md)。
- **借鉴 AShare**:输出结构化决策卡(方向/置信度/目标价/止损/风险),而非一段散文。

### 4.2 ML 层:轻量辅助信号(抄 robertmartin8 的克制,不抄 huseinzol05 的炫技)
- **只做 T2 LightGBM 涨跌方向概率**(见 ml-prediction.md),**不做 LSTM/RL**。
- ML 结果**只作为 ③风险agent 和 ④裁决agent 的一个输入特征**("模型给的5天上涨概率 0.58"),**不单独下结论**。
- 这样 ML 既参与了,又被关在"辅助信号"的笼子里,不会误导你。

### 4.3 数据层 & 可视化层(沿用既有文档,不重复)
- 数据层:方案③(实时价→腾讯/新浪,基本面/分析师→yfinance 后端缓存),见 [`data-sources.md`](data-sources.md)。AI/ML 都从这层取数。
- 可视化层:估值图为主 + AI 决策卡 + ML 概率/回测图,见 [`visualization.md`](visualization.md) 与 [`ml-prediction.md §3`](ml-prediction.md)。

---

## 5. 架构全景(把所有 docs 串起来)

```
┌────────────── Flutter Android App ──────────────┐
│  自选监控页 │ 个股详情页 │ 筛选页 │ AI解读页      │
│   (估值图)   (K线+决策卡) (排序)  (多agent报告)   │
└───────┬──────────────────────────────┬──────────┘
        │ 实时价(直连)                  │ 分析请求
        ▼                              ▼
   腾讯/新浪API              ┌─── 你的 Ubuntu 薄后端 ───┐
   (国内快/免key)            │ FastAPI                  │
                            │ ├ yfinance 缓存(基本面)   │
                            │ ├ LightGBM(涨跌概率)      │
                            │ └ 多智能体编排            │
                            │     ↑ 调你的 Azure key    │
                            └──────────────────────────┘
```

---

## 6. 借鉴清单(谁的什么,拿来用)

| 来源项目 | 借鉴什么 | 怎么用 |
|---------|---------|--------|
| TradingAgents | 多智能体分工 + 辩论架构 | 砍成 3+1 精简版 |
| TradingAgents-AShare | 产品形态:自选股/定时/持仓/BYOK/决策卡 | 直接对标,做 Android 美股版 |
| FinMem | 分层记忆 | 二期给 agent 加"历史判断记忆" |
| robertmartin8 | scikit-learn 克制的选股预测 | ML 层照此轻量化 |
| stefan-jansen | ML 特征工程方法 | LightGBM 特征设计参考 |
| huseinzol05 | LSTM/RL 长啥样 | **当反面教材**:知道但不用 |

