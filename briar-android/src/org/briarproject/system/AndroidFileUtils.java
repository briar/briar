package org.briarproject.system;

import org.briarproject.api.system.FileUtils;

import java.io.File;
import java.io.IOException;

public class AndroidFileUtils implements FileUtils {

	public long getTotalSpace(File f) throws IOException {
		return f.getTotalSpace();
	}

	public long getFreeSpace(File f) throws IOException {
		return f.getUsableSpace();
	}
}
