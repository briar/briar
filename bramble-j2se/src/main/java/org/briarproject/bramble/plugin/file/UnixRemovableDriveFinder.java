package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.annotation.Nullable;

@NotNullByDefault
abstract class UnixRemovableDriveFinder implements RemovableDriveFinder {

	protected abstract String getMountCommand();

	@Nullable
	protected abstract String parseMountPoint(String line);

	protected abstract boolean isRemovableDriveMountPoint(String path);

	@Override
	public List<File> findRemovableDrives() throws IOException {
		List<File> drives = new ArrayList<>();
		Process p = new ProcessBuilder(getMountCommand()).start();
		Scanner s = new Scanner(p.getInputStream(), "UTF-8");
		try {
			while (s.hasNextLine()) {
				String line = s.nextLine();
				String[] tokens = line.split(" ");
				if (tokens.length < 3) continue;
				// The general format is "/dev/foo on /bar/baz ..."
				if (tokens[0].startsWith("/dev/") && tokens[1].equals("on")) {
					// The path may contain spaces so we can't use tokens[2]
					String path = parseMountPoint(line);
					if (path != null && isRemovableDriveMountPoint(path)) {
						File f = new File(path);
						if (f.exists() && f.isDirectory()) drives.add(f);
					}
				}
			}
		} finally {
			s.close();
		}
		return drives;
	}
}
