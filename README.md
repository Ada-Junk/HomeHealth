<div align="center">

# 🏠 HomeHealth 家庭健康管家

**拍照上传体检报告 · AI 自动提取健康指标 · 全家健康档案一站管理**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-FF6F00)](https://developer.android.com/topic/architecture)
[![Version](https://img.shields.io/badge/version-1.1.0-blue)](./app/build.gradle.kts)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

[核心流程](#-核心流程) · [差异化亮点](#-差异化亮点) · [技术栈](#-技术架构) · [快速开始](#-快速开始)

</div>

---

## 📖 项目简介

HomeHealth 面向多成员家庭，帮助用户集中管理全家人的健康数据。只需拍照或上传体检报告、化验单，应用即可借助 **Vision 大模型**自动解析出结构化健康指标（血常规、血糖血脂、肝肾功能、维生素等 49 项），经 **Schema 归一化**统一指标名与单位后，建立每位家庭成员的独立健康档案，并基于历史数据提供**趋势分析、异常预警、健康问答与用药提醒**。

> 🔐 **本地优先，隐私至上**：所有健康数据以 Room 数据库存储在设备本地，不上传任何第三方服务器；API Key 仅保存在本机；不配置任何 LLM 也完全可用（内置离线问答引擎）。


## 🔄 核心流程

**报告 → 指标 → 洞察** 的完整数据流水线：

```
┌─────────┐   ┌──────────────┐   ┌───────────────┐   ┌──────────────┐   ┌────────────┐
│ 📷 拍照   │ → │ 🔍 Vision LLM │ → │ 📐 Schema 归一化│ → │ ✅ 人工确认    │ → │ 📊 持续洞察  │
│ 上传报告  │   │ JSON 结构化提取│   │ 指标名+单位统一  │   │ 可编辑后入库   │   │            │
└─────────┘   └──────────────┘   └───────────────┘   └──────────────┘   └────────────┘
                  │                    │                                     │
                  │  · 图片降采样防 OOM   │  · 110+ 别名映射（FPG→空腹血糖）          │  · 📈 趋势折线图
                  │  · 双协议适配        │  · 25 种单位写法归一（umol/L→μmol/L）     │  · 🚨 规则引擎异常预警
                  │    (OpenAI 兼容 /    │  · 24 类跨单位可靠换算                    │  · 💬 RAG 健康问答
                  │     Anthropic)     │    （维生素D nmol/L→ng/mL 等）            │  · 💊 用药提醒+日历联动
                  └────────────────────┴─────────────────────────────────────────┘
```

1. **拍照上传**：拍照或相册选取报告图片，自动降采样（最长边 1600px）防止大图 OOM
2. **Vision LLM 解析**：视觉大模型（如 glm-4.6v、qwen-vl）以 JSON 模式提取指标名/数值/单位/日期，文本模型在此被严格校验拦截
3. **Schema 归一化**：指标别名映射标准字典（49 项指标体系），单位写法统一，跨单位按可靠系数换算——**没有系数的单位绝不猜测**
4. **人工确认**：解析结果以可编辑卡片展示（含换算标注），核对修正后入库
5. **持续洞察**：趋势折线图、异常检测（参考范围+趋势）、基于个人记录的 RAG 问答、每日用药提醒

---

## 💎 差异化亮点

与常见「手抄数据」类健康管理 App 相比，HomeHealth 的核心竞争力：

### 1. 📐 Schema Normalization——指标标准化归一化
多数 AI 报告解析工具直接输出「模型认为的」指标名和原始单位，同一指标在不同报告中 `维生素D / 25-羟维生素D / 25(OH)D` 各写各的，`nmol/L` 与 `ng/mL` 数值不可比。HomeHealth 在入库前强制归一化：**110+ 指标别名映射标准字典、25 种单位写法统一、24 类跨单位医学换算系数**，保证趋势分析与异常检测建立在同一度量体系上。未知单位保持原样并明确标注，绝不猜测。

### 2. 👁️ 视觉/文本模型严格分离
报告解析必须用**视觉模型**（支持图片输入），健康问答用**文本模型**——两者独立配置、独立校验。误选文本模型解析图片会被运行时拦截并给出明确指引，而不是返回一堆幻觉数据。

### 3. 🔌 8 家 LLM 供应商 + 双协议适配
智谱 GLM / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Anthropic Claude / 任意 OpenAI 兼容端点（Ollama、vLLM…）。除 OpenAI 兼容协议外，原生适配 **Anthropic Messages API**（system 独立传参、图片 base64 source 格式）。深度思考模型的 `reasoning_content` / `thinking` 会被解析为可折叠的「思考过程」展示。

### 4. 🔐 真正的本地优先
健康数据、问答历史、API Key 全部仅存于设备 Room 数据库；LLM 只在你主动触发解析/提问时收到**当次请求**所需的摘要，无任何后台数据上报。不配 Key 也完整可用——离线规则引擎兜底问答。

### 5. 📅 用药提醒与系统日历联动
提醒可一键写入系统日历（每日重复事件 + 提前 5 分钟通知），**删除提醒时日历日程同步清理**（事件 ID 精确删除 + 签名兜底双通道），不产生孤儿日程。

### 6. 🎨 模块化主题设计
五大模块各持独立活力主题色，切换页面时整页色彩随动，底部导航常显分色——家庭健康产品也可以有青春朝气。

---

## ✨ 功能全景

| 功能 | 说明 |
| --- | --- |
| 👨‍👩‍👧‍👦 多成员家庭档案 | 独立档案 · 关系标注（15 种）· 身高体重 · 年龄自动计算 |
| 📸 智能报告解析 | Vision LLM JSON 模式提取 · 解析结果可编辑 · 失败可重试 |
| 📐 指标归一化 | 别名映射 · 单位统一 · 跨单位换算（含换算标注） |
| ✏️ 记录全生命周期 | 手动添加 / 编辑 / 删除 · 血压双值输入 |
| 📈 历史趋势分析 | 指标趋势折线图 · 最新/平均/最高/最低统计 |
| 🚨 主动异常预警 | 规则引擎（参考范围+趋势）· 分级预警 · 系统通知 |
| 💬 健康问答 | RAG（基于个人记录）· 深度思考过程展示 · 离线规则引擎 |
| 💊 用药提醒 | 每日多时间点 · 启停开关 · 系统日历双向联动 |
| 🔌 多 LLM 供应商 | 8 家供应商 · 视觉/文本分离配置 · 双协议 |
| 🌓 体验细节 | 深色模式 · 五模块主题色 · 输入法自适应 |

## 🏗️ 技术架构

- **语言**：Kotlin · 协程 + Flow
- **UI**：Jetpack Compose + Material 3 · 单 Activity + Navigation · 动态模块主题
- **架构**：MVVM + Clean Architecture（`ui` / `domain` / `data` 三层）
- **依赖注入**：Hilt
- **本地存储**：Room v4（渐进式迁移）· DataStore/SharedPreferences
- **网络**：Retrofit + OkHttp + Gson（LLM 直连 / 自建后端双通道）
- **后台**：WorkManager 每日健康检查 + 通知推送
- **系统集成**：CalendarProvider（日历读写）· 相机/相册

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
│  ┌────────────┬────────────┬───────────┐  │
│  │ Room 本地库 │ LlmClient  │ 自建后端   │  │
│  │ (6 表/6 DAO)│ (双协议)   │ (Retrofit)│  │
│  └────────────┴────────────┴───────────┘  │
└───────────────────────────────────────────┘
```

**关键工程决策**

- `@Upsert` 替代 `INSERT OR REPLACE`：避免 REPLACE 的 DELETE+INSERT 语义触发外键级联误删（成员编辑曾导致健康记录丢失的真实教训）
- 解析路径捕获 `Throwable` 而非 `Exception`：大图 OOM 转为可重试的失败态而非闪退，并有僵尸 PROCESSING 状态自动恢复
- LLM 请求走 OkHttp 原生实现（流式兼容 + 超时精细控制），Retrofit 仅用于自建后端

## 🤖 LLM 供应商支持

| 供应商 | 视觉解析 | 文本问答 | 协议 |
| --- | :-: | :-: | --- |
| 智谱 GLM | ✅ | ✅ | OpenAI 兼容 |
| OpenAI | ✅ | ✅ | OpenAI 兼容 |
| Google Gemini | ✅ | ✅ | OpenAI 兼容 |
| DeepSeek | ✅（实验） | ✅ | OpenAI 兼容 |
| Kimi 月之暗面 | ✅ | ✅ | OpenAI 兼容 |
| 通义千问（百炼） | ✅ | ✅ | OpenAI 兼容 |
| Anthropic Claude | ✅ | ✅ | Messages API |
| 自定义 / Ollama / vLLM | ✅ | ✅ | OpenAI 兼容 |
| 本地模式（无需 Key） | ➖ | ✅ 规则引擎 | 离线 |

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 及以上
- JDK 17
- Android SDK 34（最低支持 Android 8.0 / API 26）

### 构建运行

```bash
git clone https://github.com/Ada-Junk/HomeHealth.git
cd HomeHealth

# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

或直接用 Android Studio 打开项目，点击 Run。APK 输出在 `app/build/outputs/apk/debug/`。

> 💡 **开箱即用**：不配置任何 LLM Key 也能使用家庭档案、手动记录、趋势图、预警、用药提醒和离线健康问答；在「设置 → AI 服务配置」中填入任意供应商的 API Key 后，即可解锁拍照解析报告和 AI 问答。

## 📁 项目结构

```
app/src/main/java/com/example/homehealth/
├── data/            # 数据层：Room 数据库、DAO、LLM 客户端、Repository 实现
│   ├── remote/      #   LlmClient（双协议）、LlmProviders、离线问答引擎
│   ├── local/       #   Room 实体、DAO（6 表）
│   └── repository/  #   Repository 实现
├── domain/          # 业务层：领域模型、Repository 接口、Use Cases
├── di/              # Hilt 依赖注入模块（含渐进式数据库迁移）
├── ui/              # Compose 界面：导航、模块主题、8 个功能页面
├── worker/          # WorkManager 后台任务与通知
└── util/            # SchemaNormalizer、CalendarEventHelper、HealthTypes 等
```

完整设计文档见 [docs/开发文档.md](./docs/开发文档.md)。

## 🗺️ Roadmap

- [ ] 支持更多文档类型（影像报告等）
- [ ] 集成 Health Connect 可穿戴设备数据
- [ ] 本地 LLM 推理，完全离线运行
- [ ] 多设备同步（加密云备份）
- [ ] 家庭数据共享与远程关怀

## ⚠️ 免责声明

本项目提供的指标参考范围、异常预警和问答内容**仅供健康管理参考，不构成医疗建议**。如有健康问题，请及时咨询专业医生。

## 📄 License

本项目基于 [MIT License](./LICENSE) 开源。

---

<div align="center">

**🇬🇧 English Introduction**

**HomeHealth** is an AI-powered family health records app for Android. Snap a photo of a lab report and a **Vision LLM** extracts 49 types of structured health metrics via JSON mode — then **Schema Normalization** maps 110+ metric aliases to a standard dictionary, unifies 25 unit spellings and applies 24 clinically reliable unit conversions (e.g., vitamin D nmol/L → ng/mL) before anything touches the database.

**Highlights**

- 📐 **Schema Normalization** — one consistent measurement system behind every trend chart and anomaly alert
- 👁️ **Strict vision/text model separation** — runtime validation prevents hallucinated report parsing
- 🔌 **8 LLM providers, dual protocols** (OpenAI-compatible + Anthropic Messages API), reasoning-process display for thinking models
- 🔐 **Local-first & privacy-focused** — all health data and API keys stay on device; fully usable offline without any key
- 📅 **Calendar two-way sync** — medication reminders write to (and clean up from) the system calendar
- 🎨 **Per-module vibrant theming** — each of the 5 tabs carries its own youthful accent color

**Tech stack**: Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · Room · Retrofit/OkHttp · Coroutines/Flow · WorkManager

> ⚕️ Disclaimer: reference ranges, alerts and answers are for wellness reference only and are not medical advice.

</div>
