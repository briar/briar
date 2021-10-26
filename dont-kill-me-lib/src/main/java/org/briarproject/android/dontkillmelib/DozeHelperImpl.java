package org.briarproject.android.dontkillmelib;

import android.content.Context;

import static org.briarproject.android.dontkillmelib.PowerUtils.needsDozeWhitelisting;

public class DozeHelperImpl implements DozeHelper {
	@Override
	public boolean needToShowDoNotKillMeFragment(Context context) {
		Context appContext = context.getApplicationContext();
		return needsDozeWhitelisting(appContext) ||
				HuaweiProtectedAppsView.needsToBeShown(appContext) ||
				HuaweiAppLaunchView.needsToBeShown(appContext) ||
				XiaomiView.isXiaomiOrRedmiDevice();
	}
}
