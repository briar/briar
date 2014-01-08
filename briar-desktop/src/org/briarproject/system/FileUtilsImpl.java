package org.briarproject.system;

import java.io.File;
import java.io.IOException;

import org.briarproject.api.system.FileUtils;

class FileUtilsImpl implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		return f.getFreeSpace();
	}
}
