package org.briarproject.api.system;

import java.io.File;
import java.io.IOException;

public interface FileUtils {

	long getTotalSpace(File f) throws IOException;

	long getFreeSpace(File f) throws IOException;
}
