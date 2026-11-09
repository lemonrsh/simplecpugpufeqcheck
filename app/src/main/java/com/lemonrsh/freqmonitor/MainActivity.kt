package com.lemonrsh.freqmonitor

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(11, 16, 32)
    private val card = Color.rgb(22, 30, 51)
    private val muted = Color.rgb(151, 163, 190)
    private val white = Color.rgb(241, 245, 255)
    private val blue = Color.rgb(111, 145, 255)
    private val green = Color.rgb(82, 214, 163)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var cpuValues: TextView
    private lateinit var gpuValues: TextView
    private lateinit var status: TextView
    private lateinit var refresh: TextView
    @Volatile private var busy = false

    private val poll = object : Runnable {
        override fun run() { readStats(); handler.postDelayed(this, 1500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
        handler.post(poll)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(22), dp(22), dp(22), dp(18)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        titleCol.addView(text("FREQ MONITOR", 12, blue, true))
        titleCol.addView(text("System overview", 25, white, true).apply { setPadding(0, dp(5), 0, 0) })
        top.addView(titleCol)
        refresh = text("↻", 27, white, true).apply {
            gravity = Gravity.CENTER; background = rounded(card, 18); setPadding(dp(15), dp(8), dp(15), dp(8)); setOnClickListener { readStats() }
        }
        top.addView(refresh)
        content.addView(top)
        status = text("Waiting for root access…", 12, muted, false).apply { setPadding(0, dp(13), 0, dp(20)) }
        content.addView(status)
        content.addView(section("CPU", "PROCESSOR", blue).also { cpuValues = it.second }.first)
        content.addView(space(15))
        content.addView(section("GPU", "MALI GRAPHICS", green).also { gpuValues = it.second }.first)
        content.addView(space(18))
        content.addView(text("Live readings · refreshes every 1.5 seconds", 11, muted, false))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun section(name: String, sub: String, accent: Int): Pair<View, TextView> {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(card, 22); setPadding(dp(18), dp(18), dp(18), dp(18)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = View(this).apply { background = rounded(accent, 50) }
        head.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)))
        head.addView(text("  $name", 17, white, true))
        val subText = text(sub, 10, muted, true).apply { gravity = Gravity.RIGHT }
        head.addView(subText, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(head)
        val vals = TextView(this).apply { text = "Current     — MHz\nMaximum   — MHz\nMinimum    — MHz"; setTextColor(white); textSize = 15f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setLineSpacing(dp(9).toFloat(), 1f); setPadding(0, dp(20), 0, dp(5)) }
        box.addView(vals)
        return Pair(box, vals)
    }

    private fun readStats() {
        if (busy) return
        busy = true
        status.text = "Reading kernel frequency data…"
        executor.execute {
            val result = try {
                val p = ProcessBuilder("su", "-c", ROOT_SCRIPT).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                val code = p.waitFor()
                if (code == 0) parse(out) else Stats(error = "Root command failed ($code)")
            } catch (e: Exception) { Stats(error = "Root unavailable: ${e.message ?: "permission denied"}") }
            runOnUiThread {
                busy = false
                if (result.error != null) {
                    status.text = result.error
                } else {
                    cpuValues.text = format(result.cpuCurrent, result.cpuMax, result.cpuMin)
                    gpuValues.text = format(result.gpuCurrent, result.gpuMax, result.gpuMin)
                    status.text = "● ROOT CONNECTED     ·     ${result.source}"
                    status.setTextColor(green)
                }
            }
        }
    }

    private data class Stats(val cpuCurrent: Long = 0, val cpuMax: Long = 0, val cpuMin: Long = 0,
                             val gpuCurrent: Long = 0, val gpuMax: Long = 0, val gpuMin: Long = 0,
                             val source: String = "KERNEL", val error: String? = null)

    private fun parse(s: String): Stats {
        val m = s.lineSequence().mapNotNull { line -> val i = line.indexOf('='); if (i > 0) line.substring(0, i) to line.substring(i + 1).trim().toLongOrNull() else null }.toMap()
        return Stats(m["CPU_CUR"] ?: 0, m["CPU_MAX"] ?: 0, m["CPU_MIN"] ?: 0,
            m["GPU_CUR"] ?: 0, m["GPU_MAX"] ?: 0, m["GPU_MIN"] ?: 0,
            if ((m["GPU_MAX"] ?: 0) > 0) "MTK GPUFREQ V2" else "SYSFS")
    }

    private fun format(cur: Long, max: Long, min: Long): String {
        fun v(n: Long) = if (n > 0) String.format(Locale.US, "%,d MHz", n / 1000) else "Not available"
        return "Current     ${v(cur)}\nMaximum   ${v(max)}\nMinimum    ${v(min)}"
    }

    override fun onDestroy() { handler.removeCallbacks(poll); executor.shutdownNow(); super.onDestroy() }
    private fun text(s: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = s; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    companion object {
        private val ROOT_SCRIPT = """
            CPU_CUR=0; CPU_MAX=0; CPU_MIN=999999999
            for f in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do
              [ -r "${'$'}f/scaling_cur_freq" ] || continue
              c=${'$'}(cat "${'$'}f/scaling_cur_freq" 2>/dev/null); x=${'$'}(cat "${'$'}f/cpuinfo_max_freq" 2>/dev/null); n=${'$'}(cat "${'$'}f/cpuinfo_min_freq" 2>/dev/null)
              case ${'$'}c in ''|*[!0-9]*) continue;; esac
              [ "${'$'}c" -gt "${'$'}CPU_CUR" ] && CPU_CUR=${'$'}c
              case ${'$'}x in ''|*[!0-9]*) :;; *) [ "${'$'}x" -gt "${'$'}CPU_MAX" ] && CPU_MAX=${'$'}x;; esac
              case ${'$'}n in ''|*[!0-9]*) :;; *) [ "${'$'}n" -lt "${'$'}CPU_MIN" ] && CPU_MIN=${'$'}n;; esac
            done
            [ "${'$'}CPU_MIN" = 999999999 ] && CPU_MIN=0
            GPU_CUR=${'$'}(sed -n 's/.*\\[GPU-OPP\\].*Freq:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p' /proc/gpufreqv2/gpufreq_status 2>/dev/null | head -n1)
            [ -n "${'$'}GPU_CUR" ] || GPU_CUR=${'$'}(cat /sys/kernel/thermal/gpu_freq 2>/dev/null)
            OPP=/proc/gpufreqv2/gpu_working_opp_table
            GPU_MAX=0; GPU_MIN=999999999
            if [ -r "${'$'}OPP" ]; then
              for n in ${'$'}(sed -n 's/.*freq:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p' "${'$'}OPP"); do
                [ "${'$'}n" -gt "${'$'}GPU_MAX" ] && GPU_MAX=${'$'}n
                [ "${'$'}n" -lt "${'$'}GPU_MIN" ] && GPU_MIN=${'$'}n
              done
            fi
            [ "${'$'}GPU_MIN" = 999999999 ] && GPU_MIN=0
            echo "CPU_CUR=${'$'}CPU_CUR"; echo "CPU_MAX=${'$'}CPU_MAX"; echo "CPU_MIN=${'$'}CPU_MIN"
            echo "GPU_CUR=${'$'}GPU_CUR"; echo "GPU_MAX=${'$'}GPU_MAX"; echo "GPU_MIN=${'$'}GPU_MIN"
        """.trimIndent()
    }
}
