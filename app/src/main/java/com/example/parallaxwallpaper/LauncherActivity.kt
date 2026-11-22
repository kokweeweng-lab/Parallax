package com.example.parallaxwallpaper

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LauncherActivity : Activity() {

    data class App(val label: String, val component: ComponentName, var icon: Drawable? = null)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val iconCache = HashMap<String, Drawable>()
    private var iconMap: Map<String, String> = emptyMap()
    private var allApps: List<App> = emptyList()
    private var shown: List<App> = emptyList()
    private lateinit var adapter: AppAdapter
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var dock: LinearLayout
    private var iconBack: Bitmap? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { loadApps() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now).uppercase()
            main.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        iconMap = parseAppFilter()
        iconBack = BitmapFactory.decodeResource(resources, resources.getIdentifier("iconback", "drawable", packageName))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(44), dp(12), dp(28))
        }

        clock = neonText(58f, 0xFFFF4FD8.toInt()).apply { gravity = Gravity.CENTER }
        date = neonText(15f, 0xFF9FE8FF.toInt()).apply { gravity = Gravity.CENTER; letterSpacing = 0.2f }
        root.addView(clock, LinearLayout.LayoutParams(-1, -2))
        root.addView(date, LinearLayout.LayoutParams(-1, -2))

        val search = EditText(this).apply {
            hint = "搜索 App"
            setHintTextColor(0x99CFC8FF.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0x66140E24)
                setStroke(dp(1), 0xAA3FE0FF.toInt())
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { filter(s?.toString() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(16), dp(8), dp(10)) })

        adapter = AppAdapter()
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(10)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            isVerticalScrollBarEnabled = false
            this.adapter = this@LauncherActivity.adapter
            onItemClickListener = AdapterView.OnItemClickListener { _, v, pos, _ -> launch(shown[pos], v) }
            onItemLongClickListener = AdapterView.OnItemLongClickListener { _, v, pos, _ -> menu(shown[pos], v); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0x55100A1E)
                setStroke(dp(1), 0x88FF4FD8.toInt())
            }
        }
        root.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(8), dp(6), 0) })

        root.setOnLongClickListener { startActivity(Intent(this, SettingsActivity::class.java)); true }
        setContentView(FrameLayout(this).apply { addView(root) })

        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        loadApps()
    }

    override fun onResume() { super.onResume(); main.post(tick) }
    override fun onPause() { super.onPause(); main.removeCallbacks(tick) }
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        io.shutdown()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* home screen: stay */ }

    private fun neonText(size: Float, color: Int) = TextView(this).apply {
        textSize = size
        setTextColor(Color.WHITE)
        setShadowLayer(dp(10).toFloat(), 0f, 0f, color)
    }

    private fun parseAppFilter(): Map<String, String> {
        val m = HashMap<String, String>()
        try {
            val txt = assets.open("appfilter.xml").bufferedReader().readText()
            val re = Regex("component=\"ComponentInfo\\{([^}]+)\\}\"\\s+drawable=\"([^\"]+)\"")
            re.findAll(txt).forEach { r ->
                val comp = r.groupValues[1]
                val pkg = comp.substringBefore('/')
                m[comp] = r.groupValues[2]
                if (!m.containsKey(pkg)) m[pkg] = r.groupValues[2] // package-level fallback
            }
        } catch (_: Exception) {}
        return m
    }

    private fun loadApps() {
        io.execute {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            val apps = list.map {
                App(it.loadLabel(pm).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name))
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }
            for (a in apps) a.icon = themedIcon(a, list)
            main.post {
                allApps = apps
                shown = apps
                adapter.notifyDataSetChanged()
                buildDock()
            }
        }
    }

    private fun themedIcon(a: App, list: List<ResolveInfo>): Drawable {
        val key = a.component.flattenToString()
        iconCache[key]?.let { return it }
        val full = a.component.packageName + "/" + a.component.className
        val name = iconMap[full] ?: iconMap[a.component.packageName]
        val id = if (name != null) resources.getIdentifier(name, "drawable", packageName) else 0
        val d: Drawable = if (id != 0) {
            resources.getDrawable(id, theme)
        } else {
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            iconBack?.let { c.drawBitmap(it, null, Rect(0, 0, size, size), null) }
            val orig = try {
                list.firstOrNull { r -> r.activityInfo.packageName == a.component.packageName && r.activityInfo.name == a.component.className }
                    ?.loadIcon(packageManager)
            } catch (_: Exception) { null }
            orig?.let {
                val inset = (size * 0.2f).toInt()
                it.setBounds(inset, inset, size - inset, size - inset)
                it.draw(c)
            }
            BitmapDrawable(resources, bmp)
        }
        iconCache[key] = d
        return d
    }

    private fun filter(q: String) {
        shown = if (q.isBlank()) allApps else allApps.filter { it.label.contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    private fun buildDock() {
        dock.removeAllViews()
        val wanted = listOf(
            listOf("com.google.android.dialer", "com.android.dialer", "com.android.contacts"),
            listOf("com.google.android.apps.messaging", "com.android.mms", "com.whatsapp"),
            listOf("com.android.camera", "com.google.android.GoogleCamera"),
            listOf("com.android.chrome", "com.vivo.browser")
        )
        val pickedPkgs = HashSet<String>()
        for (opts in wanted) {
            val app = opts.asSequence().mapNotNull { p -> allApps.firstOrNull { it.component.packageName == p && it.component.packageName !in pickedPkgs } }.firstOrNull()
                ?: continue
            pickedPkgs.add(app.component.packageName)
            val iv = ImageView(this).apply {
                setImageDrawable(app.icon)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { launch(app, it) }
                setOnLongClickListener { menu(app, it); true }
            }
            dock.addView(iv, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        dock.visibility = if (dock.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun launch(a: App, v: View) {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(i, opts.toBundle())
        } catch (_: Exception) {}
    }

    private fun menu(a: App, v: View) {
        PopupMenu(this, v).apply {
            menu.add(0, 1, 0, "应用信息")
            menu.add(0, 2, 1, "卸载")
            menu.add(0, 3, 2, "Neon Mech 设置")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.component.packageName)))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.component.packageName)))
                    3 -> startActivity(Intent(this@LauncherActivity, SettingsActivity::class.java))
                }
                true
            }
            show()
        }
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
            val cell = (convert as? LinearLayout) ?: LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context), LinearLayout.LayoutParams(dp(58), dp(58)))
                addView(TextView(context).apply {
                    textSize = 11.5f
                    setTextColor(Color.WHITE)
                    setShadowLayer(dp(4).toFloat(), 0f, 0f, Color.BLACK)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(2), dp(4), dp(2), 0)
                }, LinearLayout.LayoutParams(-1, -2))
            }
            val app = shown[p]
            (cell.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }
}
