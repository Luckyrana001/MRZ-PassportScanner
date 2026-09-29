package com.android.mrzcardreader

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Configures transparent system bars and icon contrast for an edge-to-edge screen. */
fun AppCompatActivity.configureEdgeToEdge(lightSystemBars: Boolean? = null) {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    @Suppress("DEPRECATION")
    window.statusBarColor = Color.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor = Color.TRANSPARENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    val useDarkIcons = lightSystemBars ?: run {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        nightMode != Configuration.UI_MODE_NIGHT_YES
    }
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = useDarkIcons
        isAppearanceLightNavigationBars = useDarkIcons
    }
}

/** Adds system-bar and display-cutout insets without losing a view's XML padding. */
fun View.applySafeDrawingInsets(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val safeInsets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(
            initialLeft + if (left) safeInsets.left else 0,
            initialTop + if (top) safeInsets.top else 0,
            initialRight + if (right) safeInsets.right else 0,
            initialBottom + if (bottom) safeInsets.bottom else 0,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
