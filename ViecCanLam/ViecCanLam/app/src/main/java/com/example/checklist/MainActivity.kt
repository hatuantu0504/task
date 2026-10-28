package com.example.checklist

import android.app.Activity
import android.graphics.Paint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class Item(var text: String, var done: Boolean)

class MainActivity : Activity() {
    private val items = mutableListOf<Item>()
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "Việc cần làm"; textSize = 26f
            setTextColor(Color.parseColor("#1F2A24"))
        })
        summary = TextView(this).apply { textSize = 14f; setPadding(0, dp(4), 0, dp(12)) }
        root.addView(summary)

        val input = EditText(this).apply {
            hint = "Thêm việc mới…"; setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val addBtn = Button(this).apply { text = "Thêm" }
        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        addRow.addView(addBtn)
        root.addView(addRow)

        fun add() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            items.add(Item(t, false)); input.setText(""); save(); render()
        }
        addBtn.setOnClickListener { add() }
        input.setOnEditorActionListener { _, _, _ -> add(); true }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(Button(this).apply {
            text = "Xóa việc đã xong"
            setOnClickListener { items.removeAll { it.done }; save(); render() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(Button(this).apply {
            text = "Bỏ đánh dấu hết"
            setOnClickListener { items.forEach { it.done = false }; save(); render() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(footer)

        setContentView(root)
        render()
    }

    private fun render() {
        list.removeAllViews()
        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val cb = CheckBox(this).apply {
                text = item.text; textSize = 18f; isChecked = item.done
                paintFlags = if (item.done) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                             else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                alpha = if (item.done) 0.5f else 1f
                setOnClickListener { item.done = isChecked; save(); render() }
            }
            val del = TextView(this).apply {
                text = "×"; textSize = 28f; setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { items.remove(item); save(); render() }
            }
            row.addView(cb, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(del)
            list.addView(row)
        }
        val k = items.count { it.done }
        summary.text = if (items.isEmpty()) "Danh sách trống" else "Đã xong $k/${items.size} việc"
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
