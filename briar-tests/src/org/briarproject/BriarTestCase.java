package org.briarproject;

import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public abstract class BriarTestCase {

	public BriarTestCase() {
		// Ensure exceptions thrown on worker threads cause tests to fail
		UncaughtExceptionHandler fail = new UncaughtExceptionHandler() {
			public void uncaughtException(Thread thread, Throwable throwable) {
				throwable.printStackTrace();
				fail();
			}
		};
		Thread.setDefaultUncaughtExceptionHandler(fail);
	}

	protected static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount, long latestMsg)
			throws DbException {

		MessageTracker.GroupCount groupCount = tracker.getGroupCount(g);
		assertEquals(msgCount, groupCount.getMsgCount());
		assertEquals(unreadCount, groupCount.getUnreadCount());
		assertEquals(latestMsg, groupCount.getLatestMsgTime());
	}

	protected static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount) throws	DbException {

		MessageTracker.GroupCount c1 = tracker.getGroupCount(g);
		assertEquals(msgCount, c1.getMsgCount());
		assertEquals(unreadCount, c1.getUnreadCount());
	}
}
