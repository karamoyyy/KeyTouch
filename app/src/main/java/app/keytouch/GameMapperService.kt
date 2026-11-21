package app.keytouch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot

class GameMapperService : AccessibilityService(), CaptureView.Host {

    enum class Mode { OFF, PLAY, EDIT }

    companion object {
        @Volatile
        var instance: GameMapperService? = null
        private const val SLOT_STICK = 1
        private const val SLOT_CAM = 2
        private const val SLOT_BTN = 100
    }

    private lateinit var wm: WindowManager
    private lateinit var inj: TouchInjector
    private lateinit var ui: OverlayUi
    private val handler = Handler(Looper.getMainLooper())

    lateinit var layout: Layout
    var mode = Mode.OFF
        private set
    var bindCb: ((Int) -> Unit)? = null

    private var capture: CaptureView? = null
    private val pressed = HashSet<Int>()

    // analog WASD
    private var stickX = 0f
    private var stickY = 0f
    private var stickTx = 0f
    private var stickTy = 0f
    private var stickDown = false

    // kamera (mouse)
    private var camDown = false
    private var camX = 0f
    private var camY = 0f

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        inj = TouchInjector(this)
        layout = Store.load(this)
        ui = OverlayUi(this, wm)
        ui.showChip()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopAll()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAll()
        removeCapture()
        if (::ui.isInitialized) ui.hideAll()
        instance = null
        return super.onUnbind(intent)
    }

    // ------------------------------------------------------------------ mode

    fun setMode(m: Mode) {
        if (m == mode) return
        stopAll()
        if (mode == Mode.EDIT) Store.save(this, layout)
        mode = m
        removeCapture()
        when (m) {
            Mode.OFF -> ui.hideLayout()
            Mode.EDIT -> {
                layout = Store.load(this)
                ui.showLayout(true)
            }
            Mode.PLAY -> {
                layout = Store.load(this)
                ui.showLayout(false)
                addCapture(false)
            }
        }
        ui.updateChip(m)
    }

    private fun stopAll() {
        handler.removeCallbacks(stickLoop)
        handler.removeCallbacks(camIdle)
        stickX = 0f; stickY = 0f; stickTx = 0f; stickTy = 0f
        stickDown = false
        camDown = false
        pressed.clear()
        bindCb = null
        if (::inj.isInitialized) inj.releaseAll()
        if (::ui.isInitialized) ui.setKnob(0f, 0f)
    }

    // ------------------------------------------------------------------ mouse capture window

    private fun addCapture(useAppOverlay: Boolean) {
        val type = if (useAppOverlay) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        // Sengaja TANPA FLAG_NOT_FOCUSABLE: window harus fokus agar bisa memegang pointer capture.
        // FLAG_NOT_TOUCHABLE: sentuhan (termasuk gesture yang kita suntik) tetap tembus ke game.
        val lp = WindowManager.LayoutParams(
            1, 1, type, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val v = CaptureView(this, this)
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuat penangkap mouse: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        capture = v
        handler.postDelayed({
            val c = capture
            if (c != null && c === v && mode == Mode.PLAY && !c.hasWindowFocus()) {
                if (!useAppOverlay && Settings.canDrawOverlays(this)) {
                    removeCapture()
                    addCapture(true)
                } else {
                    Toast.makeText(
                        this,
                        "Mouse kamera belum aktif. Izinkan 'Tampil di atas aplikasi lain' lewat aplikasi KeyTouch.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, 700)
    }

    private fun removeCapture() {
        capture?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        capture = null
    }

    // ------------------------------------------------------------------ keyboard

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        val isDown = e.action == KeyEvent.ACTION_DOWN

        if (code == Keys.TOGGLE) {
            if (isDown && e.repeatCount == 0) setMode(if (mode == Mode.PLAY) Mode.OFF else Mode.PLAY)
            return true
        }
        val cb = bindCb
        if (cb != null) {
            if (isDown && e.repeatCount == 0) {
                bindCb = null
                cb(code)
            }
            return true
        }
        if (mode != Mode.PLAY) return false

        val mapped = Keys.isStick(code) || layout.buttons.any { it.key == code }
        if (!mapped) return false
        if (e.repeatCount > 0) return true
        onKey(code, isDown)
        return true
    }

    private fun onKey(code: Int, isDown: Boolean) {
        if (isDown) pressed.add(code) else pressed.remove(code)
        if (Keys.isStick(code)) {
            updateStick()
            return
        }
        val s = screenSize()
        layout.buttons.forEachIndexed { i, b ->
            if (b.key == code) {
                if (isDown) inj.down(SLOT_BTN + i, b.fx * s.x, b.fy * s.y) else inj.up(SLOT_BTN + i)
                ui.setPressed(i, isDown)
            }
        }
    }

    // ------------------------------------------------------------------ analog WASD (halus)

    private fun updateStick() {
        var x = 0f
        var y = 0f
        if (pressed.any { it in Keys.RIGHT }) x += 1f
        if (pressed.any { it in Keys.LEFT }) x -= 1f
        if (pressed.any { it in Keys.DOWN }) y += 1f
        if (pressed.any { it in Keys.UP }) y -= 1f
        val len = hypot(x, y)
        if (len > 0f) { x /= len; y /= len }   // diagonal tidak lebih jauh dari lurus
        stickTx = x
        stickTy = y
        handler.removeCallbacks(stickLoop)
        stickLoop.run()
    }

    private val stickLoop = object : Runnable {
        override fun run() {
            // easing: knob bergerak mulus ke arah target, dan kembali ke tengah saat dilepas
            stickX += (stickTx - stickX) * 0.35f
            stickY += (stickTy - stickY) * 0.35f
            val released = stickTx == 0f && stickTy == 0f
            if (released && abs(stickX) < 0.04f && abs(stickY) < 0.04f) {
                stickX = 0f; stickY = 0f
                if (stickDown) {
                    inj.up(SLOT_STICK)
                    stickDown = false
                }
                ui.setKnob(0f, 0f)
                return
            }
            val s = screenSize()
            val cx = layout.stickFx * s.x
            val cy = layout.stickFy * s.y
            val r = dp(layout.stickDp)
            if (!stickDown) {
                inj.down(SLOT_STICK, cx, cy)
                stickDown = true
            }
            inj.move(SLOT_STICK, cx + stickX * r, cy + stickY * r)
            ui.setKnob(stickX, stickY)
            handler.postDelayed(this, 16)
        }
    }

    // ------------------------------------------------------------------ mouse -> kamera

    override fun onMouseDelta(dx: Float, dy: Float) {
        if (mode != Mode.PLAY) return
        val s = screenSize()
        val ax = layout.camFx * s.x
        val ay = layout.camFy * s.y
        val r = dp(layout.camDp)
        if (!camDown) {
            camX = ax
            camY = ay
            inj.down(SLOT_CAM, ax, ay)
            camDown = true
        }
        camX += dx * layout.sens
        camY += dy * layout.sens
        if (hypot(camX - ax, camY - ay) > r) {
            // keluar zona: angkat jari, taruh lagi di titik awal (seperti mengangkat mouse)
            camX = ax
            camY = ay
            inj.down(SLOT_CAM, ax, ay)
        }
        inj.move(SLOT_CAM, camX, camY)
        handler.removeCallbacks(camIdle)
        handler.postDelayed(camIdle, 150)
    }

    private val camIdle = Runnable {
        if (camDown) {
            inj.up(SLOT_CAM)
            camDown = false
        }
    }

    override fun onMouseButton(code: Int, down: Boolean) {
        if (mode == Mode.PLAY) onKey(code, down)
    }
}
