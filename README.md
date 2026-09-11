<div align="center">

# 🏠 HomeHealth · 家庭健康管家

**Snap a lab report → AI extracts health metrics → one place for the whole family's health**
**拍照上传体检报告 · AI 自动提取健康指标 · 全家健康档案一站管理**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00)](https://developer.android.com/topic/architecture)
[![Version](https://img.shields.io/badge/version-1.2.0-blue)](./app/build.gradle.kts)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

[About](#-about--项目简介) · [Pipeline](#-core-pipeline--核心流程) · [Highlights](#-highlights--差异化亮点) · [Features](#-feature-overview--功能全景) · [Tech](#-tech-stack--技术架构) · [Quick Start](#-quick-start--快速开始)

</div>

---

## 📖 About | 项目简介

**English**

HomeHealth is a local-first, privacy-focused family health manager for Android. Snap a photo of a lab report or health checkup sheet, and a **Vision LLM** extracts 49 types of structured health metrics in JSON mode — complete blood count, glucose, lipids, liver & kidney function, vitamins and more. A **Schema Normalization** layer then maps 110+ metric aliases to a standard dictionary, unifies 25 unit spellings, and applies 24 clinically reliable unit conversions before anything reaches the database. Every family member gets an independent health profile with trend analysis, anomaly alerts, record-grounded Q&A, and medication reminders synced with the system calendar. The UI is fully bilingual (English / 中文).

> 🔐 **Local-first, privacy first**: all health data lives in an on-device Room database and is never uploaded anywhere; API keys stay on the device; the app is fully usable without configuring any LLM (a built-in offline Q&A engine covers the basics).

**中文**

HomeHealth 面向多成员家庭，是一款本地优先、隐私至上的 Android 健康管理应用。只需拍照或上传体检报告、化验单，**Vision 大模型**即可通过 JSON 模式自动提取 49 类结构化健康指标（血常规、血糖血脂、肝肾功能、维生素等）。入库前，**Schema 归一化**层会把 110+ 指标别名映射到标准字典、统一 25 种单位写法、执行 24 类医学上可靠的跨单位换算。每位家庭成员拥有独立健康档案，提供趋势分析、异常预警、基于个人记录的健康问答，以及与系统日历联动的用药提醒。界面已全面支持中英双语。

> 🔐 **本地优先，隐私至上**：所有健康数据以 Room 数据库存储在设备本地，不上传任何第三方服务器；API Key 仅保存在本机；不配置任何 LLM 也完全可用（内置离线问答引擎兜底）。

## 🔄 Core Pipeline | 核心流程

**English — from report to insight:**

```
┌─────────┐   ┌──────────────┐   ┌────────────────┐   ┌─────────────┐   ┌────────────┐
│ 📷 Photo │ → │ 🔍 Vision LLM│ → │ 📐 Normalize   │ → │ ✅ Confirm  │ → │ 📊 Insights │
│ upload   │   │ JSON extract │   │ names + units  │   │ editable    │   │ trends     │
└─────────┘   └──────────────┘   └────────────────┘   └─────────────┘   └────────────┘
                     │                    │                                   │
                     │ image downsampling │ 110+ alias mapping                │ trend charts
                     │ (OOM-safe)         │ 25 unit spellings unified         │ rule-based alerts
                     │ dual protocol      │ 24 reliable unit conversions      │ record-grounded Q&A
                     │ (OpenAI/Anthropic) │ (no guessing on unknown units)    │ med reminders
                     └────────────────────┴───────────────────────────────────┴────────────┘
```

1. **Photo upload** — camera or gallery; images are downsampled (max edge 1600px) to prevent OOM on large photos
2. **Vision LLM parsing** — a vision model (e.g., glm-4.6v, qwen-vl) extracts metric name / value / unit / date in JSON mode; text-only models are rejected by runtime validation
3. **Schema normalization** — aliases mapped to the 49-metric standard dictionary; units unified; cross-unit conversions applied only with reliable coefficients — **unknown units are never guessed**
4. **Human confirmation** — results shown as editable cards (with conversion annotations) before saving
5. **Continuous insights** — trend charts, anomaly detection (reference ranges + trends), RAG-style Q&A over personal records, daily medication reminders

**中文 —— 从报告到洞察：**

1. **拍照上传**：拍照或相册选取报告图片，自动降采样（最长边 1600px）防止大图 OOM
2. **Vision LLM 解析**：视觉大模型以 JSON 模式提取指标名/数值/单位/日期，文本模型在此被严格校验拦截
3. **Schema 归一化**：指标别名映射标准字典（49 项指标体系），单位写法统一，跨单位按可靠系数换算——**没有系数的单位绝不猜测**
4. **人工确认**：解析结果以可编辑卡片展示（含换算标注），核对修正后入库
5. **持续洞察**：趋势折线图、异常检测（参考范围+趋势）、基于个人记录的 RAG 问答、每日用药提醒

## 💎 Highlights | 差异化亮点

### 1. 📐 Schema Normalization — metric standardization | 指标标准化归一化

**EN**: Most AI report parsers output whatever name and unit the model feels like: `vitamin D / 25-OH-D / 25(OH)D` all over the place, `nmol/L` vs `ng/mL` incomparable. HomeHealth normalizes before storage: **110+ alias mappings, 25 unit spellings unified, 24 clinically reliable conversion factors** — so trends and alerts are always built on one consistent measurement system. Unknown units are kept as-is and clearly annotated.

**中文**：多数 AI 报告解析工具直接输出「模型认为的」指标名和原始单位，同一指标在不同报告中 `维生素D / 25-羟维生素D / 25(OH)D` 各写各的，`nmol/L` 与 `ng/mL` 数值不可比。HomeHealth 在入库前强制归一化：**110+ 指标别名映射、25 种单位写法统一、24 类跨单位医学换算系数**。未知单位保持原样并明确标注，绝不猜测。

### 2. 👁️ Strict vision/text model separation | 视觉/文本模型严格分离

**EN**: Report parsing must use a **vision model** (image input capable); health Q&A uses a **text model**. The two are configured and validated independently. Picking a text model for parsing is intercepted at runtime with a clear message — instead of a pile of hallucinated data.

**中文**：报告解析必须用**视觉模型**（支持图片输入），健康问答用**文本模型**——两者独立配置、独立校验。误选文本模型解析图片会被运行时拦截并给出明确指引，而不是返回一堆幻觉数据。

### 3. 🔌 7 LLM providers, dual protocol | 7 家 LLM 供应商，双协议适配

**EN**: Zhipu GLM / OpenAI / Google Gemini / DeepSeek / Kimi / Tongyi Qwen / Anthropic Claude. Beyond the OpenAI-compatible protocol, Anthropic's **Messages API** is natively supported (separate system param, image base64 source format). Reasoning content (`reasoning_content` / `thinking`) from thinking models is rendered as a collapsible "reasoning process" block.

**中文**：智谱 GLM / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Anthropic Claude。除 OpenAI 兼容协议外，原生适配 **Anthropic Messages API**（system 独立传参、图片 base64 source 格式）。深度思考模型的 `reasoning_content` / `thinking` 会解析为可折叠的「思考过程」展示。

### 4. 🔐 Truly local-first | 真正的本地优先

**EN**: Health data, Q&A history and API keys live only in the on-device Room database. The LLM receives only the per-request summary when you explicitly trigger parsing or asking — no background data upload whatsoever. Fully usable without a key: an offline rule engine answers basic questions.

**中文**：健康数据、问答历史、API Key 全部仅存于设备 Room 数据库；LLM 只在你主动触发解析/提问时收到**当次请求**所需的摘要，无任何后台数据上报。不配 Key 也完整可用——离线规则引擎兜底问答。

### 5. 📅 Calendar two-way sync | 用药提醒与系统日历联动

**EN**: Reminders can be written into the system calendar with one tap (daily recurring events + 5-minute-ahead notifications). Deleting a reminder also cleans up its calendar events — dual-channel cleanup via stored event IDs plus a signature-based fallback for legacy events. No orphan calendar entries.

**中文**：提醒可一键写入系统日历（每日重复事件 + 提前 5 分钟通知），**删除提醒时日历日程同步清理**（事件 ID 精确删除 + 签名兜底双通道），不产生孤儿日程。

### 6. 🌍 Bilingual UI + theming | 双语界面与模块化主题

**EN**: Full English / 中文 / follow-system language switching via Android per-app locales. Light/dark/system theme modes. Each of the five modules carries its own vibrant accent color that follows you through navigation — family health products can feel youthful too.

**中文**：基于 Android per-app locale 的中英文/跟随系统语言切换；浅色/深色/跟随系统主题模式。五大模块各持独立活力主题色，切换页面时整页色彩随动——家庭健康产品也可以有青春朝气。

## ✨ Feature Overview | 功能全景

| Feature | 功能 |
| --- | --- |
| 👨‍👩‍👧‍👦 Multi-member family profiles · avatars | 多成员家庭档案 · 成员头像 |
| 📸 Vision LLM report parsing · editable results | 视觉大模型报告解析 · 结果可编辑 |
| 📐 Schema normalization (aliases / units / conversions) | 指标归一化（别名 / 单位 / 换算） |
| ✏️ Full record lifecycle · dual-value blood pressure | 记录全生命周期 · 血压双值输入 |
| 📈 Trend charts · latest / avg / high / low stats | 趋势折线图 · 最新/平均/最高/最低统计 |
| 🚨 Rule-based anomaly alerts · severity levels · notifications | 规则引擎异常预警 · 分级 · 系统通知 |
| 💬 Record-grounded Q&A · reasoning display · offline engine | 基于记录的健康问答 · 思考过程 · 离线引擎 |
| 💊 Medication reminders · calendar two-way sync | 用药提醒 · 日历双向联动 |
| 🌍 English / 中文 UI · dark mode · per-module theming | 中英双语界面 · 深色模式 · 模块化主题 |
| 🖼️ Splash screen · adaptive app icon | 启动页 · 自适应应用图标 |

## 🏗️ Tech Stack | 技术架构

**EN**: Kotlin with coroutines & Flow · Jetpack Compose + Material 3 (single Activity + Navigation) · MVVM + Clean Architecture (`ui` / `domain` / `data`) · Hilt DI · Room v4 (progressive migrations) · OkHttp + Gson for LLM calls · WorkManager daily health checks · CalendarProvider integration. Built with AGP 9.4 / Kotlin 2.3 / compileSdk 37.

**中文**：Kotlin 协程 + Flow · Jetpack Compose + Material 3（单 Activity + Navigation）· MVVM + Clean Architecture（`ui` / `domain` / `data` 三层）· Hilt 依赖注入 · Room v4（渐进式迁移）· OkHttp + Gson（LLM 直连）· WorkManager 每日健康检查 · CalendarProvider 日历集成。基于 AGP 9.4 / Kotlin 2.3 / compileSdk 37 构建。

```
┌───────────────────────────────────────────┐
│           Presentation Layer              │
│   Compose UI · ViewModel · Navigation     │
├───────────────────────────────────────────┤
│              Domain Layer                 │
│   Use Cases · Repository 接口 · 领域模型   │
├───────────────────────────────────────────┤
│               Data Layer                  │
│         Repository 实现 · Mapper          │
│  ┌────────────────────┬───────────────┐   │
│  │ Room 本地库 (6 表)  │ LlmClient     │   │
│  │ 6 DAOs             │ (双协议)       │   │
│  └────────────────────┴───────────────┘   │
└───────────────────────────────────────────┘
```

**Key engineering decisions | 关键工程决策**

- `@Upsert` instead of `INSERT OR REPLACE`: avoids REPLACE's DELETE+INSERT semantics triggering FK cascade deletion (a real-world lesson: editing a member once wiped their health records) — 用 `@Upsert` 替代 REPLACE，避免外键级联误删
- Parsing catches `Throwable`, not just `Exception`: large-image OOM becomes a retryable failure state instead of a crash, with automatic recovery of stuck PROCESSING documents — 解析路径捕获 `Throwable`，OOM 转为可重试失败态，并有僵尸状态自动恢复
- LLM calls go through raw OkHttp (streaming compatibility + fine-grained timeouts) — LLM 请求走 OkHttp 原生实现（流式兼容 + 超时精细控制）

## 🤖 LLM Provider Support | LLM 供应商支持

| Provider | 供应商 | Vision parsing | Text Q&A | Protocol |
| --- | --- | :-: | :-: | --- |
| Zhipu GLM | 智谱 GLM | ✅ | ✅ | OpenAI-compatible |
| OpenAI | OpenAI | ✅ | ✅ | OpenAI-compatible |
| Google Gemini | Google Gemini | ✅ | ✅ | OpenAI-compatible |
| DeepSeek | DeepSeek | ✅ (experimental) | ✅ | OpenAI-compatible |
| Kimi (Moonshot) | Kimi 月之暗面 | ✅ | ✅ | OpenAI-compatible |
| Tongyi Qwen (Alibaba) | 通义千问（百炼） | ✅ | ✅ | OpenAI-compatible |
| Anthropic Claude | Anthropic Claude | ✅ | ✅ | Messages API |
| Local mode (no key) | 本地模式（无需 Key） | ➖ | ✅ rule engine | offline |

## 🚀 Quick Start | 快速开始

**EN**

- Android Studio Ladybug+ / JDK 17 / Android SDK 37 (min. Android 8.0 / API 26)

```bash
git clone https://github.com/Ada-Junk/HomeHealth.git
cd HomeHealth

# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

Or open the project in Android Studio and hit Run. APK output: `app/build/outputs/apk/debug/`.

> 💡 **Works out of the box**: family profiles, manual records, trend charts, alerts, medication reminders and offline Q&A all work without any LLM key. Fill in any provider's API key under *Settings → Report Parsing / Health Q&A Service* to unlock photo-based report parsing and AI Q&A.

**中文**

- Android Studio Ladybug 及以上 / JDK 17 / Android SDK 37（最低支持 Android 8.0 / API 26）

```bash
git clone https://github.com/Ada-Junk/HomeHealth.git
cd HomeHealth

# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

或直接用 Android Studio 打开项目，点击 Run。APK 输出在 `app/build/outputs/apk/debug/`。

> 💡 **开箱即用**：不配置任何 LLM Key 也能使用家庭档案、手动记录、趋势图、预警、用药提醒和离线健康问答；在「设置 → 报告解析服务 / 健康问答服务」中填入任意供应商的 API Key 后，即可解锁拍照解析报告和 AI 问答。

## 📁 Project Structure | 项目结构

```
app/src/main/java/com/example/homehealth/
├── data/            # Data layer: Room, DAOs, LLM client, repository impls
│   ├── remote/      #   LlmClient (dual protocol), LlmProviders, offline QA engine
│   ├── local/       #   Room entities & DAOs (6 tables)
│   └── repository/  #   Repository implementations
├── domain/          # Domain layer: models, repository interfaces, use cases
├── di/              # Hilt modules (incl. progressive DB migrations)
├── ui/              # Compose UI: navigation, module themes, 8 screens
├── worker/          # WorkManager background jobs & notifications
└── util/            # SchemaNormalizer, CalendarEventHelper, HealthTypes...
```

Full design doc (Chinese): [docs/开发文档.md](./docs/开发文档.md)

## 🗺️ Roadmap

- [ ] More document types (imaging reports, etc.) — 支持更多文档类型（影像报告等）
- [ ] Health Connect wearable data — 集成 Health Connect 可穿戴设备数据
- [ ] On-device LLM inference, fully offline — 本地 LLM 推理，完全离线运行
- [ ] Multi-device sync (encrypted cloud backup) — 多设备同步（加密云备份）
- [ ] Family data sharing & remote care — 家庭数据共享与远程关怀

## ⚠️ Disclaimer | 免责声明

**EN**: Reference ranges, anomaly alerts and Q&A content provided by this project are for wellness management reference only and do **not constitute medical advice**. Consult a qualified physician for any health concerns.

**中文**：本项目提供的指标参考范围、异常预警和问答内容**仅供健康管理参考，不构成医疗建议**。如有健康问题，请及时咨询专业医生。

## 📄 License

MIT License — see [LICENSE](./LICENSE). 本项目基于 [MIT License](./LICENSE) 开源。
