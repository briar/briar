package org.briarproject.briar.android;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.logging.LogRecord;

/**
 * This exists so that the Application object will not necessarily be cast
 * directly to the Briar application object.
 */
public interface BriarApplication {

	Collection<LogRecord> getRecentLogRecords();

	AndroidComponent getApplicationComponent();

	SharedPreferences getDefaultSharedPreferences();
}
