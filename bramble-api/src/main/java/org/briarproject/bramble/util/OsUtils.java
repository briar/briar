package org.briarproject.bramble.util;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import static org.briarproject.bramble.util.StringUtils.startsWithIgnoreCase;

@NotNullByDefault
public class OsUtils {

	@Nullable
	private static final String os = System.getProperty("os.name");
	@Nullable
	private static final String vendor = System.getProperty("java.vendor");

	public static boolean isWindows() {
		return os != null && startsWithIgnoreCase(os, "Win");
	}

	public static boolean isMac() {
		return os != null && os.equalsIgnoreCase("Mac OS X");
	}

	public static boolean isLinux() {
		return os != null && startsWithIgnoreCase(os, "Linux") && !isAndroid();
	}

	public static boolean isAndroid() {
		return vendor != null && vendor.contains("Android");
	}
}
