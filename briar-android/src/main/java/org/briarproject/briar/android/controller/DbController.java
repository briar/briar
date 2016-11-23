package org.briarproject.briar.android.controller;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface DbController {

	void runOnDbThread(Runnable task);
}
