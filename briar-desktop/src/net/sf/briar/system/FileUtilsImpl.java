package net.sf.briar.system;

import java.io.File;
import java.io.IOException;

import net.sf.briar.api.system.FileUtils;

class FileUtilsImpl implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		return f.getFreeSpace();
	}
}
