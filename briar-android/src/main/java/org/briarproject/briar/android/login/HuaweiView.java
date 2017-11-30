package org.briarproject.briar.android.login;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.util.AttributeSet;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import java.util.List;

import javax.annotation.Nullable;

@UiThread
@NotNullByDefault
class HuaweiView extends PowerView {

	private final static String PACKAGE_NAME = "com.huawei.systemmanager";
	private final static String CLASS_NAME =
			PACKAGE_NAME + ".optimize.process.ProtectActivity";

	public HuaweiView(Context context) {
		this(context, null);
	}

	public HuaweiView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public HuaweiView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setText(R.string.setup_huawei_text);
		setButtonText(R.string.setup_huawei_button);
	}

	@Override
	public boolean needsToBeShown() {
		return needsToBeShown(getContext());
	}

	public static boolean needsToBeShown(Context context) {
		PackageManager pm = context.getPackageManager();
		List<ResolveInfo> resolveInfos = pm.queryIntentActivities(getIntent(),
				PackageManager.MATCH_DEFAULT_ONLY);
		return !resolveInfos.isEmpty();
	}

	@Override
	@StringRes
	protected int getHelpText() {
		return R.string.setup_huawei_help;
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
