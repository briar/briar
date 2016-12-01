package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

@NotNullByDefault
interface RemovableDriveFinder {

	Collection<File> findRemovableDrives() throws IOException;
}
