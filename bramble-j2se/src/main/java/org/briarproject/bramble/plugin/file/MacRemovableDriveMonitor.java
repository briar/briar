package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class MacRemovableDriveMonitor extends UnixRemovableDriveMonitor {

	@Override
	protected String[] getPathsToWatch() {
		return new String[] {"/Volumes"};
	}
}
