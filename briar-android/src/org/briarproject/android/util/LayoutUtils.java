package org.briarproject.android.util;

import static android.content.Context.WINDOW_SERVICE;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class LayoutUtils {

	public static int getSeparatorWidth(Context ctx) {
		return Math.max(2, getMaxDisplayDimension(ctx) / 100 - 6);
	}

	public static int getPadding(Context ctx) {
		return getMaxDisplayDimension(ctx) / 100 + 7;
	}

	private static int getMaxDisplayDimension(Context ctx) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager wm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(metrics);
		return Math.max(metrics.widthPixels, metrics.heightPixels);
	}
}
