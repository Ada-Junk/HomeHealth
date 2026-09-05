package com.example.homehealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

@Composable
fun HomeHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

/**
 * 模块主题：五大底部模块各有专属活力主题色，
 * 在基础色板上覆盖 primary / primaryContainer，页面其余颜色保持全局一致。
 * - 家庭：珊瑚红（温暖）  - 预警：活力橙（警醒）
 * - 提醒：生命绿（生长）  - 问答：天空蓝（理性）  - 设置：紫罗兰（个性）
 */
enum class ModuleTheme(
    val primaryLight: Color,
    val containerLight: Color,
    val primaryDark: Color,
    val containerDark: Color
) {
    FAMILY(FamilyPrimaryLight, FamilyContainerLight, FamilyPrimaryDark, FamilyContainerDark),
    ALERTS(AlertsPrimaryLight, AlertsContainerLight, AlertsPrimaryDark, AlertsContainerDark),
    REMINDERS(RemindersPrimaryLight, RemindersContainerLight, RemindersPrimaryDark, RemindersContainerDark),
    QA(QaPrimaryLight, QaContainerLight, QaPrimaryDark, QaContainerDark),
    SETTINGS(SettingsPrimaryLight, SettingsContainerLight, SettingsPrimaryDark, SettingsContainerDark)
}

/** 按模块主题覆盖色板的 Composable */
@Composable
fun ModuleThemedTheme(
    module: ModuleTheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColors else LightColors
    val primary = if (darkTheme) module.primaryDark else module.primaryLight
    val container = if (darkTheme) module.containerDark else module.containerLight
    // onPrimary 容器为深色时用白字，浅色容器用深字，保证对比度
    val onPrimary = Color.White
    val onContainer = if (darkTheme) Color(0xFF141414) else Color(0xFF231A1E)

    val scheme = base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
