package org.briarproject.android.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import static android.content.Context.WINDOW_SERVICE;

public class LayoutUtils {

	public static int getPadding(Context ctx) {
		DisplayMetrics metrics = getDisplayMetrics(ctx);
		int percent = Math.max(metrics.widthPixels, metrics.heightPixels) / 100;
		return percent + 7;
	}

	private static DisplayMetrics getDisplayMetrics(Context ctx) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager wm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(metrics);
		return metrics;
	}
}
