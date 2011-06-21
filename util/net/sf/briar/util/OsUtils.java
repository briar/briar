package net.sf.briar.util;

public class OsUtils {

	private static final String os = System.getProperty("os.name");

	public static boolean isWindows() {
		return os.indexOf("Windows") != -1;
	}

	public static boolean isMac() {
		return os.indexOf("Mac OS") != -1;
	}

	public static boolean isLinux() {
		return os.indexOf("Linux") != -1;
	}
}
