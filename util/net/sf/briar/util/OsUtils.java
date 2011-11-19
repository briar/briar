package net.sf.briar.util;

public class OsUtils {

	private static final String os = System.getProperty("os.name");
	private static final String version = System.getProperty("os.version");

	public static boolean isWindows() {
		return os.indexOf("Windows") != -1;
	}

	public static boolean isMac() {
		return os.indexOf("Mac OS") != -1;
	}

	public static boolean isMacLeopardOrNewer() {
		if(!isMac() || version == null) return false;
		try {
			String[] v = version.split("\\.");
			if(v.length != 3) return false;
			int major = Integer.parseInt(v[0]);
			int minor = Integer.parseInt(v[1]);
			return major >= 10 && minor >= 5;
		} catch(NumberFormatException e) {
			return false;
		}
	}

	public static boolean isLinux() {
		return os.indexOf("Linux") != -1;
	}
}
