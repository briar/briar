package org.briarproject.briar.android.util;

import android.content.Context;
import androidx.annotation.ColorRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.briarproject.briar.R;

import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE;


public class BriarNotificationBuilder extends NotificationCompat.Builder {

	public BriarNotificationBuilder(Context context, String channelId) {
		super(context, channelId);
		// Auto-cancel does not fire the delete intent, see
		// https://issuetracker.google.com/issues/36961721
		setAutoCancel(true);

		setLights(ContextCompat.getColor(context, R.color.briar_green_light),
				750, 500);
		if (SDK_INT >= 21) setVisibility(VISIBILITY_PRIVATE);
	}

	public BriarNotificationBuilder setColorRes(@ColorRes int res) {
		setColor(ContextCompat.getColor(mContext, res));
		return this;
	}

	public BriarNotificationBuilder setNotificationCategory(String category) {
		if (SDK_INT >= 21) setCategory(category);
		return this;
	}

}
