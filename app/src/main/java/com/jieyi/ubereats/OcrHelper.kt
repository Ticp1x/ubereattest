package com.jieyi.ubereats

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * ML Kit on-device OCR。
 * 中文和拉丁字符两个 recognizer 各自能识别"派送"/"分钟"/"公里" 和 "CA$"/"5.07" 这些，
 * 同一张 bitmap 并行跑两个 recognizer，合并文本后送 OrderParser。
 *
 * 识别到派单就弹 Toast；否则只打日志（tag "OcrHelper"）供调试。
 */
object OcrHelper {

    private const val TAG = "OcrHelper"

    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun recognizeAndNotify(ctx: Context, bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val t0 = System.currentTimeMillis()
        var zh = ""
        var en = ""
        var remaining = 2

        fun done() {
            remaining--
            if (remaining == 0) {
                val combined = if (zh.isNotEmpty() && en.isNotEmpty()) "$zh\n$en" else zh + en
                val elapsed = System.currentTimeMillis() - t0
                val ex = OrderParser.extract(combined)
                Log.i(
                    TAG,
                    "elapsed=${elapsed}ms zhLen=${zh.length} enLen=${en.length} hasMarker=${ex.hasMarker} " +
                        "price=${ex.price} km=${ex.km} min=${ex.minutes}"
                )
                // 调试：命中 marker 就把前 300 字 preview 打一遍，方便核对正则
                if (ex.hasMarker) {
                    Log.i(TAG, "[PREVIEW] ${combined.take(300).replace('\n', ' ')}")
                }
                if (ex.isOrder) {
                    val d = OrderNotifier.evaluate(ctx, ex.price!!, ex.km!!, ex.minutes!!)
                    OrderNotifier.toast(ctx, ex.price, ex.km, ex.minutes, d)
                }
            }
        }

        chineseRecognizer.process(image)
            .addOnSuccessListener { zh = it.text; done() }
            .addOnFailureListener { e -> Log.w(TAG, "zh fail: ${e.message}"); done() }

        latinRecognizer.process(image)
            .addOnSuccessListener { en = it.text; done() }
            .addOnFailureListener { e -> Log.w(TAG, "en fail: ${e.message}"); done() }
    }
}
