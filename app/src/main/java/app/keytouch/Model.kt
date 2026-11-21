package app.keytouch

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

object Keys {
    const val MOUSE_LEFT = 10001
    const val MOUSE_RIGHT = 10002
    const val MOUSE_MIDDLE = 10003

    /** Tombol ` (backtick) untuk menyalakan / mematikan mode main. */
    const val TOGGLE = KeyEvent.KEYCODE_GRAVE

    val UP = setOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP)
    val LEFT = setOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT)
    val DOWN = setOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN)
    val RIGHT = setOf(KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT)

    fun isStick(code: Int) = code in UP || code in LEFT || code in DOWN || code in RIGHT

    fun label(code: Int): String = when (code) {
        0 -> "?"
        MOUSE_LEFT -> "LMB"
        MOUSE_RIGHT -> "RMB"
        MOUSE_MIDDLE -> "MMB"
        KeyEvent.KEYCODE_SPACE -> "SPC"
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "SHFT"
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "CTRL"
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "ALT"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").take(5)
    }
}

/** Satu tombol virtual. fx/fy = posisi tengah (0..1 dari lebar/tinggi layar), sizeDp = diameter. */
class Btn(var key: Int, var fx: Float, var fy: Float, var sizeDp: Float)

class Layout(
    val buttons: MutableList<Btn>,
    var stickFx: Float, var stickFy: Float, var stickDp: Float,   // stickDp = radius gerak analog
    var camFx: Float, var camFy: Float, var camDp: Float,         // titik awal jari kamera + radius
    var sens: Float                                               // sensitivitas mouse
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("keytouch", Context.MODE_PRIVATE)

    fun default() = Layout(
        mutableListOf(
            Btn(Keys.MOUSE_LEFT, 0.88f, 0.66f, 64f),
            Btn(KeyEvent.KEYCODE_SPACE, 0.94f, 0.46f, 52f),
            Btn(KeyEvent.KEYCODE_CTRL_LEFT, 0.94f, 0.84f, 46f),
            Btn(KeyEvent.KEYCODE_R, 0.80f, 0.84f, 46f),
            Btn(KeyEvent.KEYCODE_E, 0.72f, 0.70f, 46f)
        ),
        0.14f, 0.68f, 70f,
        0.62f, 0.40f, 110f,
        1.0f
    )

    fun load(c: Context): Layout {
        val s = prefs(c).getString("layout", null) ?: return default()
        return try {
            val o = JSONObject(s)
            val st = o.getJSONArray("stick")
            val cm = o.getJSONArray("cam")
            val ba = o.getJSONArray("btns")
            val list = mutableListOf<Btn>()
            for (i in 0 until ba.length()) {
                val b = ba.getJSONObject(i)
                list.add(Btn(b.getInt("k"), b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), b.getDouble("s").toFloat()))
            }
            Layout(
                list,
                st.getDouble(0).toFloat(), st.getDouble(1).toFloat(), st.getDouble(2).toFloat(),
                cm.getDouble(0).toFloat(), cm.getDouble(1).toFloat(), cm.getDouble(2).toFloat(),
                o.getDouble("sens").toFloat()
            )
        } catch (e: Exception) {
            default()
        }
    }

    fun save(c: Context, l: Layout) {
        val o = JSONObject()
        o.put("sens", l.sens.toDouble())
        o.put("stick", JSONArray(listOf(l.stickFx.toDouble(), l.stickFy.toDouble(), l.stickDp.toDouble())))
        o.put("cam", JSONArray(listOf(l.camFx.toDouble(), l.camFy.toDouble(), l.camDp.toDouble())))
        val ba = JSONArray()
        for (b in l.buttons) {
            ba.put(
                JSONObject().put("k", b.key).put("x", b.fx.toDouble())
                    .put("y", b.fy.toDouble()).put("s", b.sizeDp.toDouble())
            )
        }
        o.put("btns", ba)
        prefs(c).edit().putString("layout", o.toString()).apply()
    }
}
