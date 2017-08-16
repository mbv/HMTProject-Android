package mbv.hmtproject;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

public class Helper {
    private static volatile Helper instance;

    public static Helper getInstance() {
        Helper localInstance = instance;
        if (localInstance == null) {
            synchronized (Helper.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new Helper();
                }
            }
        }
        return localInstance;
    }

    private Helper() {}

    public void runInUiLoop(Runnable runnable) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(runnable);
    }

    public Bitmap GetBitmapMarker(Context mContext, Bitmap templateBitmap, String mText) {
        try {
            Resources resources = mContext.getResources();
            float scale = resources.getDisplayMetrics().density;

            android.graphics.Bitmap.Config bitmapConfig = templateBitmap.getConfig();

            if (bitmapConfig == null)
                bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;

            Bitmap bitmap = templateBitmap.copy(bitmapConfig, true);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTextSize((int) (14 * scale));
            paint.setFakeBoldText(true);


            Rect bounds = new Rect();
            paint.getTextBounds(mText, 0, mText.length(), bounds);
            int x = (bitmap.getWidth() - bounds.width() - 2)/2;
            int y = 31;

            canvas.drawText(mText, x, y * scale, paint);

            return bitmap;

        } catch (Exception e) {
            return null;
        }
    }
}
