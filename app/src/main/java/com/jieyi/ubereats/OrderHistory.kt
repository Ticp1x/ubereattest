package com.jieyi.ubereats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Choice { UNDECIDED, ACCEPTED, REJECTED }

data class OrderRecord(
    val timestamp: Long,
    val payout: Double,
    val km: Double,
    val minutes: Double,
    val perKm: Double,
    val perMin: Double,
    val netProfit: Double,
    val netHourly: Double,
    val verdict: Verdict,
    val choice: Choice,
    val idleSecondsBefore: Long,
    val stageLabel: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ts", timestamp)
        put("payout", payout)
        put("km", km)
        put("min", minutes)
        put("pKm", perKm)
        put("pMin", perMin)
        put("net", netProfit)
        put("netH", netHourly)
        put("v", verdict.name)
        put("c", choice.name)
        put("idle", idleSecondsBefore)
        put("stage", stageLabel)
    }

    companion object {
        fun fromJson(o: JSONObject): OrderRecord = OrderRecord(
            timestamp = o.getLong("ts"),
            payout = o.getDouble("payout"),
            km = o.getDouble("km"),
            minutes = o.getDouble("min"),
            perKm = o.getDouble("pKm"),
            perMin = o.getDouble("pMin"),
            netProfit = o.getDouble("net"),
            netHourly = o.getDouble("netH"),
            verdict = runCatching { Verdict.valueOf(o.getString("v")) }.getOrDefault(Verdict.HOLD),
            choice = runCatching { Choice.valueOf(o.getString("c")) }.getOrDefault(Choice.UNDECIDED),
            idleSecondsBefore = o.optLong("idle", 0L),
            stageLabel = o.optString("stage", "")
        )
    }
}

object OrderHistory {
    private const val FILE_NAME = "history.json"
    private const val MAX_RECORDS = 2000

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    @Synchronized
    fun append(ctx: Context, r: OrderRecord) {
        val records = loadAll(ctx).toMutableList()
        records.add(r)
        if (records.size > MAX_RECORDS) {
            records.subList(0, records.size - MAX_RECORDS).clear()
        }
        writeAll(ctx, records)
    }

    @Synchronized
    fun updateChoice(ctx: Context, timestamp: Long, choice: Choice) {
        val records = loadAll(ctx).toMutableList()
        val idx = records.indexOfFirst { it.timestamp == timestamp }
        if (idx >= 0) {
            records[idx] = records[idx].copy(choice = choice)
            writeAll(ctx, records)
        }
    }

    @Synchronized
    fun loadAll(ctx: Context): List<OrderRecord> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map { OrderRecord.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    private fun writeAll(ctx: Context, records: List<OrderRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        file(ctx).writeText(arr.toString(), Charsets.UTF_8)
    }

    fun toCsv(records: List<OrderRecord>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("time,payout,km,min,\$/km,\$/min,net,\$/h,verdict,choice,idleSec,stage\n")
        for (r in records) {
            sb.append(dateFmt.format(Date(r.timestamp))).append(",")
            sb.append("%.2f".format(r.payout)).append(",")
            sb.append("%.2f".format(r.km)).append(",")
            sb.append("%.2f".format(r.minutes)).append(",")
            sb.append("%.2f".format(r.perKm)).append(",")
            sb.append("%.2f".format(r.perMin)).append(",")
            sb.append("%.2f".format(r.netProfit)).append(",")
            sb.append("%.1f".format(r.netHourly)).append(",")
            sb.append(r.verdict.name).append(",")
            sb.append(r.choice.name).append(",")
            sb.append(r.idleSecondsBefore).append(",")
            sb.append(r.stageLabel).append("\n")
        }
        return sb.toString()
    }
}
