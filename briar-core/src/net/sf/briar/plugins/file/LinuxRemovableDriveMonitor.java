package net.sf.briar.plugins.file;

class LinuxRemovableDriveMonitor extends UnixRemovableDriveMonitor {

	@Override
	protected String[] getPathsToWatch() {
		return new String[] { "/mnt", "/media" };
	}
}
