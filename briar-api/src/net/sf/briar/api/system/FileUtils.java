package net.sf.briar.api.system;

import java.io.File;
import java.io.IOException;

public interface FileUtils {

	long getFreeSpace(File f) throws IOException;
}
