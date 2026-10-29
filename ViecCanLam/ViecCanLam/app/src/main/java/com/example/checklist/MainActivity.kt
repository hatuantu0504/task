package com.example.checklist

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class Item(var text: String, var done: Boolean)

class MainActivity : Activity() {
    private val items = mutableListOf<Item>()
    private val picked = mutableSetOf<Item>()
    private var tab = 0
    private var deleting = false
    private var sy = 0
    private var scroll: ScrollView? = null
    private lateinit var rail: LinearLayout
    private lateinit var content: FrameLayout

    private val PURPLE = 0xFF8B5CF6.toInt()
    private val BLUE = 0xFF3B82F6.toInt()
    private val PINK = 0xFFF43F5E.toInt()
    private val CARD = 0xFF171726.toInt()
    private val LINE = 0xFF2B2B46.toInt()
    private val MUTE = 0xFF7C7C9E.toInt()
    private val VERT = LinearLayout.VERTICAL
    private val HORIZ = LinearLayout.HORIZONTAL

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

    private fun box(color: Int, r: Int, sw: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (sw > 0) setStroke(dp(sw), sc)
    }

    private fun grad(c1: Int, c2: Int, r: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply {
            cornerRadius = dp(r).toFloat()
        }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
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
            setPadding(dp(8), dp(28), dp(8), dp(16))
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

    private fun buildRail() {
        rail.removeAllViews()
        val logo = tv("✓", 24f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = grad(PURPLE, BLUE, 16)
        }
        rail.addView(logo, lp(dp(48), dp(48)).apply { bottomMargin = dp(32) })
        rail.addView(navBtn(0, "＋", "Thêm việc"))
        rail.addView(navBtn(1, "☰", "Danh sách"))
    }

    private fun navBtn(i: Int, icon: String, label: String): View {
        val on = tab == i
        return LinearLayout(this).apply {
            orientation = VERT; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(10))
            background = if (on) GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x558B5CF6, 0x553B82F6)
            ).apply { cornerRadius = dp(16).toFloat(); setStroke(dp(1), 0x888B5CF6.toInt()) } else null
            addView(tv(icon, 22f, if (on) Color.WHITE else MUTE, true).apply { gravity = Gravity.CENTER })
            addView(tv(label, 10.5f, if (on) Color.WHITE else MUTE).apply { gravity = Gravity.CENTER })
            layoutParams = lp(-1, -2).apply { bottomMargin = dp(10) }
            setOnClickListener { tab = i; deleting = false; picked.clear(); sy = 0; refresh() }
        }
    }

    // ---------- Tab 1: Thêm việc ----------
    private fun pageAdd(): View {
        val p = LinearLayout(this).apply { orientation = VERT; setPadding(dp(22), dp(32), dp(22), dp(22)) }
        p.addView(tv("Thêm việc mới", 28f, Color.WHITE, true))
        p.addView(tv("Ghi lại điều bạn muốn hoàn thành hôm nay", 14f, MUTE).apply { setPadding(0, dp(4), 0, dp(24)) })

        val card = LinearLayout(this).apply {
            orientation = VERT; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = box(CARD, 24, 1, LINE)
        }
        val input = EditText(this).apply {
            hint = "Bạn muốn làm gì?"; setHintTextColor(MUTE); setTextColor(Color.WHITE)
            textSize = 18f; setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
            background = box(0xFF0F0F1C.toInt(), 14, 1, LINE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val info = tv("", 13f, MUTE).apply { setPadding(0, dp(14), 0, 0) }
        fun upd(msg: String? = null) { info.text = msg ?: "Hiện có ${items.size} việc trong danh sách" }
        fun add() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            items.add(Item(t, false)); input.setText(""); save(); upd("Đã thêm “$t” ✓")
        }
        input.setOnEditorActionListener { _, _, _ -> add(); true }
        val btn = tv("＋  Thêm vào danh sách", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16))
            background = grad(PURPLE, BLUE, 16)
            setOnClickListener { add() }
        }
        upd()
        card.addView(input)
        card.addView(btn, lp(-1, -2).apply { topMargin = dp(16) })
        card.addView(info)
        p.addView(card)

        val tip = tv("💡  Mẹo: sang tab Danh sách và chạm vào một việc để đánh dấu ✓ khi đã làm xong.", 13f, 0xFFB4B4D6.toInt()).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = box(0x228B5CF6, 16, 1, 0x448B5CF6)
        }
        p.addView(tip, lp(-1, -2).apply { topMargin = dp(20) })
        return ScrollView(this).apply { addView(p); isVerticalScrollBarEnabled = false }
    }

    // ---------- Tab 2: Danh sách ----------
    private fun pageList(): View {
        val p = LinearLayout(this).apply { orientation = VERT; setPadding(dp(22), dp(32), dp(22), dp(16)) }
        val k = items.count { it.done }

        val head = LinearLayout(this).apply { orientation = HORIZ; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = VERT }
        titles.addView(tv("Việc cần làm", 28f, Color.WHITE, true))
        val sub = when {
            deleting -> "Chọn các việc muốn xóa"
            items.isEmpty() -> "Danh sách trống"
            else -> "Đã xong $k/${items.size} việc"
        }
        titles.addView(tv(sub, 14f, MUTE).apply { setPadding(0, dp(4), 0, 0) })
        head.addView(titles, lp(0, -2, 1f))
        val trash = tv(if (deleting) "Hủy" else "🗑", if (deleting) 14f else 20f, Color.WHITE, deleting).apply {
            gravity = Gravity.CENTER
            background = box(0xFF1E1E33.toInt(), 14, 1, if (deleting) PINK else LINE)
            setOnClickListener { deleting = !deleting; picked.clear(); refresh() }
        }
        head.addView(trash, lp(dp(if (deleting) 64 else 44), dp(44)))
        p.addView(head)

        if (items.isNotEmpty() && !deleting) {
            val bar = LinearLayout(this).apply {
                orientation = HORIZ; weightSum = items.size.toFloat()
                background = box(0xFF22223A.toInt(), 4)
            }
            if (k > 0) bar.addView(View(this).apply { background = grad(PURPLE, BLUE, 4) }, lp(0, -1, k.toFloat()))
            p.addView(bar, lp(-1, dp(8)).apply { topMargin = dp(16) })
        }

        val col = LinearLayout(this).apply { orientation = VERT }
        if (items.isEmpty()) {
            col.addView(LinearLayout(this).apply {
                orientation = VERT; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(70), 0, 0)
                addView(tv("✨", 46f, Color.WHITE).apply { gravity = Gravity.CENTER })
                addView(tv("Chưa có việc nào", 18f, Color.WHITE, true).apply { setPadding(0, dp(12), 0, dp(4)) })
                addView(tv("Sang tab “Thêm việc” để bắt đầu", 14f, MUTE))
            })
        }
        items.forEach { col.addView(row(it), lp(-1, -2).apply { bottomMargin = dp(10) }) }
        val sv = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
        scroll = sv
        sv.post { sv.scrollTo(0, sy) }
        p.addView(sv, lp(-1, 0, 1f).apply { topMargin = dp(16) })

        if (deleting) {
            val n = picked.size
            val del = tv(if (n == 0) "Chọn việc để xóa" else "Xóa $n việc đã chọn", 16f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16))
                background = if (n == 0) box(0xFF2A2A40.toInt(), 16) else grad(PINK, 0xFFE11D74.toInt(), 16)
                setOnClickListener {
                    if (picked.isNotEmpty()) {
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = box(if (sel) 0xFF2A1426.toInt() else CARD, 18, 1, if (sel) PINK else LINE)
        }
        val rad = if (deleting) 13 else 8
        val mark = tv(if (on) "✓" else "", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = if (on) grad(if (deleting) PINK else PURPLE, if (deleting) 0xFFFB7185.toInt() else BLUE, rad)
            else box(0, rad, 2, MUTE)
        }
        r.addView(mark, lp(dp(26), dp(26)))
        val label = tv(item.text, 17f, if (item.done && !deleting) MUTE else Color.WHITE).apply {
            setPadding(dp(14), 0, 0, 0)
            if (item.done && !deleting) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        r.addView(label, lp(0, -2, 1f))
        r.setOnClickListener {
            sy = scroll?.scrollY ?: 0
            if (deleting) { if (!picked.remove(item)) picked.add(item) } else item.done = !item.done
            save(); refresh()
        }
        return r
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.text).put("d", it.done)) }
        getSharedPreferences("checklist", MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
    }

    private fun load() {
        val s = getSharedPreferences("checklist", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val arr = JSONArray(s)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); items.add(Item(o.getString("t"), o.getBoolean("d")))
        }
    }
}
