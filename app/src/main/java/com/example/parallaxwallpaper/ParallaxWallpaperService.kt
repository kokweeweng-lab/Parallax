package com.example.parallaxwallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import kotlin.math.max
import kotlin.math.min

class ParallaxWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ParallaxEngine()

    inner class ParallaxEngine : Engine(), SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        private val rotationMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        private var targetX = 0f
        private var targetY = 0f
        private var smoothX = 0f
        private var smoothY = 0f
        private var screenW = 1
        private var screenH = 1
        private var visible = false
        private var lastFrame = 0L

        private var background: Bitmap? = null
        private var glow: Bitmap? = null
        private var foreground: Bitmap? = null

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(22f, 0f, 10f, 0x99000000.toInt())
        }

        private val drawFrame = object : Runnable {
            override fun run() {
                if (!visible) return
                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1L, 40L).toFloat()
                lastFrame = now
                // Frame-rate-independent low-pass smoothing; larger tau feels softer.
                val alpha = 1f - kotlin.math.exp(-dt / 115f)
                smoothX += (targetX - smoothX) * alpha
                smoothY += (targetY - smoothY) * alpha
                drawWallpaperFrame(now)
                handler.postDelayed(this, 16L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            background = BitmapFactory.decodeResource(resources, R.drawable.background)
            glow = BitmapFactory.decodeResource(resources, R.drawable.glow)
            foreground = BitmapFactory.decodeResource(resources, R.drawable.foreground)
            setTouchEventsEnabled(false)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            screenW = width.coerceAtLeast(1)
            screenH = height.coerceAtLeast(1)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                lastFrame = 0L
                rotationSensor?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
                }
                handler.removeCallbacks(drawFrame)
                handler.post(drawFrame)
            } else {
                handler.removeCallbacks(drawFrame)
                sensorManager.unregisterListener(this)
                lastFrame = 0L
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            // Orientation: azimuth, pitch, roll (radians). Clamp to avoid extreme image travel.
            targetX = (orientation[2] / 0.55f).coerceIn(-1f, 1f)
            targetY = (orientation[1] / 0.55f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun drawWallpaperFrame(timeMs: Long) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    val strength = PreferenceManager.getDefaultSharedPreferences(this@ParallaxWallpaperService)
                        .getFloat(SettingsActivity.KEY_DEPTH, 1f).coerceIn(0.5f, 2f)
                    val x = smoothX * screenW * strength
                    val y = smoothY * screenH * strength

                    // Background and foreground intentionally move in opposite directions.
                    drawLayer(canvas, background, x * 0.05f, y * 0.05f, backgroundPaint)
                    val pulse = (0.5 + 0.5 * kotlin.math.sin(
                        2.0 * Math.PI * (timeMs % 4000L) / 4000.0
                    )).toFloat()
                    // 70% to 100% opacity over a 4-second breathing cycle.
                    glowPaint.alpha = (179 + 76 * pulse).toInt().coerceIn(179, 255)
                    drawLayer(canvas, glow, -x * 0.12f, -y * 0.12f, glowPaint)

                    // Shadow follows the foreground offset, creating a floating separation.
                    drawLayer(canvas, foreground, -x * 0.25f, -y * 0.25f + 10f, shadowPaint)
                    drawLayer(canvas, foreground, -x * 0.25f, -y * 0.25f, foregroundPaint)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawLayer(canvas: Canvas, bitmap: Bitmap?, offsetX: Float, offsetY: Float, paint: Paint) {
            if (bitmap == null) return
            // Center-crop to cover the screen, then add parallax translation.
            val scale = max(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = (screenW - width) / 2f + offsetX
            val top = (screenH - height) / 2f + offsetY
            val dst = RectF(left, top, left + width, top + height)
            canvas.drawBitmap(bitmap, null, dst, paint)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawFrame)
            sensorManager.unregisterListener(this)
            background?.recycle()
            glow?.recycle()
            foreground?.recycle()
            background = null
            glow = null
            foreground = null
            super.onDestroy()
        }
    }
}
