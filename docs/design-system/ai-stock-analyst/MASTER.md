# Design System Master File — AI Stock Analyst

> **LOGIC:** 构建某个具体页面时,先查 `design-system/pages/[page-name].md`。
> 若该文件存在,其规则**覆盖**本 Master;否则严格遵循下面的规则。
>
> **来源:** 由 `ui-ux-craft-kit` skill 数据库检索落地(Fintech/Crypto 品类 + Modern Dark 风格),
> 并按本项目"估值优先 / 反直觉红绿 / 行为对冲"立场做了语义层扩展。所有文本色都过了 WCAG 实测。

---

**Project:** AI Stock Analyst(美股估值分析 Android App,Flutter/Dart)
**Category:** Fintech/Crypto
**Style:** Modern Dark (Cinema Mobile)
**Platform:** Mobile-first(单手竖屏为主)

---

## 1. 颜色调色板(Fintech/Crypto·暗色)

> 直接取自 skill 的 Fintech/Crypto 配色方案(“Gold trust + purple tech”)。金色=信任/CTA,紫色=科技/AI。

| 角色 | Hex | Flutter/Token | 说明 |
|------|-----|---------------|------|
| Background 页面底 | `#0F172A` | `--bg` | 深蓝黑,避免纯 `#000000`(OLED 拖影) |
| Card 卡片 | `#222735` | `--card` | 信息最密处,所有文本色都按它实测对比度 |
| Muted 次级面 | `#272F42` | `--muted` | 折叠区块/次级背景 |
| Foreground 前景 | `#F8FAFC` | `--fg` | 主文本,14.24:1(AAA) |
| Muted-fg 弱化 | `#94A3B8` | `--muted-fg` | **次要价格/时间戳**,5.81:1(AA) |
| Border 描边 | `#334155` | `--border` | 分隔线/输入框 |
| Primary/CTA 金 | `#F59E0B` | `--primary` | 主按钮/关键操作,On=`#0F172A` |
| Secondary 亮金 | `#FBBF24` | `--secondary` | 次级强调,On=`#0F172A` |
| Accent/AI 紫 | `#8B5CF6` | `--accent` | AI/多智能体标识块,On=`#FFFFFF` |
| Destructive 红 | `#EF4444` | `--destructive` | 危险操作/删除,On=`#FFFFFF` |
| Ring 焦点环 | `#F59E0B` | `--ring` | 输入聚焦/键盘焦点 |

## 2. 语义层:反直觉估值配色(本项目灵魂)

> **红 ≠ 跌、绿 ≠ 涨。** 颜色对齐**估值**,不对齐价格方向 —— 对冲用户“跌了就想买/追涨”的本能。
> ⚠️ 铁律(来自 skill UX「Color Only」High):颜色**永不单独承载含义**,必须同时配**图标 + 文字标签**(↑↓、"有空间/偏贵"),兼顾色盲。

| 语义 | 文本色(对卡片) | 块/边框 | 图标+文字(必带) | 触发 |
|------|----------------|---------|-------------------|------|
| **有上行空间(便宜)** | `#4ADE80` 8.55:1 AAA | `#22C55E` | ▲ + “上行空间 XX%” | 中位目标价 ÷ 现价 高 |
| **偏贵/追高(危险)** | `#F87171` 5.39:1 AA | `#EF4444` | ▼ + “已接近/超目标价” | 现价 ≥ 目标价 |
| **接近目标(中性偏警惕)** | `#FBBF24` 8.92:1 AAA | `#FBBF24` | ● + “接近合理” | 上行空间收窄 |
| **AI / 模型信号** | `#A78BFA` 5.47:1 AA | `#8B5CF6` | ✦ + “AI/概率” | 多智能体、ML 概率 |

## 3. 字体(Dashboard Data 搭配)

- **数字/数据 = Fira Code(等宽)**:价格、PE、涨跌幅、目标价 —— 等宽让数字**竖向对齐**、抖动不跳位。
- **标签/正文 = Fira Sans**:界面文案、解读文字。
- Mood:dashboard / data / analytics / precise。

```css
@import url('https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500;600;700&family=Fira+Sans:wght@300;400;500;600;700&display=swap');
```
```dart
// Flutter
fontFamilyData: 'Fira Code'  // 价格、百分比、PE、目标价
fontFamilyText: 'Fira Sans'  // 标签、解读、按钮
```

### 字号阶梯(modular scale,来自 skill UX「Font Size Scale」)

`12 · 14 · 16 · 18 · 24 · 32`(sp)。禁止随意中间值。正文/输入 ≥16sp(防 iOS/安卓聚焦缩放)。

## 4. 间距 / 圆角 / 阴影

| Token | 值 | 用途 |
|-------|----|----|
| `--space-xs` | 4px | 紧凑间隙 |
| `--space-sm` | 8px | **触控目标最小间距**(见 §6) |
| `--space-md` | 16px | 标准内边距 |
| `--space-lg` | 24px | 区块内边距 |
| `--space-xl` | 32px | 大间隙 |
| radius | 12–16px | 卡片 12,弹窗 16 |
| shadow-md | `0 4px 6px rgba(0,0,0,.4)` | 卡片(暗色下加深) |
| shadow-lg | `0 10px 15px rgba(0,0,0,.5)` | 弹窗/下拉 |

## 5. 图表配色规则(fl_chart)

> 图表方向≠估值,**必须与 §2 语义解耦**,避免“K线红=跌”与“红=贵”打架。

| 图表 | 选型(skill) | 配色规则 | A11y 回退 |
|------|-------------|---------|-----------|
| **K线 OHLC** | Candlestick | **中性单色 + 实心/空心区分涨跌**(`#CBD5E1` 描边 10:1),不用红绿 → 把红绿留给估值 | OHLC 数据表 + 当日涨跌% |
| **现价 vs 目标价** | Bullet / Gauge | 质量区间**便宜(绿)→合理(黄)→贵(红)**,业绩条金 `#F59E0B`,目标 marker `#F8FAFC` | 数值+“距目标 XX%”文字常显 |
| **ML 涨跌概率** | Gauge | **中性轨道 + 紫/金弧**(不用估值红绿),概率数字常显 | 数值 + % 文字 + ARIA live |
| **ML 预测带** | Line + 置信带 | 实际实线 `#CBD5E1`,预测虚线紫 `#A78BFA`,置信带 15% 透明 | actual/forecast 可独立开关,图例带线型描述 |
| **选股排名/对比** | Bar(横向,降序) | 每条按上行空间着 §2 语义色,数值标签常显 | 数值标签常显 + CSV |

## 6. 移动端 UX 铁律(来自 skill UX 数据库,标注严重度)

| 规则 | 值 | 严重度 |
|------|----|-------|
| **触控目标** | ≥44px;本项目统一 **48dp**(同时满足 WCAG 44 与 Material 48) | High |
| **触控间距** | 相邻可点元素 ≥8px | Medium |
| **手势** | 主内容**竖向滚动优先**;不用横滑切主 Tab、不覆盖系统手势 | Medium |
| **移动键盘** | 数字用 `inputmode=numeric`,价格阈值用 `decimal` | Medium |
| **确认对话框** | 删自选股 / 清 API Key 等不可逆操作必须二次确认 | High |
| **颜色对比** | 正文 ≥4.5:1(已全实测) | High |
| **不只靠颜色** | 红绿估值必配 ↑↓ 图标 + 文字 | High |
| **减少动效** | 尊重 `prefers-reduced-motion` / 系统“减弱动态效果” | High |
| **加载态** | 骨架屏 / spinner,禁止界面冻结无反馈 | High |
| **空状态** | 空自选股 → 引导文案 + “添加”按钮 | Medium |
| **实时校验** | API 配置表单 onBlur 校验,不只在提交时 | Medium |
| **禁用态** | opacity 50% + 明确非可点 | Medium |
| **当前位置** | 底部 Tab 高亮当前项(色+图标填充) | Medium |

## 7. 风格与动效(Modern Dark / Cinema Mobile)

- Glassmorphism 顶栏/底栏(BlurView intensity ~20);卡片 frosted。
- Expo.out 缓动 `cubic-bezier(0.16,1,0.3,1)`;弹窗 spring(damping 20 / stiffness 90)。
- 按压 scale `0.97→1.0` + Haptic(Impact Light/Medium)。
- 避免纯 `#000000`(OLED 拖影);避免 AI 紫粉渐变滥用。

## 8. Anti-Patterns(禁止)

- ❌ 红=跌绿=涨的传统股市配色(与本项目语义冲突)
- ❌ 颜色单独承载信息(必配图标+文字)
- ❌ Emoji 当图标(用 SVG:Lucide/Heroicons)
- ❌ 低于 4.5:1 的正文对比
- ❌ 横滑切主 Tab / 覆盖系统返回手势
- ❌ 无加载态、无空状态、无删除确认
- ❌ AI 紫粉渐变滥用、纯黑背景

## 9. 交付前自检

- [ ] 所有文本对卡片 ≥4.5:1(数字色已实测)
- [ ] 红绿估值都带 ↑↓ 图标 + 文字标签
- [ ] 触控目标 ≥48dp,间距 ≥8px
- [ ] 数字用 Fira Code 等宽对齐
- [ ] 删除/清 Key 有二次确认弹窗
- [ ] 加载=骨架屏,空=引导态,禁用=opacity50
- [ ] 尊重 prefers-reduced-motion
- [ ] 底部 Tab 高亮当前页
- [ ] 图表红绿与估值语义不打架(K线走中性/实空心)
