package com.hiddenramblings.tagmo

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

private data class InitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

fun ComponentActivity.enableEdgeToEdgeContent() {
    val appThemeColor = ContextCompat.getColor(this, R.color.colorPrimary)
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(appThemeColor, appThemeColor),
        navigationBarStyle = SystemBarStyle.auto(appThemeColor, appThemeColor)
    )
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

fun View.applySystemBarInsets(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true
) {
    val initialPadding = InitialPadding(
        paddingLeft,
        paddingTop,
        paddingRight,
        paddingBottom
    )
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = initialPadding.left + if (left) systemBars.left else 0,
            top = initialPadding.top + if (top) systemBars.top else 0,
            right = initialPadding.right + if (right) systemBars.right else 0,
            bottom = initialPadding.bottom + if (bottom) systemBars.bottom else 0
        )
        insets
    }
    if (ViewCompat.isAttachedToWindow(this)) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(view)
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}
