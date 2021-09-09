package org.briarproject.briar.android.account;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.AttributeSet;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import java.util.List;

import javax.annotation.Nullable;

import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;

@UiThread
@NotNullByDefault
class HuaweiAppLaunchView extends PowerView {

	private final static String PACKAGE_NAME = "com.huawei.systemmanager";
	private final static String CLASS_NAME =
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
		List<ResolveInfo> resolveInfos = pm.queryIntentActivities(getIntent(),
				MATCH_DEFAULT_ONLY);
		return !resolveInfos.isEmpty();
	}

	@Override
	@StringRes
	protected int getHelpText() {
		return R.string.setup_huawei_app_launch_help;
	}

	@Override
	protected void onButtonClick() {
		getContext().startActivity(getIntent());
		setChecked(true);
	}

	private static Intent getIntent() {
		Intent intent = new Intent();
		intent.setClassName(PACKAGE_NAME, CLASS_NAME);
		return intent;
	}

}
