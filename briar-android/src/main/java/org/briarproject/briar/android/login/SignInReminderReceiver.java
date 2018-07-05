package org.briarproject.briar.android.login;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.settings.SettingsActivity;

import javax.inject.Inject;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.app.NotificationCompat.PRIORITY_LOW;
import static android.support.v4.app.NotificationCompat.VISIBILITY_SECRET;
import static org.briarproject.briar.android.TestingConstants.FEATURE_FLAG_SIGN_IN_REMINDER;
import static org.briarproject.briar.android.settings.SettingsActivity.NO_NOTIFY_SIGN_IN;
import static org.briarproject.briar.android.settings.SettingsFragment.NOTIFY_SIGN_IN;
import static org.briarproject.briar.api.android.AndroidNotificationManager.REMINDER_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.REMINDER_NOTIFICATION_ID;

public class SignInReminderReceiver extends BroadcastReceiver {

	@Inject
	DatabaseConfig databaseConfig;

	@Override
	public void onReceive(Context ctx, Intent intent) {
		if (!FEATURE_FLAG_SIGN_IN_REMINDER) return;

		BriarApplication app = (BriarApplication) ctx.getApplicationContext();
		AndroidComponent applicationComponent = app.getApplicationComponent();
		applicationComponent.inject(this);

		String action = intent.getAction();
		if (action == null) return;
		if (action.equals(ACTION_BOOT_COMPLETED) ||
				action.equals(ACTION_MY_PACKAGE_REPLACED)) {
			if (databaseConfig.databaseExists()) {
				SharedPreferences prefs = app.getDefaultSharedPreferences();
				if (prefs.getBoolean(NOTIFY_SIGN_IN, true)) {
					showSignInNotification(ctx);
				}
			}
		}
	}

	private void showSignInNotification(Context ctx) {
		NotificationManager nm = (NotificationManager)
				ctx.getSystemService(NOTIFICATION_SERVICE);
		if (nm == null) return;

		if (SDK_INT >= 26) {
			NotificationChannel channel =
					new NotificationChannel(REMINDER_CHANNEL_ID, ctx.getString(
							R.string.reminder_notification_channel_title),
							IMPORTANCE_LOW);
			channel.setLockscreenVisibility(VISIBILITY_SECRET);
			nm.createNotificationChannel(channel);
		}

		NotificationCompat.Builder b =
				new NotificationCompat.Builder(ctx, REMINDER_CHANNEL_ID);
		b.setSmallIcon(R.drawable.notification_reminder);
		b.setColor(ContextCompat.getColor(ctx, R.color.briar_primary));
		b.setContentTitle(ctx.getText(R.string.reminder_notification_title));
		b.setContentText(ctx.getText(R.string.reminder_notification_text));
		b.setAutoCancel(true);
		b.setWhen(0); // Don't show the time
		b.setPriority(PRIORITY_LOW);

		// Add a 'Do not show sign-in reminder' action
		String actionTitle =
				ctx.getString(R.string.reminder_notification_do_not_show_again);
		Intent i1 = new Intent(ctx, SettingsActivity.class);
		i1.setAction(NO_NOTIFY_SIGN_IN);
		PendingIntent actionIntent = PendingIntent.getActivity(ctx, 0, i1, 0);
		b.addAction(0, actionTitle, actionIntent);

		Intent i = new Intent(ctx, NavDrawerActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		b.setContentIntent(PendingIntent.getActivity(ctx, 0, i, 0));

		nm.notify(REMINDER_NOTIFICATION_ID, b.build());
	}

}
