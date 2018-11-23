package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public class OsUtils {

	@Nullable
	private static final String os = System.getProperty("os.name");
	@Nullable
	private static final String vendor = System.getProperty("java.vendor");

	public static boolean isWindows() {
		return os != null && os.contains("Windows");
	}

	public static boolean isMac() {
		return os != null && os.contains("Mac OS");
	}

	public static boolean isLinux() {
		return os != null && os.contains("Linux") && !isAndroid();
	}

	public static boolean isAndroid() {
		return vendor != null && vendor.contains("Android");
	}
}
