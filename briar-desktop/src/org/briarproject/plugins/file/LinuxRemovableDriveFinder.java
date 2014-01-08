package org.briarproject.plugins.file;

class LinuxRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/bin/mount";
	}

	@Override
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
