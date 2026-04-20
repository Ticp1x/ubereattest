package com.jieyi.ubereats

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var summaryText: TextView
    private lateinit var emptyText: TextView
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        title = getString(R.string.history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        summaryText = findViewById(R.id.summaryText)
        emptyText = findViewById(R.id.emptyText)
        listView = findViewById(R.id.historyList)

        adapter = HistoryAdapter(this, emptyList())
        listView.adapter = adapter

        findViewById<Button>(R.id.clearBtn).setOnClickListener { confirmClear() }
        findViewById<Button>(R.id.exportBtn).setOnClickListener { exportCsv() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun refresh() {
        val all = OrderHistory.loadAll(this).sortedByDescending { it.timestamp }
        adapter.update(all)
        if (all.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
        val accepted = all.count { it.choice == Choice.ACCEPTED }
        val rejected = all.count { it.choice == Choice.REJECTED }
        val undecided = all.count { it.choice == Choice.UNDECIDED }
        val netAcceptedSum = all.filter { it.choice == Choice.ACCEPTED }.sumOf { it.netProfit }
        val avgIdle = if (all.isNotEmpty()) all.sumOf { it.idleSecondsBefore } / all.size else 0L
        summaryText.text = buildString {
            append("总查询 ${all.size} · 接 $accepted · 拒 $rejected · 未决 $undecided\n")
            append("已接订单净利润合计 \$${"%.2f".format(netAcceptedSum)}\n")
            append("平均查询间隔等单 ${avgIdle / 60}m${avgIdle % 60}s")
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_clear)
            .setMessage("确认清空所有订单记录？此操作无法撤销。")
            .setPositiveButton("清空") { _, _ ->
                OrderHistory.clear(this)
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportCsv() {
        val records = OrderHistory.loadAll(this)
        if (records.isEmpty()) {
            Toast.makeText(this, "无记录可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val csv = OrderHistory.toCsv(records)
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "ubereats_$stamp.csv")
        file.writeText(csv, Charsets.UTF_8)

        val authority = "$packageName.fileprovider"
        val uri = FileProvider.getUriForFile(this, authority, file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "导出 ${records.size} 条记录"))
    }

    private class HistoryAdapter(
        private val ctx: AppCompatActivity,
        private var items: List<OrderRecord>
    ) : BaseAdapter() {

        private val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

        fun update(newItems: List<OrderRecord>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].timestamp

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: ctx.layoutInflater.inflate(
                R.layout.item_history, parent, false
            )
            val r = items[position]
            view.findViewById<TextView>(R.id.itemTime).text =
                "${dateFmt.format(Date(r.timestamp))} · 等 ${r.idleSecondsBefore / 60}m · ${r.stageLabel}"
            view.findViewById<TextView>(R.id.itemMain).text =
                "\$${"%.2f".format(r.payout)} · ${"%.1f".format(r.km)}km · ${"%.0f".format(r.minutes)}min"
            view.findViewById<TextView>(R.id.itemDetail).text =
                "\$/km ${"%.2f".format(r.perKm)} · 净\$${"%.2f".format(r.netProfit)} · ${r.verdict.name}"

            val choiceView = view.findViewById<TextView>(R.id.itemChoice)
            when (r.choice) {
                Choice.ACCEPTED -> {
                    choiceView.text = "接"
                    choiceView.setBackgroundResource(R.drawable.result_go)
                }
                Choice.REJECTED -> {
                    choiceView.text = "拒"
                    choiceView.setBackgroundResource(R.drawable.result_no)
                }
                Choice.UNDECIDED -> {
                    choiceView.text = "?"
                    choiceView.setBackgroundResource(R.drawable.result_hold)
                }
            }
            return view
        }
    }
}
