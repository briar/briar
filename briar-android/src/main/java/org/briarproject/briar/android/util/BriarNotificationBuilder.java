package org.briarproject.briar.android.util;

import android.content.Context;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.briarproject.briar.R;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PRIVATE;
import static android.support.v4.app.NotificationCompat.VISIBILITY_SECRET;


public class BriarNotificationBuilder extends NotificationCompat.Builder {

	public BriarNotificationBuilder(Context context, String channelId) {
		super(context, channelId);
		// Auto-cancel does not fire the delete intent, see
		// https://issuetracker.google.com/issues/36961721
		setAutoCancel(true);

		setLights(ContextCompat.getColor(context, R.color.briar_green_light),
				750, 500);
	}

	public BriarNotificationBuilder setColorRes(@ColorRes int res) {
		setColor(ContextCompat.getColor(mContext, res));
		return this;
	}

	public BriarNotificationBuilder setLockscreenVisibility(String category,
			boolean show) {
		if (Build.VERSION.SDK_INT >= 21) {
			setCategory(category);
			if (show) setVisibility(VISIBILITY_PRIVATE);
			else setVisibility(VISIBILITY_SECRET);
		}
		return this;
	}

}
