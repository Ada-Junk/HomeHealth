# 🏠 HomeHealth 家庭健康管家

> 一款 AI 驱动的家庭健康档案 Android 应用 —— 拍照上传体检报告，自动提取健康指标，构建家庭健康档案，趋势分析 · 异常预警 · 健康问答。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-blue)](./app/build.gradle.kts)

**English introduction below 👇**

---

## 📖 项目简介

HomeHealth 面向多成员家庭，帮助用户集中管理全家人的健康数据。只需拍照或上传体检报告、化验单、处方等医疗文档，应用即可借助多模态大模型自动解析出结构化健康指标（血压、血糖、血脂等），建立每位家庭成员的独立健康档案，并基于历史数据提供趋势分析、异常预警和健康问答。

**本地优先，隐私至上**：所有健康数据以 Room 数据库存储在设备本地，不上传任何第三方服务器；LLM 仅在你主动配置后才用于报告解析与问答，不配置也完全可用（内置离线问答引擎）。

## ✨ 核心特性

| 功能 | 说明 |
| --- | --- |
| 👨‍👩‍👧‍👦 多成员家庭档案 | 每位成员独立健康档案，关系标注（本人 / 父母 / 配偶…） |
| 📸 智能报告解析 | 拍照 / 相册上传，多模态 LLM 自动 OCR + 结构化提取，人工确认后入库 |
| 📈 历史趋势分析 | 指标趋势折线图，跨时间对比各项健康指标 |
| 🚨 主动异常预警 | 规则引擎检测超范围 / 趋势异常，分级预警 + 系统通知 |
| 💬 健康问答 | 基于个人健康数据的问答（RAG / 离线规则引擎双模式） |
| 💊 用药提醒 | 灵活的用药计划管理，WorkManager 每日定时提醒 |
| 🔌 多 LLM 供应商 | 智谱 GLM / OpenAI / Gemini / DeepSeek / Kimi / 通义千问 / Claude / 自定义兼容服务 |
| 🔐 本地优先存储 | 健康数据仅存于设备本地，充分保护隐私 |

## 🏗️ 技术架构

- **语言**：Kotlin · 协程 + Flow
- **UI**：Jetpack Compose + Material 3 · 单 Activity + Navigation
- **架构**：MVVM + Clean Architecture（`ui` / `domain` / `data` 分层）
- **依赖注入**：Hilt
- **本地存储**：Room（成员、指标、文档、预警、提醒、问答历史）
- **网络**：Retrofit + OkHttp + Gson（LLM / 自建后端调用）
- **图片**：Coil · 相机拍照上传
- **后台**：WorkManager 每日健康检查 + 通知推送

```
┌─────────────────────────────┐
│      Presentation Layer     │  Compose UI · ViewModel
├─────────────────────────────┤
│        Domain Layer         │  Use Cases · Repository 接口
├─────────────────────────────┤
│         Data Layer          │  Repository 实现
│  ┌──────────┬─────────────┐ │
│  │ Room 本地 │ LLM / 后端  │ │
│  └──────────┴─────────────┘ │
└─────────────────────────────┘
```

## 🤖 LLM 供应商支持

报告解析使用**视觉模型**、健康问答使用**文本模型**，两类独立配置，应用会校验防止误用。所有 API Key 仅保存在设备本地。

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
# 克隆项目
git clone https://github.com/Ada-Junk/HomeHealth.git
cd HomeHealth

# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

或直接用 Android Studio 打开项目，点击 Run。APK 输出在 `app/build/outputs/apk/debug/`。

> 💡 开箱即用：不配置任何 LLM Key 也能使用家庭档案、手动记录、趋势图、预警、用药提醒和离线健康问答；在「设置」中填入任意供应商的 API Key 后，即可解锁拍照解析报告和 AI 问答。

## 📁 项目结构

```
app/src/main/java/com/example/homehealth/
├── data/            # 数据层：Room 数据库、DAO、LLM 客户端、Repository 实现
├── domain/          # 业务层：领域模型、Repository 接口、Use Cases
├── di/              # Hilt 依赖注入模块
├── ui/              # Compose 界面：导航、主题、各功能页面
├── worker/          # WorkManager 后台任务与通知
└── util/            # 工具类
```

完整设计文档见 [docs/开发文档.md](./docs/开发文档.md)。

## 🗺️ Roadmap

- [ ] 支持更多文档类型（影像报告等）
- [ ] 集成 Health Connect 可穿戴设备数据
- [ ] 本地 LLM 推理，完全离线运行
- [ ] 多设备同步（加密云备份）

## ⚠️ 免责声明

本项目提供的指标参考范围、异常预警和问答内容**仅供健康管理参考，不构成医疗建议**。如有健康问题，请及时咨询专业医生。

## 📄 License

本项目基于 [MIT License](./LICENSE) 开源。

---

## 🇬🇧 English Introduction

**HomeHealth** is an AI-powered family health records app for Android. Snap a photo of a medical report (lab results, prescriptions, check-up summaries) and a multimodal LLM extracts structured health metrics — blood pressure, glucose, cholesterol and more — into per-member family profiles.

**Highlights**

- 👨‍👩‍👧‍👦 Independent health profiles for every family member
- 📸 Photo → structured metrics via multimodal LLMs (with human confirmation)
- 📈 Trend charts, rule-based anomaly alerts with system notifications
- 💬 Health Q&A grounded in personal records (RAG, plus an offline rule-based engine)
- 💊 Medication reminders via WorkManager
- 🔌 Pluggable LLM providers: Zhipu GLM, OpenAI, Gemini, DeepSeek, Kimi, Qwen, Claude, or any OpenAI-compatible endpoint (Ollama, vLLM…)
- 🔐 Local-first & privacy-focused — all health data stays on device in Room; API keys never leave the phone

**Tech stack**: Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · Room · Retrofit/OkHttp · Coroutines/Flow · WorkManager

> ⚕️ Disclaimer: reference ranges, alerts and answers are for wellness reference only and are not medical advice.
