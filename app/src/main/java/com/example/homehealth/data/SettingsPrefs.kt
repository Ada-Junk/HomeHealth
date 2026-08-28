package com.example.homehealth.data

import android.content.Context
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用设置（SharedPreferences）。
 * 报告解析服务与健康问答服务各自独立配置：供应商 / API Key / 模型 / 服务地址。
 */
@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val sp = context.getSharedPreferences("homehealth_settings", Context.MODE_PRIVATE)

    init {
        migrateOldServiceMode()
    }

    // ---- 报告解析服务 ----

    /** 解析供应商：local / backend / zhipu / openai / gemini / deepseek / custom */
    var parseProvider: String
        get() = sp.getString(KEY_PARSE_PROVIDER, LlmProviders.LOCAL) ?: LlmProviders.LOCAL
        set(value) = sp.edit().putString(KEY_PARSE_PROVIDER, value).apply()

    /** 解析服务 API Key */
    var parseApiKey: String
        get() = sp.getString(KEY_PARSE_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_PARSE_KEY, value.trim()).apply()

    /** 解析模型（空 = 用供应商默认视觉模型） */
    var parseModel: String
        get() = sp.getString(KEY_PARSE_MODEL, "") ?: ""
        set(value) = sp.edit().putString(KEY_PARSE_MODEL, value.trim()).apply()

    /** 解析服务地址：backend 时为后端地址；custom 时为 OpenAI 兼容地址 */
    var parseBaseUrl: String
        get() = sp.getString(KEY_PARSE_URL, Constants.DEFAULT_BASE_URL) ?: Constants.DEFAULT_BASE_URL
        set(value) = sp.edit().putString(KEY_PARSE_URL, value.trim()).apply()

    // ---- 健康问答服务 ----

    /** 问答供应商：local / backend / zhipu / openai / gemini / deepseek / custom */
    var qaProvider: String
        get() = sp.getString(KEY_QA_PROVIDER, LlmProviders.LOCAL) ?: LlmProviders.LOCAL
        set(value) = sp.edit().putString(KEY_QA_PROVIDER, value).apply()

    /** 问答服务 API Key */
    var qaApiKey: String
        get() = sp.getString(KEY_QA_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_QA_KEY, value.trim()).apply()

    /** 问答模型（空 = 用供应商默认文本模型） */
    var qaModel: String
        get() = sp.getString(KEY_QA_MODEL, "") ?: ""
        set(value) = sp.edit().putString(KEY_QA_MODEL, value.trim()).apply()

    /** 问答服务地址 */
    var qaBaseUrl: String
        get() = sp.getString(KEY_QA_URL, Constants.DEFAULT_BASE_URL) ?: Constants.DEFAULT_BASE_URL
        set(value) = sp.edit().putString(KEY_QA_URL, value.trim()).apply()

    /** 旧版本（单 serviceMode）一次性迁移到双服务配置 */
    private fun migrateOldServiceMode() {
        if (sp.contains(KEY_PARSE_PROVIDER) || !sp.contains("service_mode")) return

        val mode = sp.getString("service_mode", LlmProviders.LOCAL) ?: LlmProviders.LOCAL
        val oldKey = sp.getString("zhipu_api_key", "") ?: ""
        val oldUrl = sp.getString("api_base_url", Constants.DEFAULT_BASE_URL)
            ?: Constants.DEFAULT_BASE_URL

        sp.edit()
            .putString(KEY_PARSE_PROVIDER, mode)
            .putString(KEY_QA_PROVIDER, mode)
            .putString(KEY_PARSE_KEY, oldKey)
            .putString(KEY_QA_KEY, oldKey)
            .putString(KEY_PARSE_MODEL, "")
            .putString(KEY_QA_MODEL, "")
            .putString(KEY_PARSE_URL, oldUrl)
            .putString(KEY_QA_URL, oldUrl)
            .apply()
    }

    companion object {
        private const val KEY_PARSE_PROVIDER = "parse_provider"
        private const val KEY_PARSE_KEY = "parse_api_key"
        private const val KEY_PARSE_MODEL = "parse_model"
        private const val KEY_PARSE_URL = "parse_base_url"
        private const val KEY_QA_PROVIDER = "qa_provider"
        private const val KEY_QA_KEY = "qa_api_key"
        private const val KEY_QA_MODEL = "qa_model"
        private const val KEY_QA_URL = "qa_base_url"
    }
}
