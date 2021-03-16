package org.briarproject.briar.android.account;

import androidx.annotation.UiThread;

public interface RecoverAccountListener {
	@UiThread
	void recoverAccountChosen();
}
