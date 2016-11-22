package org.briarproject;

import junit.framework.Assert;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumManagerTest extends BriarIntegrationTest {

	private ForumManager forumManager0, forumManager1;
	private ForumSharingManager forumSharingManager0, forumSharingManager1;
	private Forum forum0;
	private GroupId g;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		forumManager0 = c0.getForumManager();
		forumManager1 = c1.getForumManager();
		forumSharingManager0 = c0.getForumSharingManager();
		forumSharingManager1 = c1.getForumSharingManager();


		forum0 = forumManager0.addForum("Test Forum");
		g = forum0.getId();
		// share forum
		forumSharingManager0.sendInvitation(g, contactId1From0, null);
		sync0To1(1, true);
		forumSharingManager1.respondToInvitation(forum0, contact0From1, true);
		sync1To0(1, true);
	}

	private ForumPost createForumPost(GroupId groupId,
			@Nullable ForumPost parent, String body, long ms) throws Exception {
		return forumPostFactory.createPost(groupId, ms,
				parent == null ? null : parent.getMessage().getId(),
				author0, body);
	}

	@Test
	public void testForumPost() throws Exception {
		assertEquals(1, forumManager0.getForums().size());
		final long ms1 = clock.currentTimeMillis() - 1000L;
		final String body1 = "some forum text";
		final long ms2 = clock.currentTimeMillis();
		final String body2 = "some other forum text";
		ForumPost post1 =
				createForumPost(forum0.getGroup().getId(), null, body1, ms1);
		assertEquals(ms1, post1.getMessage().getTimestamp());
		ForumPost post2 =
				createForumPost(forum0.getGroup().getId(), post1, body2, ms2);
		assertEquals(ms2, post2.getMessage().getTimestamp());
		forumManager0.addLocalPost(post1);
		forumManager0.setReadFlag(forum0.getGroup().getId(),
				post1.getMessage().getId(), true);
		assertGroupCount(messageTracker0, forum0.getGroup().getId(), 1, 0,
				post1.getMessage().getTimestamp());
		forumManager0.addLocalPost(post2);
		forumManager0.setReadFlag(forum0.getGroup().getId(),
				post2.getMessage().getId(), false);
		assertGroupCount(messageTracker0, forum0.getGroup().getId(), 2, 1,
				post2.getMessage().getTimestamp());
		forumManager0.setReadFlag(forum0.getGroup().getId(),
				post2.getMessage().getId(), false);
		assertGroupCount(messageTracker0, forum0.getGroup().getId(), 2, 1,
				post2.getMessage().getTimestamp());
		Collection<ForumPostHeader> headers =
				forumManager0.getPostHeaders(forum0.getGroup().getId());
		assertEquals(2, headers.size());
		for (ForumPostHeader h : headers) {
			final String hBody = forumManager0.getPostBody(h.getId());

			boolean isPost1 = h.getId().equals(post1.getMessage().getId());
			boolean isPost2 = h.getId().equals(post2.getMessage().getId());
			Assert.assertTrue(isPost1 || isPost2);
			if (isPost1) {
				assertEquals(h.getTimestamp(), ms1);
				assertEquals(body1, hBody);
				assertNull(h.getParentId());
				assertTrue(h.isRead());
			}
			else {
				assertEquals(h.getTimestamp(), ms2);
				assertEquals(body2, hBody);
				assertEquals(h.getParentId(), post2.getParent());
				assertFalse(h.isRead());
			}
		}
		forumManager0.removeForum(forum0);
		assertEquals(0, forumManager0.getForums().size());
	}

	@Test
	public void testForumPostDelivery() throws Exception {
		// add one forum post
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g, null, "a", time);
		forumManager0.addLocalPost(post1);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());
		assertGroupCount(messageTracker0, g, 1, 0, time);
		assertGroupCount(messageTracker1, g, 0, 0, 0);

		// send post to 1
		sync0To1(1, true);
		assertEquals(1, forumManager1.getPostHeaders(g).size());
		assertGroupCount(messageTracker1, g, 1, 1, time);

		// add another forum post
		long time2 = clock.currentTimeMillis();
		ForumPost post2 = createForumPost(g, null, "b", time2);
		forumManager1.addLocalPost(post2);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(2, forumManager1.getPostHeaders(g).size());
		assertGroupCount(messageTracker0, g, 1, 0, time);
		assertGroupCount(messageTracker1, g, 2, 1, time2);

		// send post to 0
		sync1To0(1, true);
		assertEquals(2, forumManager1.getPostHeaders(g).size());
		assertGroupCount(messageTracker0, g, 2, 1, time2);
	}

	@Test
	public void testForumPostDeliveredAfterParent() throws Exception {
		// add one forum post without the parent
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g, null, "a", time);
		ForumPost post2 = createForumPost(g, post1, "a", time);
		forumManager0.addLocalPost(post2);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// send post to 1 without waiting for message delivery
		sync0To1(1, false);
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// now add the parent post as well
		forumManager0.addLocalPost(post1);
		assertEquals(2, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// and send it over to 1 and wait for a second message to be delivered
		sync0To1(2, true);
		assertEquals(2, forumManager1.getPostHeaders(g).size());
	}

	@Test
	public void testForumPostWithParentInOtherGroup() throws Exception {
		// share a second forum
		Forum forum1 = forumManager0.addForum("Test Forum1");
		GroupId g1 = forum1.getId();
		forumSharingManager0.sendInvitation(g1, contactId1From0, null);
		sync0To1(1, true);
		forumSharingManager1.respondToInvitation(forum1, contact0From1, true);
		sync1To0(1, true);

		// add one forum post with a parent in another forum
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g1, null, "a", time);
		ForumPost post = createForumPost(g, post1, "b", time);
		forumManager0.addLocalPost(post);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// send the child post to 1
		sync0To1(1, false);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// now also add the parent post which is in another group
		forumManager0.addLocalPost(post1);
		assertEquals(1, forumManager0.getPostHeaders(g1).size());
		assertEquals(0, forumManager1.getPostHeaders(g1).size());

		// send posts to 1
		sync0To1(1, true);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(1, forumManager0.getPostHeaders(g1).size());
		// the next line is critical, makes sure post doesn't show up
		assertEquals(0, forumManager1.getPostHeaders(g).size());
		assertEquals(1, forumManager1.getPostHeaders(g1).size());
	}

}
