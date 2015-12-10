package org.briarproject.system;

import org.briarproject.api.system.FileUtils;

import java.io.File;
import java.io.IOException;

public class FileUtilsImpl implements FileUtils {

    public long getTotalSpace(File f) throws IOException {
        return f.getTotalSpace(); // Requires Java 1.6
    }

    public long getFreeSpace(File f) throws IOException {
        return f.getUsableSpace(); // Requires Java 1.6
    }

    public void deleteFileOrDir(File f) {
        if (f.isFile())
            f.delete();
        else if (f.isDirectory())
            for (File child : f.listFiles())
                deleteFileOrDir(child);
    }
}
