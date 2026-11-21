package app.keytouch

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * View 1x1 px yang tidak menangkap sentuhan, tapi memegang "pointer capture" supaya
 * gerakan mouse masuk sebagai delta relatif (tanpa batas tepi layar) dan klik mouse terbaca.
 */
class CaptureView(ctx: Context, private val host: Host) : View(ctx) {

    interface Host {
        fun onMouseDelta(dx: Float, dy: Float)
        fun onMouseButton(code: Int, down: Boolean)
    }

    private var lastButtons = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnCapturedPointerListener { _, e ->
            handle(e)
            true
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
            requestPointerCapture()
        } else {
            releasePointerCapture()
        }
    }

    private fun handle(e: MotionEvent) {
        // Di mode capture, x/y = perpindahan relatif. Jumlahkan juga sampel yang digabung (history).
        var dx = e.x
        var dy = e.y
        for (i in 0 until e.historySize) {
            dx += e.getHistoricalX(i)
            dy += e.getHistoricalY(i)
        }
        if (dx != 0f || dy != 0f) host.onMouseDelta(dx, dy)

        val now = e.buttonState
        val changed = now xor lastButtons
        if (changed and MotionEvent.BUTTON_PRIMARY != 0)
            host.onMouseButton(Keys.MOUSE_LEFT, now and MotionEvent.BUTTON_PRIMARY != 0)
        if (changed and MotionEvent.BUTTON_SECONDARY != 0)
            host.onMouseButton(Keys.MOUSE_RIGHT, now and MotionEvent.BUTTON_SECONDARY != 0)
        if (changed and MotionEvent.BUTTON_TERTIARY != 0)
            host.onMouseButton(Keys.MOUSE_MIDDLE, now and MotionEvent.BUTTON_TERTIARY != 0)
        lastButtons = now
    }
}
