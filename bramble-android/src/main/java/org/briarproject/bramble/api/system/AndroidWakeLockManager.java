package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

@NotNullByDefault
public interface AndroidWakeLockManager {

	AndroidWakeLock createWakeLock();

	void runWakefully(Runnable r);

	void executeWakefully(Runnable r, Executor executor);
}
