package org.briarproject.briar.android.socialbackup;

import org.briarproject.bramble.api.db.DbException;

import androidx.annotation.UiThread;

public interface ThresholdDefinedListener {

	@UiThread
	void thresholdDefined(int threshold) throws DbException;

}