package com.example.parallaxwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class SettingsActivity : Activity() {
    companion object { const val KEY_DEPTH = "depth_strength" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "3D Parallax Wallpaper"
            textSize = 23f
        }
        val description = TextView(this).apply {
            text = "Depth strength"
            textSize = 16f
            setPadding(0, 32, 0, 8)
        }
        val value = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
        }
        val seek = SeekBar(this).apply {
            max = 150 // 0..150 maps to 0.5x..2.0x
            progress = ((prefs.getFloat(KEY_DEPTH, 1f) - 0.5f) * 100f).toInt().coerceIn(0, 150)
        }
        fun updateLabel(progress: Int) {
            val multiplier = 0.5f + progress / 100f
            value.text = String.format(Locale.US, "%.1fx", multiplier)
        }
        updateLabel(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
                prefs.edit().putFloat(KEY_DEPTH, 0.5f + progress / 100f).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        val setWallpaper = Button(this).apply {
            text = "Set live wallpaper"
            setOnClickListener {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        android.content.ComponentName(this@SettingsActivity, ParallaxWallpaperService::class.java))
                })
            }
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(description)
        root.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(setWallpaper, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 28 })
        setContentView(root)
    }
}
