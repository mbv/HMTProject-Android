package mbv.hmtproject

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper

object Helper {

    fun runInUiLoop(runnable: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        handler.post(runnable)
    }

    fun GetBitmapMarker(mContext: Context, templateBitmap: Bitmap, mText: String): Bitmap? {
        try {
            val resources = mContext.resources
            val scale = resources.displayMetrics.density

            var bitmapConfig: android.graphics.Bitmap.Config? = templateBitmap.config

            if (bitmapConfig == null)
                bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888

            val bitmap = templateBitmap.copy(bitmapConfig, true)

            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.textSize = (14 * scale).toInt().toFloat()
            paint.isFakeBoldText = true


            val bounds = Rect()
            paint.getTextBounds(mText, 0, mText.length, bounds)
            val x = (bitmap.width - bounds.width() - 2) / 2
            val y = 31

            canvas.drawText(mText, x.toFloat(), y * scale, paint)

            return bitmap

        } catch (e: Exception) {
            return null
        }

    }
}
