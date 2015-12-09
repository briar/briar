package org.briarproject;

import org.briarproject.api.system.FileUtils;

import java.io.File;
import java.io.IOException;

public class TestFileUtils implements FileUtils {

	public long getTotalSpace(File f) throws IOException {
		return f.getTotalSpace();
	}

	public long getFreeSpace(File f) throws IOException {
		return f.getUsableSpace();
	}
}
