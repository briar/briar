package net.sf.briar.plugins.file;

class MacRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/sbin/mount";
	}

	@Override
	protected String parseMountPoint(String line) {
		// The format is "/dev/foo on /bar/baz (opt1, opt2)"
		line = line.replaceFirst("^/dev/[^ ]+ on ", "");
		return line.replaceFirst(" \\([^)]+\\)$", "");
	}

	@Override
	protected boolean isRemovableDriveMountPoint(String path) {
		return path.startsWith("/Volumes/");
	}
}
