package org.briarproject.briar.android.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
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

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.ArticleMovementMethod;
import org.briarproject.briar.android.widget.LinkDialogFragment;

import javax.annotation.Nullable;

import static android.content.Context.POWER_SERVICE;
import static android.content.Intent.CATEGORY_DEFAULT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
import static android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE;
import static android.text.format.DateUtils.FORMAT_ABBREV_TIME;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.WEEK_IN_MILLIS;
import static org.briarproject.briar.BuildConfig.APPLICATION_ID;
import static org.briarproject.briar.android.BriarApplication.EXPIRY_DATE;

public class UiUtils {

	public static final long MIN_DATE_RESOLUTION = MINUTE_IN_MILLIS;
	public static final int TEASER_LENGTH = 320;
	public static final float GREY_OUT = 0.5f;

	public static void setError(TextInputLayout til, @Nullable String error,
			boolean set) {
		if (set) {
			if (til.getError() == null) til.setError(error);
		} else {
			til.setError(null);
		}
	}

	public static String formatDate(Context ctx, long time) {
		int flags = FORMAT_ABBREV_RELATIVE |
				FORMAT_SHOW_DATE | FORMAT_ABBREV_TIME | FORMAT_ABBREV_MONTH;

		long diff = System.currentTimeMillis() - time;
		if (diff < MIN_DATE_RESOLUTION) return ctx.getString(R.string.now);
		if (diff >= DAY_IN_MILLIS && diff < WEEK_IN_MILLIS) {
			// also show time when older than a day, but newer than a week
			return DateUtils.getRelativeDateTimeString(ctx, time,
					MIN_DATE_RESOLUTION, WEEK_IN_MILLIS, flags).toString();
		}
		// otherwise just show "...ago" or date string
		return DateUtils.getRelativeTimeSpanString(time,
				System.currentTimeMillis(),
				MIN_DATE_RESOLUTION, flags).toString();
	}

	public static int getDaysUntilExpiry() {
		long now = System.currentTimeMillis();
		long daysBeforeExpiry = (EXPIRY_DATE - now) / 1000 / 60 / 60 / 24;
		return (int) daysBeforeExpiry;
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
			String url = span.getURL();
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

	public static String getAvatarTransitionName(ContactId c) {
		return "avatar" + c.getInt();
	}

	public static String getBulbTransitionName(ContactId c) {
		return "bulb" + c.getInt();
	}

	public static OnClickListener getGoToSettingsListener(Context context) {
		return (dialog, which) -> {
			Intent i = new Intent();
			i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
			i.addCategory(CATEGORY_DEFAULT);
			i.setData(Uri.parse("package:" + APPLICATION_ID));
			i.addFlags(FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(i);
		};
	}

	public static void showOnboardingDialog(Context ctx, String text) {
		new AlertDialog.Builder(ctx, R.style.OnboardingDialogTheme)
				.setMessage(text)
				.setNeutralButton(R.string.got_it,
						(dialog, which) -> dialog.cancel())
				.show();
	}

	public static boolean needsDozeWhitelisting(Context ctx) {
		if (Build.VERSION.SDK_INT < 23) return false;
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

}
