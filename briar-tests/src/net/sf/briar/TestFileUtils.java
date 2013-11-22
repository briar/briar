package net.sf.briar;

import java.io.File;
import java.io.IOException;

import net.sf.briar.api.os.FileUtils;

public class TestFileUtils implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		return f.getFreeSpace();
	}
}
