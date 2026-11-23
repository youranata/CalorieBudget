package com.example.caloriewidget.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.caloriewidget.CalorieWidgetApp
import com.example.caloriewidget.domain.DateManager
import com.example.caloriewidget.receiver.ResetScheduler
import com.example.caloriewidget.widget.CalorieWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {
    protected val repository get() = (application as CalorieWidgetApp).repository
    protected fun content(root: View) {
        setContentView(root)
        val l = root.paddingLeft; val t = root.paddingTop; val r = root.paddingRight; val b = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous: String? = null
                while (true) {
                    val date = DateManager.todayString()
                    if (date != previous) {
                        repository.ensureDate()
                        onDateChecked(date)
                        CalorieWidgetProvider.refreshAll(this@BaseActivity)
                        ResetScheduler.scheduleNextMidnight(this@BaseActivity)
                        previous = date
                    }
                    delay((DateManager.nextMidnight() - System.currentTimeMillis() + 50).coerceIn(50, 30_000))
                }
            }
        }
    }
    protected open fun onDateChecked(date: String) {}
}
