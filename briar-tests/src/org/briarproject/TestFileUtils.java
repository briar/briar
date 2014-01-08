package org.briarproject;

import java.io.File;
import java.io.IOException;

import org.briarproject.api.system.FileUtils;

public class TestFileUtils implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		return f.getFreeSpace();
	}
}
