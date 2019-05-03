package org.briarproject.briar.android;

import android.app.Activity;
import android.content.SharedPreferences;

import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import java.util.Collection;
import java.util.logging.LogRecord;

/**
 * This exists so that the Application object will not necessarily be cast
 * directly to the Briar application object.
 */
public interface BriarApplication {

	Class<? extends Activity> ENTRY_ACTIVITY = NavDrawerActivity.class;

	Collection<LogRecord> getRecentLogRecords();

	AndroidComponent getApplicationComponent();

	SharedPreferences getDefaultSharedPreferences();

	boolean isRunningInBackground();
}
