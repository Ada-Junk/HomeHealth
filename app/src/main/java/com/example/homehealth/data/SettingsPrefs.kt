package com.example.homehealth.data

import android.content.Context
import com.example.homehealth.data.remote.LlmProviders
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用设置（SharedPreferences）。
 * 报告解析服务与健康问答服务各自独立配置：供应商 / API Key / 模型 / 服务地址。
 */
@Singleton
class SettingsPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val sp = context.getSharedPreferences("homehealth_settings", Context.MODE_PRIVATE)

    private val _themeModeFlow = MutableStateFlow(sp.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)

    /** 外观模式流（设置页切换后全局即时生效，无需重启） */
    val themeModeFlow: StateFlow<String> = _themeModeFlow

    init {
        migrateOldServiceMode()
        migrateRemovedBackend()
    }

    // ---- 外观模式 ----

    /** 外观模式：system（跟随系统）/ light（浅色）/ dark（深色） */
    var themeMode: String
        get() = _themeModeFlow.value
        set(value) {
            sp.edit().putString(KEY_THEME_MODE, value).apply()
            _themeModeFlow.value = value
        }

    // ---- 报告解析服务 ----

    /** 解析供应商：local / zhipu / openai / gemini / deepseek / kimi / qwen / anthropic / custom */
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

    /** 解析服务地址（custom 时为 OpenAI 兼容地址，如 http://192.168.1.10:11434/v1/） */
    var parseBaseUrl: String
        get() = sp.getString(KEY_PARSE_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_PARSE_URL, value.trim()).apply()

    // ---- 健康问答服务 ----

    /** 问答供应商：local / zhipu / openai / gemini / deepseek / kimi / qwen / anthropic / custom */
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

    /** 问答服务地址（custom 时为 OpenAI 兼容地址） */
    var qaBaseUrl: String
        get() = sp.getString(KEY_QA_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_QA_URL, value.trim()).apply()

    /** 健康问答上次咨询的成员（重启后自动选中，直接显示历史对话） */
    var qaMemberId: String
        get() = sp.getString(KEY_QA_MEMBER, "") ?: ""
        set(value) = sp.edit().putString(KEY_QA_MEMBER, value).apply()

    /** 旧版本（单 serviceMode）一次性迁移到双服务配置 */
    private fun migrateOldServiceMode() {
        if (sp.contains(KEY_PARSE_PROVIDER) || !sp.contains("service_mode")) return

        val mode = sp.getString("service_mode", LlmProviders.LOCAL) ?: LlmProviders.LOCAL
        val oldKey = sp.getString("zhipu_api_key", "") ?: ""
        val oldUrl = sp.getString("api_base_url", "") ?: ""

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

    /** 自建后端服务已下线：历史选择了 backend 的用户迁移回本地模式 */
    private fun migrateRemovedBackend() {
        val legacyBackend = "backend"
        val parse = sp.getString(KEY_PARSE_PROVIDER, null)
        val qa = sp.getString(KEY_QA_PROVIDER, null)
        if (parse == legacyBackend || qa == legacyBackend) {
            sp.edit().apply {
                if (parse == legacyBackend) putString(KEY_PARSE_PROVIDER, LlmProviders.LOCAL)
                if (qa == legacyBackend) putString(KEY_QA_PROVIDER, LlmProviders.LOCAL)
            }.apply()
        }
    }

    companion object {
        /** 外观模式取值 */
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PARSE_PROVIDER = "parse_provider"
        private const val KEY_PARSE_KEY = "parse_api_key"
        private const val KEY_PARSE_MODEL = "parse_model"
        private const val KEY_PARSE_URL = "parse_base_url"
        private const val KEY_QA_PROVIDER = "qa_provider"
        private const val KEY_QA_KEY = "qa_api_key"
        private const val KEY_QA_MODEL = "qa_model"
        private const val KEY_QA_URL = "qa_base_url"
        private const val KEY_QA_MEMBER = "qa_member_id"
    }
}
