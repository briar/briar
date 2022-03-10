package org.briarproject.briar.android.account;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.AttributeSet;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Arrays.asList;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@UiThread
@NotNullByDefault
class HuaweiAppLaunchView extends PowerView {

	private static final Logger LOG =
			getLogger(HuaweiAppLaunchView.class.getName());

	private final static String PACKAGE_NAME = "com.huawei.systemmanager";
	// First try to open StartupNormalAppListActivity
	private final static String CLASS_NAME_1 =
			PACKAGE_NAME + ".startupmgr.ui.StartupNormalAppListActivity";
	// Fall back to HwPowerManagerActivity
	private final static String CLASS_NAME_2 =
			PACKAGE_NAME + ".power.ui.HwPowerManagerActivity";

	public HuaweiAppLaunchView(Context context) {
		this(context, null);
	}

	public HuaweiAppLaunchView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public HuaweiAppLaunchView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setText(R.string.setup_huawei_app_launch_text);
		setButtonText(R.string.setup_huawei_app_launch_button);
	}

	@Override
	public boolean needsToBeShown() {
		return needsToBeShown(getContext());
	}

	public static boolean needsToBeShown(Context context) {
		// "App launch" was introduced in EMUI 8 (Android 8.0)
		if (SDK_INT < 26) return false;
		PackageManager pm = context.getPackageManager();
		for (Intent i : getIntents()) {
			if (!pm.queryIntentActivities(i, MATCH_DEFAULT_ONLY).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	@StringRes
	protected int getHelpText() {
		return R.string.setup_huawei_app_launch_help;
	}

	@Override
	protected void onButtonClick() {
		Context context = getContext();
		for (Intent i : getIntents()) {
			try {
				context.startActivity(i);
				setChecked(true);
				return;
			} catch (Exception e) {
				logException(LOG, WARNING, e);
			}
		}
		Toast.makeText(context, R.string.setup_huawei_app_launch_error_toast,
				LENGTH_LONG).show();
		// Let the user continue with setup
		setChecked(true);
	}

	private static List<Intent> getIntents() {
		Intent intent1 = new Intent();
		intent1.setClassName(PACKAGE_NAME, CLASS_NAME_1);
		Intent intent2 = new Intent();
		intent2.setClassName(PACKAGE_NAME, CLASS_NAME_2);
		return asList(intent1, intent2);
	}
}
