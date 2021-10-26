package org.briarproject.android.dontkillmelib;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;

import java.io.IOException;
import java.util.Scanner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import static android.content.Context.POWER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static java.lang.Runtime.getRuntime;

public class PowerUtils {

	public static boolean needsDozeWhitelisting(Context ctx) {
		if (SDK_INT < 23) return false;
		PowerManager pm = (PowerManager) ctx.getSystemService(POWER_SERVICE);
		String packageName = ctx.getPackageName();
		if (pm == null) throw new AssertionError();
		return !pm.isIgnoringBatteryOptimizations(packageName);
	}

	@TargetApi(23)
	@SuppressLint("BatteryLife")
	public static Intent getDozeWhitelistingIntent(Context ctx) {
		Intent i = new Intent();
		i.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
		i.setData(Uri.parse("package:" + ctx.getPackageName()));
		return i;
	}

	static void showOnboardingDialog(Context ctx, String text) {
		new AlertDialog.Builder(ctx, R.style.OnboardingDialogTheme)
				.setMessage(text)
				.setNeutralButton(R.string.got_it,
						(dialog, which) -> dialog.cancel())
				.show();
	}

	@Nullable
	static String getSystemProperty(String propName) {
		try {
			Process p = getRuntime().exec("getprop " + propName);
			Scanner s = new Scanner(p.getInputStream());
			String line = s.nextLine();
			s.close();
			return line;
		} catch (SecurityException | IOException e) {
			return null;
		}
	}
}
