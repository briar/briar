package org.briarproject.bramble.test;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.util.IoUtils;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUtils {

	private static final AtomicInteger nextTestDir =
			new AtomicInteger((int) (Math.random() * 1000 * 1000));
	private static final Random random = new Random();

	public static File getTestDirectory() {
		int name = nextTestDir.getAndIncrement();
		return new File("test.tmp/" + name);
	}

	public static void deleteTestDirectory(File testDir) {
		IoUtils.deleteFileOrDir(testDir);
		testDir.getParentFile().delete(); // Delete if empty
	}

	public static byte[] getRandomBytes(int length) {
		byte[] b = new byte[length];
		random.nextBytes(b);
		return b;
	}

	public static byte[] getRandomId() {
		return getRandomBytes(UniqueId.LENGTH);
	}

	public static String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++)
			c[i] = (char) ('a' + random.nextInt(26));
		return new String(c);
	}

	public static SecretKey getSecretKey() {
		return new SecretKey(getRandomBytes(SecretKey.LENGTH));
	}

}
