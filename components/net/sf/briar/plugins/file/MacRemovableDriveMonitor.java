package net.sf.briar.plugins.file;

class MacRemovableDriveMonitor extends UnixRemovableDriveMonitor {

	@Override
	protected String[] getPathsToWatch() {
		return new String[] { "/Volumes" };
	}
}
