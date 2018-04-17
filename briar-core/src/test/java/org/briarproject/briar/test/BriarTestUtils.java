package org.briarproject.briar.test;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;

public class BriarTestUtils {

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

	public static Author getRealAuthor(AuthorFactory authorFactory) {
		return authorFactory.createAuthor(getRandomString(5),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
	}

}
