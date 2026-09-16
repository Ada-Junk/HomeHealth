package com.example.homehealth.data.remote

/**
 * ReAct 循环的护栏参数。
 *
 * 集中一处，与 `util/DetectionConfig` 同风格：所有"上限"一眼可见，调参不必翻循环体。
 * 这些数字不是拍脑袋 —— 每一条都对应一种具体的失控方式，见各字段注释。
 */
object AgentConfig {

    /**
     * 最多几轮 LLM 请求（含最终回答轮）。
     * 为什么是 5：超过 5 轮还没收敛的问题，答案本身已不可靠；而每轮都要重发工具 schema
     * 与全部历史 observation，token 成本随轮数**超线性**增长。
     */
    const val MAX_TURNS = 5

    /** 一次提问允许的工具调用总次数：防"单轮并行调用 N 个 × 多轮"叠加 */
    const val MAX_TOOL_CALLS = 8

    /** 同一工具连续失败多少次后禁用：防止模型反复撞同一面墙（查错类型 → 重试 → 再错） */
    const val MAX_TOOL_CONSECUTIVE_FAILURES = 2

    /**
     * 单个工具结果拼进上下文的字符上限。
     * 与 `QARepositoryImpl.SUMMARY_CHAR_BUDGET` 同一思路，但预算独立：
     * 一次 search_records 返回多项 × 多条就能把上下文撑爆。
     */
    const val OBSERVATION_CHAR_BUDGET = 1_500

    /** 本地工具超时：查记录 / 查参考范围 / 读告警都应远快于此 */
    const val TOOL_TIMEOUT_MS = 10_000L

    /** 视觉工具超时：读图是真实网络请求，与本地工具不是一个量级 */
    const val VISION_TOOL_TIMEOUT_MS = 60_000L

    /** 路由为 Agent 路径时，最终回答轮的采样温度 */
    const val ANSWER_TEMPERATURE = 0.3

    /** 路由为 Agent 路径时，最终回答轮的最大输出长度 */
    const val ANSWER_MAX_TOKENS = 2048
}
