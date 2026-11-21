package app.keytouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Widget yang digambar di atas layar game: analog WASD, zona kamera, dan tombol. */
class WidgetView(ctx: Context, val kind: Int, val index: Int) : View(ctx) {
    companion object {
        const val STICK = 0
        const val CAM = 1
        const val BTN = 2
    }

    var label = ""
    var selectedW = false
    var active = false
    var knobX = 0f
    var knobY = 0f
    var editable = false
    var onSelect: ((WidgetView) -> Unit)? = null
    var onMoved: ((WidgetView) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var dx = 0f
    private var dy = 0f

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 4f
        line.color = Color.parseColor(if (selectedW) "#FFFFC857" else "#FF4FC3F7")
        when (kind) {
            STICK -> {
                fill.color = Color.parseColor("#55000000")
                c.drawCircle(cx, cy, r, fill)
                c.drawCircle(cx, cy, r, line)
                txt.textSize = r * 0.26f
                val off = r * 0.72f
                val fm = txt.textSize / 3f
                c.drawText("W", cx, cy - off + fm, txt)
                c.drawText("S", cx, cy + off + fm, txt)
                c.drawText("A", cx - off, cy + fm, txt)
                c.drawText("D", cx + off, cy + fm, txt)
                val kr = r * 0.3f
                val tr = r - kr
                fill.color = Color.parseColor("#BBFFFFFF")
                c.drawCircle(cx + knobX * tr, cy + knobY * tr, kr, fill)
            }
            CAM -> {
                fill.color = Color.parseColor("#22FFFFFF")
                c.drawCircle(cx, cy, r, fill)
                c.drawCircle(cx, cy, r, line)
                c.drawLine(cx - r * 0.25f, cy, cx + r * 0.25f, cy, line)
                c.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, line)
                txt.textSize = r * 0.16f
                c.drawText("MOUSE / KAMERA", cx, cy + r * 0.55f, txt)
            }
            else -> {
                fill.color = Color.parseColor(if (active) "#CCFFC857" else "#66000000")
                c.drawCircle(cx, cy, r, fill)
                c.drawCircle(cx, cy, r, line)
                txt.textSize = r * 0.55f
                c.drawText(label, cx, cy + txt.textSize / 3f, txt)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return false
        val p = parent as? View ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dx = x - e.rawX
                dy = y - e.rawY
                onSelect?.invoke(this)
            }
            MotionEvent.ACTION_MOVE -> {
                x = (e.rawX + dx).coerceIn(-width / 2f, p.width - width / 2f)
                y = (e.rawY + dy).coerceIn(-height / 2f, p.height - height / 2f)
            }
            MotionEvent.ACTION_UP -> onMoved?.invoke(this)
        }
        return true
    }
}

class OverlayUi(private val svc: GameMapperService, private val wm: WindowManager) {
    private val ctx: Context = svc
    private var chip: LinearLayout? = null
    private lateinit var playBtn: TextView
    private var root: FrameLayout? = null
    private var toolbar: LinearLayout? = null
    private var bindBar: LinearLayout? = null
    private val widgets = ArrayList<WidgetView>()
    private var editable = false
    private var selKind = -1
    private var selIdx = -1

    private fun d(v: Float) = ctx.dp(v).toInt()

    private fun round(color: String, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = ctx.dp(radiusDp)
        }

    private fun tv(text: String, onClick: (() -> Unit)? = null): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(d(12f), d(8f), d(12f), d(8f))
            if (onClick != null) setOnClickListener { onClick() }
        }

    private fun overlayParams(w: Int, h: Int, flags: Int) =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)

    // ---------------------------------------------------------------- chip mengambang

    fun showChip() {
        val lp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = d(12f)
        lp.y = d(80f)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round("#E6101820", 22f)
            setPadding(d(4f), d(2f), d(4f), d(2f))
        }
        val handle = tv("≡")
        playBtn = tv("MAIN") {
            svc.setMode(if (svc.mode == GameMapperService.Mode.PLAY) GameMapperService.Mode.OFF else GameMapperService.Mode.PLAY)
        }
        val edit = tv("ATUR") {
            svc.setMode(if (svc.mode == GameMapperService.Mode.EDIT) GameMapperService.Mode.OFF else GameMapperService.Mode.EDIT)
        }
        row.addView(handle)
        row.addView(playBtn)
        row.addView(edit)

        handle.setOnTouchListener(object : View.OnTouchListener {
            var ox = 0
            var oy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { ox = lp.x; oy = lp.y; tx = e.rawX; ty = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = ox + (e.rawX - tx).toInt()
                        lp.y = oy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(row, lp)
                    }
                }
                return true
            }
        })
        wm.addView(row, lp)
        chip = row
    }

    fun updateChip(m: GameMapperService.Mode) {
        playBtn.text = if (m == GameMapperService.Mode.PLAY) "STOP" else "MAIN"
        playBtn.background = if (m == GameMapperService.Mode.PLAY) round("#FF2E7D32", 16f) else null
    }

    // ---------------------------------------------------------------- layar overlay

    fun showLayout(edit: Boolean) {
        hideLayout()
        editable = edit
        selKind = -1
        selIdx = -1
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!edit) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, flags)
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val r = FrameLayout(ctx)
        wm.addView(r, lp)
        root = r
        if (edit) buildToolbar(r)
        rebuild()
    }

    fun hideLayout() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        toolbar = null
        bindBar = null
        widgets.clear()
    }

    fun hideAll() {
        hideLayout()
        chip?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        chip = null
    }

    private fun rebuild() {
        val r = root ?: return
        for (w in widgets) r.removeView(w)
        widgets.clear()
        val s = ctx.screenSize()
        val l = svc.layout

        fun add(kind: Int, idx: Int, fx: Float, fy: Float, sizePx: Int, label: String) {
            val v = WidgetView(ctx, kind, idx)
            v.label = label
            v.editable = editable
            v.alpha = if (editable) 1f else 0.45f
            v.selectedW = kind == selKind && idx == selIdx
            r.addView(v, FrameLayout.LayoutParams(sizePx, sizePx))
            v.x = fx * s.x - sizePx / 2f
            v.y = fy * s.y - sizePx / 2f
            if (editable) {
                v.onSelect = { w ->
                    selKind = w.kind
                    selIdx = w.index
                    for (o in widgets) { o.selectedW = o === w; o.invalidate() }
                }
                v.onMoved = { w ->
                    val nfx = ((w.x + w.width / 2f) / s.x).coerceIn(0f, 1f)
                    val nfy = ((w.y + w.height / 2f) / s.y).coerceIn(0f, 1f)
                    when (w.kind) {
                        WidgetView.STICK -> { l.stickFx = nfx; l.stickFy = nfy }
                        WidgetView.CAM -> { l.camFx = nfx; l.camFy = nfy }
                        else -> l.buttons.getOrNull(w.index)?.let { it.fx = nfx; it.fy = nfy }
                    }
                }
            }
            widgets.add(v)
        }

        add(WidgetView.STICK, 0, l.stickFx, l.stickFy, (ctx.dp(l.stickDp) * 2).toInt(), "")
        add(WidgetView.CAM, 0, l.camFx, l.camFy, (ctx.dp(l.camDp) * 2).toInt(), "")
        l.buttons.forEachIndexed { i, b -> add(WidgetView.BTN, i, b.fx, b.fy, d(b.sizeDp), Keys.label(b.key)) }
        toolbar?.bringToFront()
        bindBar?.bringToFront()
    }

    // ---------------------------------------------------------------- toolbar edit

    private fun buildToolbar(r: FrameLayout) {
        val tb = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round("#EE101820", 14f)
            setPadding(d(6f), d(6f), d(6f), d(6f))
        }
        fun b(t: String, f: () -> Unit) {
            val v = tv(t, f)
            v.background = round("#334FC3F7", 10f)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(d(3f), 0, d(3f), 0)
            tb.addView(v, p)
        }
        b("+ Tombol") { addButton() }
        b("Ganti Tombol") { bindSelected() }
        b("Kecil") { resize(0.9f) }
        b("Besar") { resize(1.1f) }
        b("Hapus") { deleteSelected() }
        b("Simpan & Main") { svc.setMode(GameMapperService.Mode.PLAY) }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = d(8f)
        r.addView(tb, lp)
        toolbar = tb
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun addButton() {
        svc.layout.buttons.add(Btn(0, 0.5f, 0.5f, 48f))
        selKind = WidgetView.BTN
        selIdx = svc.layout.buttons.size - 1
        rebuild()
        bindSelected()
    }

    private fun deleteSelected() {
        if (selKind != WidgetView.BTN) { toast("Pilih tombol dulu (ketuk tombolnya)"); return }
        svc.layout.buttons.removeAt(selIdx)
        selKind = -1
        selIdx = -1
        rebuild()
    }

    private fun resize(f: Float) {
        val l = svc.layout
        when (selKind) {
            WidgetView.STICK -> l.stickDp = (l.stickDp * f).coerceIn(30f, 200f)
            WidgetView.CAM -> l.camDp = (l.camDp * f).coerceIn(40f, 260f)
            WidgetView.BTN -> l.buttons.getOrNull(selIdx)?.let { it.sizeDp = (it.sizeDp * f).coerceIn(24f, 120f) }
            else -> { toast("Ketuk dulu widget yang mau diubah ukurannya"); return }
        }
        rebuild()
    }

    private fun bindSelected() {
        if (selKind != WidgetView.BTN) { toast("Pilih tombol dulu (ketuk tombolnya)"); return }
        val idx = selIdx
        val r = root ?: return
        toolbar?.visibility = View.GONE

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = round("#EE101820", 14f)
            setPadding(d(6f), d(6f), d(6f), d(6f))
        }
        fun finish(code: Int?) {
            svc.bindCb = null
            r.removeView(bar)
            bindBar = null
            toolbar?.visibility = View.VISIBLE
            if (code != null) svc.layout.buttons.getOrNull(idx)?.key = code
            rebuild()
        }
        bar.addView(tv("Tekan tombol keyboard…"))
        for ((name, code) in listOf("LMB" to Keys.MOUSE_LEFT, "RMB" to Keys.MOUSE_RIGHT, "MMB" to Keys.MOUSE_MIDDLE)) {
            val v = tv(name) { finish(code) }
            v.background = round("#334FC3F7", 10f)
            bar.addView(v)
        }
        val cancel = tv("Batal") { finish(null) }
        cancel.background = round("#33FF5252", 10f)
        bar.addView(cancel)

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = d(8f)
        r.addView(bar, lp)
        bindBar = bar
        svc.bindCb = { code -> finish(code) }
    }

    // ---------------------------------------------------------------- umpan balik visual saat main

    fun setKnob(x: Float, y: Float) {
        for (w in widgets) if (w.kind == WidgetView.STICK) { w.knobX = x; w.knobY = y; w.invalidate() }
    }

    fun setPressed(btnIndex: Int, down: Boolean) {
        for (w in widgets) if (w.kind == WidgetView.BTN && w.index == btnIndex) { w.active = down; w.invalidate() }
    }
}
