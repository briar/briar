package org.briarproject.android.util;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.view.ArticleMovementMethod;
import org.briarproject.android.widget.LinkDialogFragment;
import org.briarproject.util.IoUtils;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
import static android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE;
import static android.text.format.DateUtils.FORMAT_ABBREV_TIME;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.WEEK_IN_MILLIS;

public class AndroidUtils {

	public static final long MIN_RESOLUTION = MINUTE_IN_MILLIS;
	public static final int TEASER_LENGTH = 320;

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";

	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= 21) {
			abis.addAll(Arrays.asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return Collections.unmodifiableList(abis);
	}

	public static void setError(TextInputLayout til, String error,
			boolean set) {
		if (set) {
			if (til.getError() == null) til.setError(error);
		} else {
			til.setError(null);
		}
	}

	public static String getBluetoothAddress(Context ctx,
			BluetoothAdapter adapter) {
		// Return the adapter's address if it's valid and not fake
		String address = adapter.getAddress();
		if (isValidBluetoothAddress(address)) return address;
		// Return the address from settings if it's valid and not fake
		address = Settings.Secure.getString(ctx.getContentResolver(),
				"bluetooth_address");
		if (isValidBluetoothAddress(address)) return address;
		// Let the caller know we can't find the address
		return "";
	}

	private static boolean isValidBluetoothAddress(String address) {
		return !StringUtils.isNullOrEmpty(address)
				&& BluetoothAdapter.checkBluetoothAddress(address)
				&& !address.equals(FAKE_BLUETOOTH_ADDRESS);
	}

	public static void deleteAppData(Context ctx) {
		File dataDir = new File(ctx.getApplicationInfo().dataDir);
		File[] children = dataDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!child.getName().equals("lib"))
					IoUtils.deleteFileOrDir(child);
			}
		}
		// Recreate the cache dir as some OpenGL drivers expect it to exist
		new File(dataDir, "cache").mkdir();
	}

	public static File getReportDir(Context ctx) {
		return ctx.getDir(STORED_REPORTS, MODE_PRIVATE);
	}

	public static String formatDate(Context ctx, long time) {
		int flags = FORMAT_ABBREV_RELATIVE |
				FORMAT_SHOW_DATE | FORMAT_ABBREV_TIME | FORMAT_ABBREV_MONTH;

		long diff = System.currentTimeMillis() - time;
		if (diff < MIN_RESOLUTION) return ctx.getString(R.string.now);
		if (diff >= DAY_IN_MILLIS && diff < WEEK_IN_MILLIS) {
			// also show time when older than a day, but newer than a week
			return DateUtils.getRelativeDateTimeString(ctx, time,
					MIN_RESOLUTION, WEEK_IN_MILLIS, flags).toString();
		}
		// otherwise just show "...ago" or date string
		return DateUtils.getRelativeTimeSpanString(time,
				System.currentTimeMillis(), MIN_RESOLUTION, flags).toString();
	}

	public static SpannableStringBuilder getTeaser(Context ctx, Spanned body) {
		if (body.length() < TEASER_LENGTH)
			throw new IllegalArgumentException(
					"String is shorter than TEASER_LENGTH");

		SpannableStringBuilder builder =
				new SpannableStringBuilder(body.subSequence(0, TEASER_LENGTH));
		String ellipsis = ctx.getString(R.string.ellipsis);
		builder.append(ellipsis).append(" ");

		Spannable readMore = new SpannableString(
				ctx.getString(R.string.read_more) + ellipsis);
		ForegroundColorSpan fg = new ForegroundColorSpan(
				ContextCompat.getColor(ctx, R.color.briar_text_link));
		readMore.setSpan(fg, 0, readMore.length(),
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		builder.append(readMore);

		return builder;
	}

	public static Spanned getSpanned(String s) {
		return Html.fromHtml(s);
	}

	public static void makeLinksClickable(TextView v) {
		SpannableStringBuilder ssb = new SpannableStringBuilder(v.getText());
		URLSpan[] spans = ssb.getSpans(0, ssb.length(), URLSpan.class);
		for (URLSpan span : spans) {
			int start = ssb.getSpanStart(span);
			int end = ssb.getSpanEnd(span);
			final String url = span.getURL();
			ssb.removeSpan(span);
			ClickableSpan cSpan = new ClickableSpan() {
				@Override
				public void onClick(View v2) {
					LinkDialogFragment f = LinkDialogFragment.newInstance(url);
					FragmentManager fm = ((AppCompatActivity) v2.getContext())
							.getSupportFragmentManager();
					f.show(fm, f.getUniqueTag());
				}
			};
			ssb.setSpan(cSpan, start, end, 0);
		}
		v.setText(ssb);
		v.setMovementMethod(ArticleMovementMethod.getInstance());
	}

}
