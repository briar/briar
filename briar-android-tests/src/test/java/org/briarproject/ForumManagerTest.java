package org.briarproject;

import junit.framework.Assert;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.briarproject.util.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;

import javax.inject.Inject;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumManagerTest {

	@Inject
	protected ForumManager forumManager;
	@Inject
	protected ForumPostFactory forumPostFactory;
	@Inject
	protected DatabaseComponent db;

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() throws Exception {

		assertTrue(testDir.mkdirs());
		File tDir = new File(testDir, "db");

		ForumManagerTestComponent component =
				DaggerForumManagerTestComponent.builder()
						.testDatabaseModule(new TestDatabaseModule(tDir))
						.build();

		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new ForumModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
		component.inject(this);
	}

	ForumPost createForumPost(GroupId groupId, ForumPost parent, String body,
			long ms)
			throws Exception {
		return forumPostFactory.createAnonymousPost(groupId, ms,
				parent == null ? null : parent.getMessage().getId(),
				"text/plain", StringUtils.toUtf8(body));
	}

	@Test
	public void testForumPost() throws Exception {
		assertFalse(db.open());
		assertNotNull(forumManager);
		Forum forum = forumManager.addForum("TestForum");
		assertEquals(1, forumManager.getForums().size());
		final long ms1 = System.currentTimeMillis() - 1000L;
		final String body1 = "some forum text";
		final long ms2 = System.currentTimeMillis();
		final String body2 = "some other forum text";
		ForumPost post1 =
				createForumPost(forum.getGroup().getId(), null, body1, ms1);
		assertEquals(ms1, post1.getMessage().getTimestamp());
		ForumPost post2 =
				createForumPost(forum.getGroup().getId(), post1, body2, ms2);
		assertEquals(ms2, post2.getMessage().getTimestamp());
		forumManager.addLocalPost(post1);
		forumManager.setReadFlag(post1.getMessage().getId(), true);
		forumManager.addLocalPost(post2);
		forumManager.setReadFlag(post2.getMessage().getId(), false);
		Collection<ForumPostHeader> headers =
				forumManager.getPostHeaders(forum.getGroup().getId());
		assertEquals(2, headers.size());
		for (ForumPostHeader h : headers) {
			final String hBody =
					StringUtils.fromUtf8(forumManager.getPostBody(h.getId()));

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
		forumManager.removeForum(forum);
		assertEquals(0, forumManager.getForums().size());
		db.close();
	}
}
