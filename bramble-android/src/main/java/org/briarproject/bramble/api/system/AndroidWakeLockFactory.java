package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AndroidWakeLockFactory {

	AndroidWakeLock createWakeLock();
}
