package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

@NotNullByDefault
public interface AndroidWakeLockManager {

	AndroidWakeLock createWakeLock(String tag);

	void runWakefully(Runnable r, String tag);

	void executeWakefully(Runnable r, Executor executor, String tag);
}
