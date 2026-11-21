package app.keytouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

/**
 * Menyuntikkan banyak "jari" virtual sekaligus lewat dispatchGesture (tanpa root / Shizuku).
 *
 * Cara kerja: setiap jari = satu stroke yang terus disambung (continueStroke) dalam potongan
 * gesture ~30 ms. Potongan berikutnya dikirim begitu potongan sebelumnya selesai, sehingga
 * jari terasa menempel terus di layar dan bisa digeser mulus.
 */
class TouchInjector(private val svc: AccessibilityService) {

    private class Finger(var x: Float, var y: Float) {
        var tx = x
        var ty = y
        var stroke: GestureDescription.StrokeDescription? = null
        var up = false          // sudah diminta diangkat
        var next: Finger? = null // jari pengganti setelah yang ini diangkat (re-anchor)
    }

    private val fingers = LinkedHashMap<Int, Finger>()
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    /** Taruh jari di (x,y). Jika slot sudah dipakai, jari lama diangkat lalu jari baru turun. */
    fun down(slot: Int, x: Float, y: Float) {
        val old = fingers[slot]
        if (old != null) {
            old.up = true
            old.next = Finger(x, y)
        } else {
            fingers[slot] = Finger(x, y)
        }
        pump()
    }

    fun move(slot: Int, x: Float, y: Float) {
        val f = fingers[slot] ?: return
        val t = f.next ?: f
        t.tx = x
        t.ty = y
        pump()
    }

    fun up(slot: Int) {
        val f = fingers[slot] ?: return
        f.next = null
        f.up = true
        pump()
    }

    fun releaseAll() {
        for (f in fingers.values) {
            f.next = null
            f.up = true
        }
        pump()
    }

    private fun pump() {
        if (busy || fingers.isEmpty()) return
        val sz = svc.screenSize()
        val maxX = (sz.x - 2).toFloat()
        val maxY = (sz.y - 2).toFloat()
        val builder = GestureDescription.Builder()
        val finished = ArrayList<Int>()
        var n = 0
        val limit = GestureDescription.getMaxStrokeCount()

        for ((slot, f) in fingers) {
            if (n >= limit) break
            val sx = f.x.coerceIn(1f, maxX)
            val sy = f.y.coerceIn(1f, maxY)
            val ex = f.tx.coerceIn(1f, maxX)
            val ey = f.ty.coerceIn(1f, maxY)
            val path = Path()
            path.moveTo(sx, sy)
            if (ex != sx || ey != sy) path.lineTo(ex, ey)

            val willContinue = !f.up
            val prev = f.stroke
            val stroke = if (prev == null) {
                GestureDescription.StrokeDescription(path, 0L, TICK_MS, willContinue)
            } else {
                prev.continueStroke(path, 0L, TICK_MS, willContinue)
            }
            builder.addStroke(stroke)
            f.stroke = stroke
            f.x = ex; f.y = ey; f.tx = ex; f.ty = ey
            if (f.up) finished.add(slot)
            n++
        }

        for (slot in finished) {
            val f = fingers.remove(slot)
            val nx = f?.next
            if (nx != null) fingers[slot] = nx
        }

        busy = true
        val ok = svc.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                busy = false
                pump()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                busy = false
                resetStrokes()
                pump()
            }
        }, handler)
        if (!ok) {
            busy = false
            fingers.clear()
        }
    }

    /** Gesture dibatalkan sistem: semua jari yang masih aktif harus "turun lagi" dari awal. */
    private fun resetStrokes() {
        for ((slot, f) in fingers.entries.toList()) {
            if (f.up) {
                fingers.remove(slot)
                val nx = f.next
                if (nx != null) fingers[slot] = nx
            } else {
                f.stroke = null
            }
        }
    }

    companion object {
        private const val TICK_MS = 30L
    }
}
