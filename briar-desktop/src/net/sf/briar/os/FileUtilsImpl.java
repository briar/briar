package net.sf.briar.os;

import java.io.File;
import java.io.IOException;

import net.sf.briar.api.os.FileUtils;

import org.apache.commons.io.FileSystemUtils;

class FileUtilsImpl implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		return FileSystemUtils.freeSpaceKb(f.getAbsolutePath()) * 1024;
	}
}
