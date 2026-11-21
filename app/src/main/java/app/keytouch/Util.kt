package app.keytouch

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Ukuran layar fisik (px), sama dengan sistem koordinat gesture. */
@Suppress("DEPRECATION")
fun Context.screenSize(): Point {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        Point(b.width(), b.height())
    } else {
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        Point(m.widthPixels, m.heightPixels)
    }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
