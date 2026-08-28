package com.example.homehealth.ui.navigation

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.homehealth.ui.screens.alerts.AlertsScreen
import com.example.homehealth.ui.screens.documentupload.DocumentUploadScreen
import com.example.homehealth.ui.screens.familylist.FamilyListScreen
import com.example.homehealth.ui.screens.memberdetail.MemberDetailScreen
import com.example.homehealth.ui.screens.qa.QAScreen
import com.example.homehealth.ui.screens.recorddetail.RecordDetailScreen
import com.example.homehealth.ui.screens.reminders.RemindersScreen
import com.example.homehealth.ui.screens.settings.SettingsScreen

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
    val label: String,
    val icon: ImageVector
)

/** 应用根导航：底部导航 + NavHost */
@Composable
fun RootApp(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem(Routes.FAMILY, "家庭", Icons.Filled.Home),
        BottomItem(Routes.ALERTS, "预警", Icons.Filled.Notifications),
        BottomItem(Routes.REMINDERS, "提醒", Icons.Filled.Alarm),
        BottomItem(Routes.QA, "问答", Icons.Filled.QuestionAnswer),
        BottomItem(Routes.SETTINGS, "设置", Icons.Filled.Settings)
    )
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.FAMILY) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
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
