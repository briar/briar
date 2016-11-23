package org.briarproject.bramble.plugin.file;

import com.sun.jna.platform.win32.Kernel32;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@NotNullByDefault
class WindowsRemovableDriveFinder implements RemovableDriveFinder {

	// http://msdn.microsoft.com/en-us/library/windows/desktop/aa364939.aspx
	private static final int DRIVE_REMOVABLE = 2;

	@Override
	public Collection<File> findRemovableDrives() throws IOException {
		File[] roots = File.listRoots();
		if (roots == null) throw new IOException();
		List<File> drives = new ArrayList<>();
		for (File root : roots) {
			try {
				int type = Kernel32.INSTANCE.GetDriveType(root.getPath());
				if (type == DRIVE_REMOVABLE) drives.add(root);
			} catch (RuntimeException e) {
				throw new IOException(e);
			}
		}
		return drives;
	}
}
