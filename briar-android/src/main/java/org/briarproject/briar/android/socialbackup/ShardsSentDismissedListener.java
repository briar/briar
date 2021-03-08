package org.briarproject.briar.android.socialbackup;

import androidx.annotation.UiThread;

public interface ShardsSentDismissedListener {

	@UiThread
	void shardsSentDismissed();

}