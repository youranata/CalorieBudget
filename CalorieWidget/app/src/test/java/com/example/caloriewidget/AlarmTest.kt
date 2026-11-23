package com.example.caloriewidget

import android.app.AlarmManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.caloriewidget.domain.DateManager
import com.example.caloriewidget.receiver.ResetScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = CalorieWidgetApp::class)
class AlarmTest {
    @Test fun reSchedulingReplacesTheAlarmAndPermissionDenialStillSchedules() {
        val app = ApplicationProvider.getApplicationContext<CalorieWidgetApp>()
        val manager = app.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) ShadowAlarmManager.setCanScheduleExactAlarms(false)
        repeat(4) { ResetScheduler.scheduleNextMidnight(app) }
        val shadow = shadowOf(manager)
        assertEquals(1, shadow.scheduledAlarms.size)
        val alarm = requireNotNull(shadow.peekNextScheduledAlarm())
        assertEquals(DateManager.nextMidnight(), alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
        if (Build.VERSION.SDK_INT >= 31) {
            assertNotEquals(0L, alarm.windowLengthMs)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            ResetScheduler.scheduleNextMidnight(app)
            assertEquals(0L, requireNotNull(shadow.peekNextScheduledAlarm()).windowLengthMs)
        } else assertEquals(0L, alarm.windowLengthMs)
    }
}
