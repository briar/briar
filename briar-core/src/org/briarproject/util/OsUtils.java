package org.briarproject.util;

public class OsUtils {

	private static final String os = System.getProperty("os.name");
	private static final String version = System.getProperty("os.version");
	private static final String vendor = System.getProperty("java.vendor");

	public static boolean isWindows() {
		return os != null && os.indexOf("Windows") != -1;
	}

	public static boolean isMac() {
		return os != null && os.indexOf("Mac OS") != -1;
	}

	public static boolean isMacLeopardOrNewer() {
		if (!isMac() || version == null) return false;
		try {
			String[] v = version.split("\\.");
			if (v.length != 3) return false;
			int major = Integer.parseInt(v[0]);
			int minor = Integer.parseInt(v[1]);
			return major >= 10 && minor >= 5;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean isLinux() {
		return os != null && os.indexOf("Linux") != -1 && !isAndroid();
	}

	public static boolean isAndroid() {
		return vendor != null && vendor.indexOf("Android") != -1;
	}
}
