package com.jaysay.coursetable.data.reminder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 引导用户前往系统设置开启应用自启动。
 *
 * 上课提醒在应用未打开时依赖系统闹钟触发；部分厂商 ROM（小米/华为/OPPO/vivo 等）
 * 会把“划掉后台”当作冻结应用并拦截闹钟，因此需要用户手动允许自启动。
 * 各厂商自启动管理页的入口组件并不稳定，这里依次尝试厂商专用页面，
 * 失败时回退到通用的应用详情设置页（用户可在其中手动找到“自启动/后台运行”开关）。
 */
object AutostartHelper {

    /** 依次尝试跳转厂商自启动页，全部失败时回退应用详情页。返回是否跳转成功。 */
    fun launch(context: Context): Boolean {
        if (tryManufacturerPage(context)) return true
        return launchAppDetails(context)
    }

    /** 跳转通用应用详情设置页。 */
    fun launchAppDetails(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    private fun tryManufacturerPage(context: Context): Boolean {
        val candidates = when {
            isMiui() -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter" to "com.miui.permcenter.MainAcitivty"
            )
            isEmui() -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
            isOppo() -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
            isVivo() -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
            isSamsung() -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            else -> emptyList()
        }
        for ((pkg, cls) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching { context.startActivity(intent) }.isSuccess
            if (resolved) return true
        }
        return false
    }

    private fun isMiui(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            hasSystemFeature("miui") || hasSystemFeature("miui.system")

    private fun isEmui(): Boolean =
        Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
            Build.MANUFACTURER.equals("HONOR", ignoreCase = true)

    private fun isOppo(): Boolean =
        Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ||
            Build.MANUFACTURER.equals("realme", ignoreCase = true)

    private fun isVivo(): Boolean =
        Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
            Build.MANUFACTURER.equals("iQOO", ignoreCase = true)

    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun hasSystemFeature(name: String): Boolean = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as String
    }.getOrNull()?.isNotBlank() == true
}
