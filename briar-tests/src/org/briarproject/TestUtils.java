package org.briarproject;

import org.briarproject.api.UniqueId;
import org.briarproject.util.FileUtils;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.SecretKey;

public class TestUtils {

	private static final AtomicInteger nextTestDir =
			new AtomicInteger((int) (Math.random() * 1000 * 1000));
	private static final Random random = new Random();

	public static File getTestDirectory() {
		int name = nextTestDir.getAndIncrement();
		return new File("test.tmp/" + name);
	}

	public static void deleteTestDirectory(File testDir) {
		FileUtils.deleteFileOrDir(testDir);
		testDir.getParentFile().delete(); // Delete if empty
	}

	public static byte[] getRandomId() {
		byte[] b = new byte[UniqueId.LENGTH];
		random.nextBytes(b);
		return b;
	}

	public static String createRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++)
			c[i] = (char) ('a' + random.nextInt(26));
		return new String(c);
	}

	public static SecretKey createSecretKey() {
		byte[] b = new byte[SecretKey.LENGTH];
		random.nextBytes(b);
		return new SecretKey(b);
	}
}
