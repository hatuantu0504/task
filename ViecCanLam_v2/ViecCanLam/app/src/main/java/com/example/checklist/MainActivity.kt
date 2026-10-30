package com.example.checklist

import android.Manifest
import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class Item(val id: Int, var text: String, var done: Boolean, var day: Long, var min: Int)

fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("checklist", Context.MODE_PRIVATE)
    fun load(c: Context): MutableList<Item> {
        val out = mutableListOf<Item>()
        val arr = JSONArray(prefs(c).getString("items", "[]") ?: "[]")
        val today = LocalDate.now().toEpochDay()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Item(o.optInt("id", 1000 + i), o.getString("t"), o.getBoolean("d"), o.optLong("day", today), o.optInt("m", -1)))
        }
        return out
    }
    fun save(c: Context, l: List<Item>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.done).put("day", it.day).put("m", it.min)) }
        prefs(c).edit().putString("items", arr.toString()).apply()
        TaskWidget.refresh(c)
    }
}

object Alarms {
    fun triggerAt(i: Item): Long =
        LocalDate.ofEpochDay(i.day).atTime(i.min / 60, i.min % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun pi(c: Context, i: Item): PendingIntent =
        PendingIntent.getBroadcast(
            c, i.id, Intent(c, AlarmReceiver::class.java).putExtra("id", i.id).putExtra("t", i.text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun cancel(c: Context, i: Item) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, i))

    fun set(c: Context, i: Item) {
        cancel(c, i)
        if (i.min < 0 || i.done) return
        val t = triggerAt(i)
        if (t <= System.currentTimeMillis()) return
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(t, openApp(c)), pi(c, i))
    }

    fun rescheduleAll(c: Context) = Store.load(c).forEach { set(c, it) }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Alarms.rescheduleAll(c); return }
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("remind", "Nhắc việc", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        })
        val n = Notification.Builder(c, "remind")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Đến giờ rồi ⏰")
            .setContentText(intent.getStringExtra("t"))
            .setContentIntent(Alarms.openApp(c))
            .setAutoCancel(true).build()
        nm.notify(intent.getIntExtra("id", 0), n)
    }
}

class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, build(c)) }
    }

    companion object {
        fun build(c: Context): RemoteViews {
            val today = LocalDate.now().toEpochDay()
            val list = Store.load(c).filter { it.day == today }.sortedBy { if (it.min < 0) 9999 else it.min }
            val v = RemoteViews(c.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_title, "Hôm nay · ${list.count { it.done }}/${list.size} xong")
            v.setTextViewText(
                R.id.w_body,
                if (list.isEmpty()) "Chưa có việc nào cho hôm nay ✨"
                else list.take(6).joinToString("\n") { (if (it.done) "☑ " else "☐ ") + (if (it.min >= 0) hm(it.min) + "  " else "") + it.text }
            )
            v.setOnClickPendingIntent(R.id.w_root, Alarms.openApp(c))
            return v
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, TaskWidget::class.java)).forEach { m.updateAppWidget(it, build(c)) }
        }
    }
}

class MainActivity : Activity() {
    private lateinit var items: MutableList<Item>
    private val picked = mutableSetOf<Item>()
    private var tab = 0
    private var view = 0
    private var deleting = false
    private var railOpen = true
    private var sy = 0
    private var selDay = LocalDate.now().toEpochDay()
    private var selMin = -1
    private var draft = ""
    private var scroll: ScrollView? = null
    private lateinit var rail: LinearLayout
    private lateinit var content: FrameLayout

    private val PURPLE = 0xFF8B5CF6.toInt()
    private val BLUE = 0xFF3B82F6.toInt()
    private val PINK = 0xFFF43F5E.toInt()
    private val CARD = 0xFF171726.toInt()
    private val LINE = 0xFF2B2B46.toInt()
    private val MUTE = 0xFF7C7C9E.toInt()
    private val SOFT = 0xFFB4B4D6.toInt()
    private val DLG = android.R.style.Theme_Material_Dialog_Alert
    private val VERT = LinearLayout.VERTICAL
    private val HORIZ = LinearLayout.HORIZONTAL
    private val DOW = arrayOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)
    private fun save() = Store.save(this, items)

    private fun dayLabel(d: Long): String {
        val ld = LocalDate.ofEpochDay(d)
        return when (d - LocalDate.now().toEpochDay()) {
            0L -> "Hôm nay"; 1L -> "Ngày mai"; -1L -> "Hôm qua"
            else -> DOW[ld.dayOfWeek.value - 1] + ", %02d/%02d".format(ld.dayOfMonth, ld.monthValue)
        }
    }

    private fun box(color: Int, r: Int, sw: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (sw > 0) setStroke(dp(sw), sc)
    }

    private fun grad(c1: Int, c2: Int, r: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply { cornerRadius = dp(r).toFloat() }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun glow(color: Int, size: Int, g: Int, m: Int): View = View(this).apply {
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(size / 2).toFloat()
            colors = intArrayOf((color and 0x00FFFFFF) or 0x66000000, color and 0x00FFFFFF)
        }
        layoutParams = FrameLayout.LayoutParams(dp(size), dp(size), g).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }
    }

    private fun chip(label: String, on: Boolean, click: () -> Unit) =
        tv(label, 13f, if (on) Color.WHITE else SOFT, on).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(9), dp(14), dp(9))
            background = if (on) grad(PURPLE, BLUE, 20) else box(0xFF0F0F1C.toInt(), 20, 1, LINE)
            setOnClickListener { click() }
        }

    private fun chipRow(vararg v: View) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply {
            orientation = HORIZ
            v.forEach { addView(it, lp(-2, -2).apply { rightMargin = dp(8) }) }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = Store.load(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        window.statusBarColor = 0xFF0A0A14.toInt()
        window.navigationBarColor = 0xFF0A0A14.toInt()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0A0A14.toInt(), 0xFF140B26.toInt(), 0xFF0A1428.toInt())
        )
        root.addView(glow(PURPLE, 300, Gravity.TOP or Gravity.END, -110))
        root.addView(glow(BLUE, 300, Gravity.BOTTOM or Gravity.START, -120))

        rail = LinearLayout(this).apply {
            orientation = VERT; gravity = Gravity.CENTER_HORIZONTAL
            background = box(0xCC0C0C1A.toInt(), 0)
        }
        content = FrameLayout(this)
        val row = LinearLayout(this).apply { orientation = HORIZ }
        row.addView(rail, lp(dp(76), -1))
        row.addView(View(this).apply { setBackgroundColor(LINE) }, lp(dp(1), -1))
        row.addView(content, lp(0, -1, 1f))
        root.addView(row, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        buildRail()
        content.removeAllViews()
        content.addView(if (tab == 0) pageAdd() else pageList())
    }

    // ---------- Thanh bên trái (thu gọn được) ----------
    private fun buildRail() {
        rail.removeAllViews()
        rail.layoutParams = lp(dp(if (railOpen) 76 else 40), -1)
        val side = if (railOpen) 8 else 4
        rail.setPadding(dp(side), dp(28), dp(side), dp(16))
        val toggle = tv(if (railOpen) "‹" else "›", 22f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = box(0xFF1E1E33.toInt(), 14, 1, LINE)
            setOnClickListener { railOpen = !railOpen; refresh() }
        }
        if (!railOpen) { rail.addView(toggle, lp(dp(32), dp(40))); return }
        val logo = tv("✓", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = grad(PURPLE, BLUE, 16) }
        rail.addView(logo, lp(dp(48), dp(48)).apply { bottomMargin = dp(32) })
        rail.addView(navBtn(0, "＋", "Thêm việc"))
        rail.addView(navBtn(1, "☰", "Danh sách"))
        rail.addView(View(this), lp(1, 0, 1f))
        rail.addView(toggle, lp(-1, dp(40)))
    }

    private fun navBtn(i: Int, icon: String, label: String): View {
        val on = tab == i
        return LinearLayout(this).apply {
            orientation = VERT; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(10))
            background = if (on) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x558B5CF6, 0x553B82F6))
                .apply { cornerRadius = dp(16).toFloat(); setStroke(dp(1), 0x888B5CF6.toInt()) } else null
            addView(tv(icon, 22f, if (on) Color.WHITE else MUTE, true).apply { gravity = Gravity.CENTER })
            addView(tv(label, 10.5f, if (on) Color.WHITE else MUTE).apply { gravity = Gravity.CENTER })
            layoutParams = lp(-1, -2).apply { bottomMargin = dp(10) }
            setOnClickListener { tab = i; deleting = false; picked.clear(); sy = 0; refresh() }
        }
    }

    // ---------- Tab 1: Thêm việc ----------
    private fun pageAdd(): View {
        val p = LinearLayout(this).apply { orientation = VERT; setPadding(dp(20), dp(30), dp(20), dp(22)) }
        val now = LocalDate.now()
        p.addView(tv("Chào bạn 👋", 14f, 0xFFA78BFA.toInt(), true))
        p.addView(tv("Thêm việc mới", 28f, Color.WHITE, true).apply { setPadding(0, dp(2), 0, 0) })
        p.addView(tv("${DOW[now.dayOfWeek.value - 1]}, %02d/%02d/%d".format(now.dayOfMonth, now.monthValue, now.year), 13f, MUTE)
            .apply { setPadding(0, dp(4), 0, dp(20)) })

        val card = LinearLayout(this).apply {
            orientation = VERT; setPadding(dp(16), dp(16), dp(16), dp(16)); background = box(CARD, 24, 1, LINE)
        }
        val input = EditText(this).apply {
            hint = "Bạn muốn làm gì?"; setHintTextColor(MUTE); setTextColor(Color.WHITE); setText(draft)
            textSize = 18f; setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
            background = box(0xFF0F0F1C.toInt(), 14, 1, LINE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val info = tv("", 13f, MUTE).apply { setPadding(0, dp(14), 0, 0) }
        fun upd(msg: String? = null) { info.text = msg ?: "Hiện có ${items.size} việc trong danh sách" }
        fun keep(b: () -> Unit) { draft = input.text.toString(); b(); refresh() }

        val t = LocalDate.now().toEpochDay()
        val custom = selDay != t && selDay != t + 1
        val days = chipRow(
            chip("Hôm nay", selDay == t) { keep { selDay = t } },
            chip("Ngày mai", selDay == t + 1) { keep { selDay = t + 1 } },
            chip(if (custom) "📅 " + dayLabel(selDay) else "📅 Chọn ngày", custom) {
                draft = input.text.toString()
                val d = LocalDate.ofEpochDay(selDay)
                DatePickerDialog(this, DLG, { _, y, m, dd -> selDay = LocalDate.of(y, m + 1, dd).toEpochDay(); refresh() },
                    d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
        )
        val times = chipRow(
            chip("Không nhắc", selMin < 0) { keep { selMin = -1 } },
            chip(if (selMin >= 0) "⏰ " + hm(selMin) else "⏰ Đặt giờ nhắc", selMin >= 0) {
                draft = input.text.toString()
                TimePickerDialog(this, DLG, { _, h, m -> selMin = h * 60 + m; refresh() },
                    if (selMin >= 0) selMin / 60 else 8, if (selMin >= 0) selMin % 60 else 0, true).show()
            }
        )

        fun add() {
            val txt = input.text.toString().trim()
            if (txt.isEmpty()) return
            val it2 = Item((System.currentTimeMillis() % 1_000_000_000L).toInt() + 2000, txt, false, selDay, selMin)
            items.add(it2); Alarms.set(this, it2); save(); draft = ""; input.setText("")
            var msg = "Đã thêm “$txt” · ${dayLabel(selDay)}"
            if (selMin >= 0) msg += if (Alarms.triggerAt(it2) > System.currentTimeMillis()) " lúc ${hm(selMin)} ⏰" else " (giờ đã qua, không nhắc)"
            upd(msg)
        }
        input.setOnEditorActionListener { _, _, _ -> add(); true }
        val btn = tv("＋  Thêm vào danh sách", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16)); background = grad(PURPLE, BLUE, 16)
            setOnClickListener { add() }
        }
        upd()
        card.addView(input)
        card.addView(tv("Làm vào ngày", 12f, MUTE).apply { setPadding(dp(2), dp(14), 0, dp(6)) })
        card.addView(days)
        card.addView(tv("Nhắc lúc", 12f, MUTE).apply { setPadding(dp(2), dp(14), 0, dp(6)) })
        card.addView(times)
        card.addView(btn, lp(-1, -2).apply { topMargin = dp(18) })
        card.addView(info)
        p.addView(card)

        val wbtn = tv("📌  Thêm widget ra màn hình chính", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(15), 0, dp(15))
            background = box(0x228B5CF6, 16, 1, 0x668B5CF6)
            setOnClickListener {
                val m = AppWidgetManager.getInstance(this@MainActivity)
                if (m.isRequestPinAppWidgetSupported)
                    m.requestPinAppWidget(ComponentName(this@MainActivity, TaskWidget::class.java), null, null)
                else Toast.makeText(this@MainActivity, "Giữ màn hình chính → Tiện ích → Việc cần làm", Toast.LENGTH_LONG).show()
            }
        }
        p.addView(wbtn, lp(-1, -2).apply { topMargin = dp(18) })
        return ScrollView(this).apply { addView(p); isVerticalScrollBarEnabled = false }
    }

    // ---------- Tab 2: Danh sách ----------
    private fun pageList(): View {
        val p = LinearLayout(this).apply { orientation = VERT; setPadding(dp(20), dp(30), dp(20), dp(16)) }
        val t = LocalDate.now().toEpochDay()
        val shown = items.filter { when (view) { 0 -> it.day == t; 1 -> it.day == t + 1; else -> true } }
            .sortedWith(compareBy<Item>({ it.day }, { if (it.min < 0) 9999 else it.min }))
        val k = shown.count { it.done }
        val n = shown.size

        val head = LinearLayout(this).apply { orientation = HORIZ; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = VERT }
        titles.addView(tv("Việc cần làm", 28f, Color.WHITE, true))
        titles.addView(tv(if (deleting) "Chọn các việc muốn xóa" else "Chạm vào việc để đánh dấu ✓", 13f, MUTE)
            .apply { setPadding(0, dp(4), 0, 0) })
        head.addView(titles, lp(0, -2, 1f))
        val trash = tv(if (deleting) "Hủy" else "🗑", if (deleting) 14f else 20f, Color.WHITE, deleting).apply {
            gravity = Gravity.CENTER
            background = box(0xFF1E1E33.toInt(), 14, 1, if (deleting) PINK else LINE)
            setOnClickListener { deleting = !deleting; picked.clear(); refresh() }
        }
        head.addView(trash, lp(dp(if (deleting) 64 else 44), dp(44)))
        p.addView(head)

        p.addView(chipRow(
            chip("Hôm nay", view == 0) { view = 0; sy = 0; refresh() },
            chip("Ngày mai", view == 1) { view = 1; sy = 0; refresh() },
            chip("Tất cả", view == 2) { view = 2; sy = 0; refresh() }
        ), lp(-1, -2).apply { topMargin = dp(16) })

        if (!deleting) {
            val hero = LinearLayout(this).apply {
                orientation = VERT; setPadding(dp(18), dp(14), dp(18), dp(16))
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x558B5CF6, 0x553B82F6))
                    .apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x668B5CF6) }
            }
            val line = LinearLayout(this).apply { orientation = HORIZ; gravity = Gravity.CENTER_VERTICAL }
            line.addView(tv(if (n == 0) "—" else "${k * 100 / n}%", 30f, Color.WHITE, true))
            line.addView(tv(if (n == 0) "Chưa có việc nào" else "Đã xong $k/$n việc", 14f, SOFT).apply { setPadding(dp(14), 0, 0, 0) })
            hero.addView(line)
            if (n > 0) {
                val bar = LinearLayout(this).apply { orientation = HORIZ; weightSum = n.toFloat(); background = box(0x33FFFFFF, 4) }
                if (k > 0) bar.addView(View(this).apply { background = grad(PURPLE, BLUE, 4) }, lp(0, -1, k.toFloat()))
                hero.addView(bar, lp(-1, dp(8)).apply { topMargin = dp(10) })
            }
            p.addView(hero, lp(-1, -2).apply { topMargin = dp(14) })
        }

        val col = LinearLayout(this).apply { orientation = VERT }
        if (shown.isEmpty()) {
            col.addView(LinearLayout(this).apply {
                orientation = VERT; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(50), 0, 0)
                addView(tv("✨", 46f, Color.WHITE).apply { gravity = Gravity.CENTER })
                addView(tv("Không có việc nào", 18f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, dp(4)) })
                addView(tv("Sang tab “Thêm việc” để tạo mới", 14f, MUTE))
            })
        }
        var last = Long.MIN_VALUE
        shown.forEach {
            if (view == 2 && it.day != last) {
                col.addView(tv(dayLabel(it.day), 13f, 0xFFA78BFA.toInt(), true).apply { setPadding(dp(4), dp(12), 0, dp(8)) })
                last = it.day
            }
            col.addView(row(it), lp(-1, -2).apply { bottomMargin = dp(10) })
        }
        val sv = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
        scroll = sv
        sv.post { sv.scrollTo(0, sy) }
        p.addView(sv, lp(-1, 0, 1f).apply { topMargin = dp(14) })

        if (deleting) {
            val c = picked.size
            val del = tv(if (c == 0) "Chọn việc để xóa" else "Xóa $c việc đã chọn", 16f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16))
                background = if (c == 0) box(0xFF2A2A40.toInt(), 16) else grad(PINK, 0xFFE11D74.toInt(), 16)
                setOnClickListener {
                    if (picked.isNotEmpty()) {
                        picked.forEach { Alarms.cancel(this@MainActivity, it) }
                        items.removeAll(picked); picked.clear(); deleting = false; save(); refresh()
                    }
                }
            }
            p.addView(del, lp(-1, -2).apply { topMargin = dp(8) })
        }
        return p
    }

    private fun row(item: Item): View {
        val sel = deleting && item in picked
        val on = if (deleting) sel else item.done
        val r = LinearLayout(this).apply {
            orientation = HORIZ; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = box(if (sel) 0xFF2A1426.toInt() else CARD, 18, 1, if (sel) PINK else LINE)
        }
        val rad = if (deleting) 13 else 8
        val mark = tv(if (on) "✓" else "", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = if (on) grad(if (deleting) PINK else PURPLE, if (deleting) 0xFFFB7185.toInt() else BLUE, rad)
            else box(0, rad, 2, MUTE)
        }
        r.addView(mark, lp(dp(26), dp(26)))
        val texts = LinearLayout(this).apply { orientation = VERT; setPadding(dp(14), 0, 0, 0) }
        val strike = item.done && !deleting
        texts.addView(tv(item.text, 17f, if (strike) MUTE else Color.WHITE).apply {
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        if (item.min >= 0) texts.addView(tv("⏰ " + hm(item.min), 12f, 0xFF60A5FA.toInt()).apply { setPadding(0, dp(3), 0, 0) })
        r.addView(texts, lp(0, -2, 1f))
        r.setOnClickListener {
            sy = scroll?.scrollY ?: 0
            if (deleting) { if (!picked.remove(item)) picked.add(item) }
            else { item.done = !item.done; Alarms.set(this, item) }
            save(); refresh()
        }
        return r
    }
}
