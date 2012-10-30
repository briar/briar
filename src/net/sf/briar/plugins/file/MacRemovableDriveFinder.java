package net.sf.briar.plugins.file;

class MacRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/sbin/mount";
	}

	@Override
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
