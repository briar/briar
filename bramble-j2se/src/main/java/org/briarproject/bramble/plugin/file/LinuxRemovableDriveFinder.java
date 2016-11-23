package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
class LinuxRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/bin/mount";
	}

	@Override
	@Nullable
	protected String parseMountPoint(String line) {
		// The format is "/dev/foo on /bar/baz type bam (opt1,opt2)"
		String pattern = "^/dev/[^ ]+ on (.*) type [^ ]+ \\([^)]+\\)$";
		String path = line.replaceFirst(pattern, "$1");
		return path.equals(line) ? null : path;
	}

	@Override
	protected boolean isRemovableDriveMountPoint(String path) {
		return path.startsWith("/mnt/") || path.startsWith("/media/");
	}
}
