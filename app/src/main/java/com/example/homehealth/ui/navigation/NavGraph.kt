package com.example.homehealth.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.homehealth.R
import com.example.homehealth.ui.screens.alerts.AlertsScreen
import com.example.homehealth.ui.screens.documentupload.DocumentUploadScreen
import com.example.homehealth.ui.screens.familylist.FamilyListScreen
import com.example.homehealth.ui.screens.memberdetail.MemberDetailScreen
import com.example.homehealth.ui.screens.qa.QAScreen
import com.example.homehealth.ui.screens.recorddetail.RecordDetailScreen
import com.example.homehealth.ui.screens.reminders.RemindersScreen
import com.example.homehealth.ui.screens.settings.SettingsScreen
import com.example.homehealth.ui.theme.ModuleTheme
import com.example.homehealth.ui.theme.ModuleThemedTheme

/** 路由定义 */
object Routes {
    const val FAMILY = "family"
    const val ALERTS = "alerts"
    const val REMINDERS = "reminders"
    const val QA = "qa"
    const val SETTINGS = "settings"
    const val MEMBER = "member/{memberId}"
    const val RECORD = "member/{memberId}/record/{type}"
    const val UPLOAD = "upload/{memberId}"

    fun member(memberId: String) = "member/$memberId"
    fun record(memberId: String, type: String) = "member/$memberId/record/$type"
    fun upload(memberId: String) = "upload/$memberId"
}

private data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val module: ModuleTheme
)

/** 应用根导航：底部导航（各模块独立主题色）+ NavHost（按当前模块切换主题） */
@Composable
fun RootApp(navController: NavHostController, darkTheme: Boolean) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem(Routes.FAMILY, R.string.nav_family, Icons.Filled.Home, ModuleTheme.FAMILY),
        BottomItem(Routes.ALERTS, R.string.nav_alerts, Icons.Filled.Notifications, ModuleTheme.ALERTS),
        BottomItem(Routes.REMINDERS, R.string.nav_reminders, Icons.Filled.Alarm, ModuleTheme.REMINDERS),
        BottomItem(Routes.QA, R.string.nav_qa, Icons.Filled.QuestionAnswer, ModuleTheme.QA),
        BottomItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, ModuleTheme.SETTINGS)
    )
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    // 键盘弹出时隐藏底部导航：问答输入栏需要贴合键盘，导航栏被键盘盖住毫无意义，
    // 且隐藏后外层 content padding 收缩，配合 imePadding/consumeWindowInsets 输入栏才能贴平键盘上沿
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // 当前模块：底部页取自身模块；二级页（成员/记录/上传）归入家庭模块
    val currentModule = when {
        currentRoute == Routes.ALERTS -> ModuleTheme.ALERTS
        currentRoute == Routes.REMINDERS -> ModuleTheme.REMINDERS
        currentRoute == Routes.QA -> ModuleTheme.QA
        currentRoute == Routes.SETTINGS -> ModuleTheme.SETTINGS
        else -> ModuleTheme.FAMILY
    }

    ModuleThemedTheme(module = currentModule, darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                if (showBottomBar && !imeVisible) {
                    NavigationBar {
                        bottomItems.forEach { item ->
                            val selected = currentRoute == item.route
                            // 底部导航各模块用自身主题色：选中纯色，未选中淡化
                            val moduleColor = if (darkTheme) item.module.primaryDark
                            else item.module.primaryLight
                            val containerColor = if (darkTheme) item.module.containerDark
                            else item.module.containerLight
                            val itemColor = if (selected) moduleColor
                            else moduleColor.copy(alpha = 0.45f)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Routes.FAMILY) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        item.icon,
                                        contentDescription = stringResource(item.labelRes),
                                        tint = itemColor
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(item.labelRes),
                                        color = if (selected) itemColor else Color.Unspecified
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = containerColor.copy(alpha = 0.9f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.FAMILY,
                modifier = Modifier.padding(padding)
            ) {
                composable(Routes.FAMILY) {
                    FamilyListScreen(navController)
                }
                composable(
                    Routes.MEMBER,
                    arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                ) {
                    MemberDetailScreen(navController)
                }
                composable(
                    Routes.RECORD,
                    arguments = listOf(
                        navArgument("memberId") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType }
                    )
                ) {
                    RecordDetailScreen(navController)
                }
                composable(
                    Routes.UPLOAD,
                    arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                ) {
                    DocumentUploadScreen(navController)
                }
                composable(Routes.ALERTS) {
                    AlertsScreen(navController)
                }
                composable(Routes.REMINDERS) {
                    RemindersScreen(navController)
                }
                composable(Routes.QA) {
                    QAScreen(navController)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(navController)
                }
            }
        }
    }
}
