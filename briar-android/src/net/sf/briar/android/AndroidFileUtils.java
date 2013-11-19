package net.sf.briar.android;

import java.io.File;
import java.io.IOException;

import net.sf.briar.api.os.FileUtils;
import android.os.Build;
import android.os.StatFs;

class AndroidFileUtils implements FileUtils {

	public long getFreeSpace(File f) throws IOException {
		StatFs s = new StatFs(f.getAbsolutePath());
		if(Build.VERSION.SDK_INT >= 18)
			return s.getAvailableBlocksLong() * s.getBlockSizeLong();
		return (long) s.getAvailableBlocks() * s.getBlockSize();
	}
}
