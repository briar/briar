package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.List;

interface RemovableDriveFinder {

	List<File> findRemovableDrives() throws IOException;
}
