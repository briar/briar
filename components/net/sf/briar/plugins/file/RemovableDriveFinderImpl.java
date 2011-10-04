package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.sf.briar.util.OsUtils;

class RemovableDriveFinderImpl implements RemovableDriveFinder {

	private final LinuxRemovableDriveFinder linux =
		new LinuxRemovableDriveFinder();
	private final MacRemovableDriveFinder mac =
		new MacRemovableDriveFinder();
	private final WindowsRemovableDriveFinder windows =
		new WindowsRemovableDriveFinder();

	public List<File> findRemovableDrives() throws IOException {
		if(OsUtils.isLinux()) return linux.findRemovableDrives();
		else if(OsUtils.isMac()) return mac.findRemovableDrives();
		else if(OsUtils.isWindows()) return windows.findRemovableDrives();
		else return Collections.emptyList();
	}
}
