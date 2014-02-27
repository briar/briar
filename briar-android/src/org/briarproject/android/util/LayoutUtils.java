package org.briarproject.android.util;

import static android.content.Context.WINDOW_SERVICE;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class LayoutUtils {

	public static int getPadding(Context ctx) {
		DisplayMetrics metrics = getDisplayMetrics(ctx);
		int percent = Math.max(metrics.widthPixels, metrics.heightPixels) / 100;
		return percent + 7;
	}

	public static int getLargeItemPadding(Context ctx) {
		DisplayMetrics metrics = getDisplayMetrics(ctx);
		return Math.min(metrics.widthPixels, metrics.heightPixels) / 4;
	}

	private static DisplayMetrics getDisplayMetrics(Context ctx) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager wm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(metrics);
		return metrics;
	}
}
