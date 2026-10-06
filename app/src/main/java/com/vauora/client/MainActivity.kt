package com.vauora.client

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121118")); setPadding(40, 40, 40, 40)
        }
        root.addView(ImageView(this).apply { setImageResource(R.drawable.logo) },
            LinearLayout.LayoutParams((120 * d).toInt(), (120 * d).toInt()))
        root.addView(TextView(this).apply {
            text = "Vauora Client"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        })
        val ver = try { packageManager.getPackageInfo("com.mojang.minecraftpe", 0).versionName } catch (e: Exception) { null }
        root.addView(TextView(this).apply {
            text = if (ver == null) "Minecraft not installed" else "Minecraft $ver detected"
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20)
        })
        root.addView(Button(this).apply {
            text = "Launch Minecraft"; setOnClickListener { launch() }
        })
        setContentView(root)
    }

    private fun launch() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then press Launch again", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mc = packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
        if (mc == null) { Toast.makeText(this, "Minecraft is not installed", Toast.LENGTH_LONG).show(); return }
        startForegroundService(Intent(this, OverlayService::class.java))
        startActivity(mc)
    }
}
