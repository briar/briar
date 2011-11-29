package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

interface RemovableDriveFinder {

	Collection<File> findRemovableDrives() throws IOException;
}
