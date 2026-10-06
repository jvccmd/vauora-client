package com.vauora.client

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var logo: ImageView
    private var menu: View? = null
    private val tabs = listOf("Combat", "Visuals", "Performance")
    private val mods = listOf(
        listOf("CPS Counter", "Crosshair"),
        listOf("Screen Dimmer", "Clock HUD"),
        listOf("FPS Boost (soon)", "Motion Blur (soon)"))
    private val active = mutableMapOf<String, View>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clicks = ArrayDeque<Long>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun type() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private fun box(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("v", "Vauora", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "v")
            .setContentTitle("Vauora Client running").setSmallIcon(R.mipmap.ic_launcher).build())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addLogo()
        val prefs = getSharedPreferences("mods", MODE_PRIVATE)
        mods.flatten().filter { prefs.getBoolean(it, false) }.forEach { applyMod(it, true) }
    }

    private fun addLogo() {
        logo = ImageView(this).apply { setImageResource(R.drawable.logo); alpha = 0.9f }
        val p = WindowManager.LayoutParams(dp(56), dp(56), type(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START; p.x = dp(16); p.y = dp(120)
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        logo.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) { p.x = ox + dx.toInt(); p.y = oy + dy.toInt(); wm.updateViewLayout(logo, p) }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggleMenu()
            }
            true
        }
        wm.addView(logo, p)
    }

    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        val m = buildMenu(); menu = m
        val w = (resources.displayMetrics.widthPixels * 0.92).toInt()
        val p = WindowManager.LayoutParams(w, dp(320), type(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.CENTER
        wm.addView(m, p)
    }

    private fun closeMenu() { menu?.let { wm.removeView(it) }; menu = null }

    private fun buildMenu(): View {
        val prefs = getSharedPreferences("mods", MODE_PRIVATE)
        val root = FrameLayout(this).apply { background = box(Color.parseColor("#17161C"), 16) }
        val body = LinearLayout(this)
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121116")); setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        val content = FrameLayout(this).apply { setPadding(dp(14), dp(14), dp(14), dp(14)) }
        val tabViews = mutableListOf<TextView>()

        fun select(i: Int) {
            tabViews.forEachIndexed { j, t ->
                t.background = box(if (j == i) Color.parseColor("#4A4950") else Color.TRANSPARENT, 10)
            }
            content.removeAllViews()
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            list.addView(TextView(this).apply { text = tabs[i]; textSize = 18f; setTextColor(Color.WHITE) })
            mods[i].forEach { name ->
                list.addView(Switch(this).apply {
                    text = name; setTextColor(Color.WHITE); setPadding(0, dp(10), 0, dp(10))
                    if (name.endsWith("(soon)")) { isEnabled = false; alpha = 0.5f }
                    else {
                        isChecked = prefs.getBoolean(name, false)
                        setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(name, on).apply(); applyMod(name, on) }
                    }
                })
            }
            content.addView(ScrollView(this).apply { addView(list) })
        }

        side.addView(ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) })
        tabs.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 13f; setTextColor(Color.WHITE); setPadding(dp(8), dp(10), dp(8), dp(10))
                maxLines = 1; setOnClickListener { select(i) }
            }
            tabViews.add(t); side.addView(t)
        }
        body.addView(side, LinearLayout.LayoutParams(dp(110), -1))
        body.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(this).apply {
            text = "✕"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setOnClickListener { closeMenu() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END))
        select(0)
        return root
    }

    private fun addOverlay(key: String, v: View, w: Int, h: Int, flags: Int, g: Int, x: Int = 0, y: Int = 0) {
        val p = WindowManager.LayoutParams(w, h, type(), flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        p.gravity = g; p.x = x; p.y = y
        wm.addView(v, p); active[key] = v
    }

    private fun applyMod(name: String, on: Boolean) {
        if (!on) {
            active.keys.filter { it.startsWith(name) }.forEach { k ->
                active.remove(k)?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            }
            return
        }
        if (active.containsKey(name)) return
        val ghost = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val hud = { t: String -> TextView(this).apply {
            text = t; textSize = 16f; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4)); background = box(Color.parseColor("#99000000"), 8) } }
        when (name) {
            "Crosshair" -> addOverlay(name, hud("+").apply { background = null; textSize = 28f },
                -2, -2, ghost, Gravity.CENTER)
            "Screen Dimmer" -> addOverlay(name, View(this).apply { setBackgroundColor(Color.parseColor("#55000000")) },
                -1, -1, ghost, Gravity.TOP or Gravity.START)
            "Clock HUD" -> addOverlay(name, android.widget.TextClock(this).apply {
                format12Hour = "h:mm a"; format24Hour = "HH:mm"; textSize = 16f; setTextColor(Color.WHITE)
                setPadding(dp(8), dp(4), dp(8), dp(4)); background = box(Color.parseColor("#99000000"), 8) },
                -2, -2, ghost, Gravity.TOP or Gravity.END, dp(60), dp(8))
            "CPS Counter" -> {
                val tv = hud("CPS: 0")
                addOverlay("CPS Counter#text", tv, -2, -2, ghost, Gravity.TOP or Gravity.START, dp(80), dp(8))
                val catcher = View(this)
                catcher.setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_OUTSIDE) clicks.addLast(System.currentTimeMillis()); false }
                addOverlay(name, catcher, 1, 1, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, Gravity.TOP or Gravity.START)
                handler.post(object : Runnable { override fun run() {
                    if (!active.containsKey(name)) return
                    val now = System.currentTimeMillis()
                    while (clicks.isNotEmpty() && now - clicks.first() > 1000) clicks.removeFirst()
                    tv.text = "CPS: ${clicks.size}"; handler.postDelayed(this, 150) } })
            }
        }
    }

    override fun onDestroy() {
        closeMenu(); mods.flatten().forEach { applyMod(it, false) }
        wm.removeView(logo); super.onDestroy() }
}
