package org.briarproject;

import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.IoUtils;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

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

	public static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount, long latestMsgTime)
			throws DbException {
		GroupCount groupCount = tracker.getGroupCount(g);
		assertEquals(msgCount, groupCount.getMsgCount());
		assertEquals(unreadCount, groupCount.getUnreadCount());
		assertEquals(latestMsgTime, groupCount.getLatestMsgTime());
	}

	public static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount) throws	DbException {
		GroupCount c1 = tracker.getGroupCount(g);
		assertEquals(msgCount, c1.getMsgCount());
		assertEquals(unreadCount, c1.getUnreadCount());
	}
}
