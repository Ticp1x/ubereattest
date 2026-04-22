package com.jieyi.ubereats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * 前台 service 持 MediaProjection + VirtualDisplay + ImageReader。
 *
 * 对外接口（通过 startService 的 action）：
 *   ACTION_START   首次初始化，需带 EXTRA_RESULT_CODE + EXTRA_RESULT_DATA（MediaProjection 授权结果）
 *   ACTION_CAPTURE 截一张 + OCR + 识别派单则弹 Toast
 *   ACTION_STOP    停止
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCapture"
        const val ACTION_START = "com.jieyi.ubereats.CAPTURE_START"
        const val ACTION_CAPTURE = "com.jieyi.ubereats.CAPTURE_ONCE"
        const val ACTION_STOP = "com.jieyi.ubereats.CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_RESULT_DATA = "rd"

        private const val NOTIF_CHAN = "screen_capture"
        private const val NOTIF_ID = 4420

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_CAPTURE -> handleCapture()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> { /* 系统重启时 action 可能为 null，不特别处理 */ }
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        if (projection != null) {
            Log.i(TAG, "already started, skipping")
            return
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (data == null) {
            Log.w(TAG, "no result data, stopping")
            stopSelf()
            return
        }

        // Android 14+ 要求 MediaProjection 的 service 必须先进入 foreground 再拿 projection
        startAsForeground()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        densityDpi = metrics.densityDpi

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "projection stopped by system")
                cleanup()
                stopSelf()
            }
        }, Handler(mainLooper))

        thread = HandlerThread("ScreenCapture").also { it.start() }
        handler = Handler(thread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "UberEatsCapture",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        isRunning = true
        Log.i(TAG, "started ${width}x${height} dpi=$densityDpi")
    }

    private fun handleCapture() {
        val reader = imageReader
        if (reader == null) {
            Log.w(TAG, "capture requested but reader is null (not started?)")
            return
        }
        handler?.post {
            val bitmap = acquireBitmap(reader)
            if (bitmap == null) {
                Log.w(TAG, "no frame available")
                return@post
            }
            OcrHelper.recognizeAndNotify(applicationContext, bitmap)
        }
    }

    private fun acquireBitmap(reader: ImageReader): Bitmap? {
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmpW = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(bmpW, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(buffer)
            // 裁掉 row padding
            if (bmpW == width) padded
            else Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        } catch (e: Throwable) {
            Log.w(TAG, "acquireBitmap failed: ${e.message}")
            null
        } finally {
            try { image?.close() } catch (_: Throwable) {}
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIF_CHAN, "屏幕读单", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val notif = Notification.Builder(this, NOTIF_CHAN)
            .setContentTitle("自动读单已启用")
            .setContentText("Uber 派单弹出时截屏识别")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        isRunning = false
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        projection = null
        thread?.quitSafely()
        thread = null
        handler = null
    }
}
