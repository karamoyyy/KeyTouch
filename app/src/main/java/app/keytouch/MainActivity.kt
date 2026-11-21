package app.keytouch

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20f).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        fun text(t: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, pad / 3, 0, pad / 3)
        }

        fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        }

        col.addView(text("KeyTouch", 28f, true))
        col.addView(text("Main game di HP pakai keyboard & mouse, tanpa Shizuku / root.", 14f))
        status = text("", 15f, true)
        col.addView(status)

        col.addView(button("1. Aktifkan layanan aksesibilitas KeyTouch") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(button("2. (Opsional) Izin tampil di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val sensLabel = text("", 14f)
        val l0 = Store.load(this)
        sensLabel.text = "Sensitivitas mouse: ${"%.1f".format(l0.sens)}x"
        col.addView(sensLabel)
        col.addView(SeekBar(this).apply {
            max = 290
            progress = (Math.round(l0.sens * 100) - 10).coerceIn(0, 290)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    sensLabel.text = "Sensitivitas mouse: ${"%.1f".format((p + 10) / 100f)}x"
                }

                override fun onStartTrackingTouch(s: SeekBar) {}

                override fun onStopTrackingTouch(s: SeekBar) {
                    val l = Store.load(this@MainActivity)
                    l.sens = (s.progress + 10) / 100f
                    Store.save(this@MainActivity, l)
                }
            })
        })

        col.addView(button("3. Atur tata letak tombol & analog") {
            val svc = GameMapperService.instance
            if (svc == null) {
                Toast.makeText(this, "Aktifkan dulu layanan aksesibilitasnya.", Toast.LENGTH_LONG).show()
            } else {
                svc.setMode(GameMapperService.Mode.EDIT)
                Toast.makeText(this, "Buka game-nya, lalu geser tombol ke posisi kontrol game.", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(button("Kembalikan tata letak bawaan") {
            Store.save(this, Store.default())
            Toast.makeText(this, "Tata letak direset.", Toast.LENGTH_SHORT).show()
        })

        col.addView(text(
            "Cara pakai\n" +
                "• Sambungkan keyboard & mouse (OTG / Bluetooth).\n" +
                "• Buka game, ketuk ATUR di chip mengambang, geser WASD / MOUSE / tombol ke posisi kontrol aslinya di game, lalu Simpan & Main.\n" +
                "• Tekan ` (backtick) kapan saja untuk MAIN / STOP.\n" +
                "• Saat MAIN: W A S D (atau panah) = analog, mouse = kamera, tombol lain sesuai yang kamu atur.",
            13f
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101820"))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        val on = serviceEnabled()
        status.text = if (on) "Status: layanan aktif ✔" else "Status: layanan belum aktif"
        status.setTextColor(Color.parseColor(if (on) "#7CFC9A" else "#FFC857"))
    }

    private fun serviceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, GameMapperService::class.java).flattenToString()
        return flat.split(':').any { it.equals(me, ignoreCase = true) }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
