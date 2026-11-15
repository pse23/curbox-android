package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker

import android.content.Context
import android.text.format.DateFormat
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.AppUsageConfig
import neth.iecal.curbox.data.models.TimeInterval
import neth.iecal.curbox.utils.AppGroupScheduleConflict
import neth.iecal.curbox.utils.isAllDaySchedule
import java.util.Calendar

fun Context.appGroupScheduleSummary(config: AppTimeConfig): String {
    if (config.isAllDaySchedule()) {
        return getString(R.string.schedule_summary_all_day)
    }

    val dailyInterval = config.everydayIntervals.singleOrNull()
    if (config.isEveryday && dailyInterval != null) {
        return getString(
            R.string.schedule_summary_daily,
            formatScheduleTime(dailyInterval.startHour, dailyInterval.startMinute),
            formatScheduleTime(dailyInterval.endHour, dailyInterval.endMinute)
        )
    }

    val weekdayInterval = config.singleWeekdayInterval()
    if (weekdayInterval != null) {
        return getString(
            R.string.schedule_summary_weekdays,
            formatScheduleTime(weekdayInterval.startHour, weekdayInterval.startMinute),
            formatScheduleTime(weekdayInterval.endHour, weekdayInterval.endMinute)
        )
    }

    return getString(R.string.schedule_summary_custom)
}

fun Context.appGroupUsageSummary(usage: AppUsageConfig): String = when {
    !usage.isDailyUniform -> getString(R.string.usage_summary_varies)
    usage.uniformLimit <= 0L -> getString(R.string.usage_summary_none)
    else -> getString(R.string.usage_summary_minutes, usage.uniformLimit)
}

fun Context.appGroupConflictMessage(conflicts: List<AppGroupScheduleConflict>): String {
    return conflicts.joinToString("\n\n") { conflict ->
        val appNames = conflict.sharedPackages
            .map(::applicationLabel)
            .sorted()
            .joinToString(", ")
        getString(
            R.string.schedule_conflict_item,
            appNames,
            conflict.group.name
        )
    }
}

private fun Context.formatScheduleTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour % 24)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(this).format(calendar.time)
}

private fun AppTimeConfig.singleWeekdayInterval(): TimeInterval? {
    if (isEveryday) return null
    val activeDays = dailyIntervals
        .filterValues(List<TimeInterval>::isNotEmpty)
        .keys
    if (activeDays != setOf(1, 2, 3, 4, 5)) return null

    val mondayInterval = dailyIntervals[1]?.singleOrNull() ?: return null
    return if ((2..5).all { dailyIntervals[it]?.singleOrNull() == mondayInterval }) {
        mondayInterval
    } else {
        null
    }
}

private fun Context.applicationLabel(packageName: String): String {
    return try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        packageName
    }
}

/** Lets other packages build the same summary line without importing the extensions. */
object AppGroupUiTextBridge {
    fun scheduleSummary(context: Context, config: AppTimeConfig): String =
        context.appGroupScheduleSummary(config)

    fun usageSummary(context: Context, usage: AppUsageConfig): String =
        context.appGroupUsageSummary(usage)
}
