package org.briarproject;

import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import static org.junit.Assert.assertEquals;

public abstract class BriarIntegrationTest extends BriarTestCase {

	// TODO maybe we could add uncaught exception handlers for other threads here (#670)

	protected void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount, long latestMsg)
			throws DbException {

		GroupCount groupCount = tracker.getGroupCount(g);
		assertEquals(msgCount, groupCount.getMsgCount());
		assertEquals(unreadCount, groupCount.getUnreadCount());
		assertEquals(latestMsg, groupCount.getLatestMsgTime());
	}

	protected void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount) throws	DbException {

		GroupCount c1 = tracker.getGroupCount(g);
		assertEquals(msgCount, c1.getMsgCount());
		assertEquals(unreadCount, c1.getUnreadCount());
	}

}
