package com.example.parallaxwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    companion object { const val KEY_DEPTH = "depth_strength" }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun neonButton(label: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        isAllCaps = false
        textSize = 16f
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xFF140E24.toInt())
            setStroke(dp(2), color)
        }
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF07040F.toInt()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(0xFF07040F.toInt())
        }
        val full = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(TextView(this).apply {
            text = "NEON MECH"
            textSize = 28f
            setTextColor(0xFFFF4FD8.toInt())
            setShadowLayer(18f, 0f, 0f, 0xFFFF4FD8.toInt())
            letterSpacing = 0.15f
        })
        root.addView(TextView(this).apply {
            text = "赛博机械霓虹 · 3D动态壁纸 + 图标包"
            textSize = 14f
            setTextColor(0xFF9FE8FF.toInt())
            setPadding(0, dp(4), 0, dp(20))
        })

        // Wallpaper section
        val value = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE) }
        root.addView(TextView(this).apply {
            text = "景深强度"; textSize = 16f; setTextColor(0xFF9FE8FF.toInt())
        })
        root.addView(value)
        val seek = SeekBar(this).apply {
            max = 150 // 0..150 maps to 0.5x..2.0x
            progress = ((prefs.getFloat(KEY_DEPTH, 1f) - 0.5f) * 100f).toInt().coerceIn(0, 150)
        }
        fun updateLabel(p: Int) { value.text = String.format(Locale.US, "%.1fx", 0.5f + p / 100f) }
        updateLabel(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
                prefs.edit().putFloat(KEY_DEPTH, 0.5f + progress / 100f).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        root.addView(seek, full)
        root.addView(neonButton("设为动态壁纸", 0xFFFF4FD8.toInt()) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@SettingsActivity, ParallaxWallpaperService::class.java))
                })
            } catch (e: Exception) {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }, LinearLayout.LayoutParams(full).apply { topMargin = dp(12) })

        // Icon pack section
        root.addView(TextView(this).apply {
            text = "霓虹图标"
            textSize = 16f
            setTextColor(0xFF9FE8FF.toInt())
            setPadding(0, dp(28), 0, dp(10))
        })
        val icons = listOf("phone", "messages", "whatsapp", "camera", "gallery", "settings", "browser", "youtube",
            "playstore", "spotify", "instagram", "facebook", "maps", "gmail", "telegram", "tiktok")
        val grid = GridLayout(this).apply { columnCount = 4 }
        val size = (resources.displayMetrics.widthPixels - dp(44)) / 4
        for (name in icons) {
            val id = resources.getIdentifier("icon_$name", "drawable", packageName)
            if (id == 0) continue
            grid.addView(ImageView(this).apply {
                setImageResource(id)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = GridLayout.LayoutParams().apply { width = size; height = size }
            })
        }
        root.addView(grid)
        root.addView(neonButton("设 Neon 桌面为默认桌面（免费，自带霓虹图标）", 0xFF3FE0FF.toInt()) {
            try { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
            catch (e: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }, LinearLayout.LayoutParams(full).apply { topMargin = dp(12) })
        root.addView(neonButton("先预览 Neon 桌面", 0xFFB46CFF.toInt()) {
            startActivity(Intent(this, LauncherActivity::class.java))
        }, LinearLayout.LayoutParams(full).apply { topMargin = dp(10) })
        root.addView(neonButton("（可选）套用到 Nova Launcher", 0xFF555577.toInt()) { applyNova() },
            LinearLayout.LayoutParams(full).apply { topMargin = dp(10) })
        root.addView(TextView(this).apply {
            text = "用法：先在上面设为动态壁纸，再点\"设 Neon 桌面为默认桌面\"，在\"桌面/主屏幕应用\"里选 Neon 桌面。\n" +
                "• 所有App自动套霓虹图标，没专属图标的会加霓虹玻璃底框\n" +
                "• 长按App：应用信息 / 卸载；长按空白处：打开本设置\n" +
                "• 若找不到选项：设置 → 应用与权限 → 默认应用 → 桌面"
            textSize = 13f
            setTextColor(0xFFB8B0D0.toInt())
            setPadding(0, dp(14), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF07040F.toInt())
            addView(root)
        })
    }

    private fun applyNova() {
        val intent = Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME").apply {
            setPackage("com.teslacoilsw.launcher")
            putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
            putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "还没安装 Nova Launcher，帮你打开商店页面", Toast.LENGTH_SHORT).show()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.teslacoilsw.launcher")))
            } catch (e2: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.teslacoilsw.launcher")))
            }
        }
    }
}
