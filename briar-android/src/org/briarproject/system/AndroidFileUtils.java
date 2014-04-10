package org.briarproject.system;

import java.io.File;
import java.io.IOException;

import org.briarproject.api.system.FileUtils;

import android.os.Build;
import android.os.StatFs;

class AndroidFileUtils implements FileUtils {

	@SuppressWarnings("deprecation")
	public long getTotalSpace(File f) throws IOException {
		if(Build.VERSION.SDK_INT >= 9) return f.getTotalSpace();
		StatFs s = new StatFs(f.getAbsolutePath());
		// These deprecated methods are the best thing available for SDK < 9
		return (long) s.getBlockCount() * s.getBlockSize();
	}

	@SuppressWarnings("deprecation")
	public long getFreeSpace(File f) throws IOException {
		if(Build.VERSION.SDK_INT >= 9) return f.getUsableSpace();
		StatFs s = new StatFs(f.getAbsolutePath());
		// These deprecated methods are the best thing available for SDK < 9
		return (long) s.getAvailableBlocks() * s.getBlockSize();
	}
}
