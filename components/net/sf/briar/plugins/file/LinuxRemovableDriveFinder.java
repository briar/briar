package net.sf.briar.plugins.file;

class LinuxRemovableDriveFinder extends UnixRemovableDriveFinder {

	@Override
	protected String getMountCommand() {
		return "/bin/mount";
	}

	@Override
	protected String parseMountPoint(String line) {
		// The format is "/dev/foo on /bar/baz type bam (opt1,opt2)"
		line = line.replaceFirst("^/dev/[^ ]+ on ", "");
		return line.replaceFirst(" type [^ ]+ \\([^)]+\\)$", "");
	}

	@Override
	protected boolean isRemovableDriveMountPoint(String path) {
		return path.startsWith("/mnt/") || path.startsWith("/media/");
	}
}
