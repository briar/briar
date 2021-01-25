package org.briarproject.briar.android.account;

import android.content.Context;

import static org.briarproject.briar.android.account.HuaweiView.needsToBeShown;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;

class DozeHelperImpl implements DozeHelper {
	@Override
	public boolean needToShowDozeFragment(Context context) {
		return needsDozeWhitelisting(context.getApplicationContext()) ||
				needsToBeShown(context.getApplicationContext());
	}
}
