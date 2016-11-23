package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
class MacRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/sbin/mount";
	}

	@Override
	@Nullable
	protected String parseMountPoint(String line) {
		// The format is "/dev/foo on /bar/baz (opt1, opt2)"
		String pattern = "^/dev/[^ ]+ on (.*) \\([^)]+\\)$";
		String path = line.replaceFirst(pattern, "$1");
		return path.equals(line) ? null : path;
	}

	@Override
	protected boolean isRemovableDriveMountPoint(String path) {
		return path.startsWith("/Volumes/");
	}
}
