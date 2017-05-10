package org.briarproject.briar.sharing;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchGroupException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.blog.event.BlogInvitationRequestReceivedEvent;
import org.briarproject.briar.api.blog.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationMessage;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.briarproject.briar.api.blog.BlogSharingManager.CLIENT_ID;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BlogSharingIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private BlogManager blogManager0, blogManager1;
	private Blog blog0, blog1, blog2;
	private SharerListener listener0;
	private InviteeListener listener1;

	// objects accessed from background threads need to be volatile
	private volatile BlogSharingManager blogSharingManager0;
	private volatile BlogSharingManager blogSharingManager1;
	private volatile BlogSharingManager blogSharingManager2;
	private volatile Waiter eventWaiter;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		blogManager0 = c0.getBlogManager();
		blogManager1 = c1.getBlogManager();
		blogSharingManager0 = c0.getBlogSharingManager();
		blogSharingManager1 = c1.getBlogSharingManager();
		blogSharingManager2 = c2.getBlogSharingManager();

		blog0 = blogManager0.getPersonalBlog(author0);
		blog1 = blogManager0.getPersonalBlog(author1);
		blog2 = blogManager0.getPersonalBlog(author2);

		// initialize waiters fresh for each test
		eventWaiter = new Waiter();
	}

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(c0);

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(c1);

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(c2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPersonalBlogCannotBeSharedWithOwner() throws Exception {
		listenToEvents(true);

		assertFalse(blogSharingManager0.canBeShared(blog1.getId(),
				contact1From0));
		assertFalse(blogSharingManager0.canBeShared(blog2.getId(),
				contact2From0));
		assertFalse(blogSharingManager1.canBeShared(blog0.getId(),
				contact0From1));
		assertFalse(blogSharingManager2.canBeShared(blog0.getId(),
				contact0From2));

		// create invitation
		blogSharingManager0
				.sendInvitation(blog1.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// invitee has own blog and that of the sharer
		assertEquals(2, blogManager1.getBlogs().size());

		// get sharing group and assert group message count
		GroupId g = contactGroupFactory.createContactGroup(CLIENT_ID,
				contact1From0)
				.getId();
		assertGroupCount(messageTracker0, g, 1, 0);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
		assertGroupCount(messageTracker1, g, 2, 1);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);
		assertGroupCount(messageTracker0, g, 2, 1);

		// blog was added successfully
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());

		// invitee has one invitation message from sharer
		List<InvitationMessage> list =
				new ArrayList<InvitationMessage>(blogSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		// check other things are alright with the message
		for (InvitationMessage m : list) {
			if (m instanceof BlogInvitationRequest) {
				BlogInvitationRequest invitation =
						(BlogInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(blog2.getAuthor().getName(),
						invitation.getBlogAuthorName());
				assertEquals(contactId1From0, invitation.getContactId());
				assertEquals("Hi!", invitation.getMessage());
			} else {
				BlogInvitationResponse response =
						(BlogInvitationResponse) m;
				assertEquals(contactId0From1, response.getContactId());
				assertTrue(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				blogSharingManager0.getInvitationMessages(contactId1From0)
						.size());
		// blog can not be shared again
		assertFalse(blogSharingManager0.canBeShared(blog2.getId(),
				contact1From0));
		assertFalse(blogSharingManager1.canBeShared(blog2.getId(),
				contact0From1));

		// group message count is still correct
		assertGroupCount(messageTracker0, g, 2, 1);
		assertGroupCount(messageTracker1, g, 2, 1);
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		// initialize and let invitee deny all requests
		listenToEvents(false);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, null,
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was not added
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(2, blogManager1.getBlogs().size());
		// blog is no longer available to invitee who declined
		assertEquals(0, blogSharingManager1.getInvitations().size());

		// invitee has one invitation message from sharer and one response
		List<InvitationMessage> list =
				new ArrayList<InvitationMessage>(blogSharingManager1
						.getInvitationMessages(contactId0From1));
		assertEquals(2, list.size());
		// check things are alright with the  message
		for (InvitationMessage m : list) {
			if (m instanceof BlogInvitationRequest) {
				BlogInvitationRequest invitation =
						(BlogInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(blog2.getAuthor().getName(),
						invitation.getBlogAuthorName());
				assertEquals(contactId1From0, invitation.getContactId());
				assertEquals(null, invitation.getMessage());
			} else {
				BlogInvitationResponse response =
						(BlogInvitationResponse) m;
				assertEquals(contactId0From1, response.getContactId());
				assertFalse(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				blogSharingManager0.getInvitationMessages(contactId1From0)
						.size());
		// blog can be shared again
		assertTrue(
				blogSharingManager0.canBeShared(blog2.getId(), contact1From0));
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added successfully
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());
		assertTrue(blogManager1.getBlogs().contains(blog2));

		// sharer shares blog with invitee
		assertTrue(blogSharingManager0.getSharedWith(blog2.getId())
				.contains(contact1From0));
		// invitee gets blog shared by sharer
		assertTrue(blogSharingManager1.getSharedWith(blog2.getId())
				.contains(contact0From1));

		// invitee un-subscribes from blog
		blogManager1.removeBlog(blog2);

		// send leave message to sharer
		sync1To0(1, true);

		// blog is gone
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(2, blogManager1.getBlogs().size());

		// sharer no longer shares blog with invitee
		assertFalse(blogSharingManager0.getSharedWith(blog2.getId())
				.contains(contact1From0));
		// invitee no longer has blog shared by sharer
		assertEquals(0,
				blogSharingManager1.getSharedWith(blog2.getId()).size());
		// blog can be shared again by sharer
		assertTrue(
				blogSharingManager0.canBeShared(blog2.getId(), contact1From0));
	}

	@Test
	public void testInvitationForExistingBlog() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// 1 and 2 are adding each other
		addContacts1And2();
		assertEquals(3, blogManager1.getBlogs().size());

		// sharer sends invitation for 2's blog to 1
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// make sure blog2 is shared by 0 and 2
		Collection<Contact> contacts =
				blogSharingManager1.getSharedWith(blog2.getId());
		assertEquals(2, contacts.size());
		assertTrue(contacts.contains(contact0From1));

		// make sure 1 knows that they have blog2 already
		Collection<InvitationMessage> messages =
				blogSharingManager1.getInvitationMessages(contactId0From1);
		assertEquals(2, messages.size());
		assertEquals(blog2, blogManager1.getBlog(blog2.getId()));

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was not added, because it was there already
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());
	}

	@Test
	public void testRemovingSharedBlog() throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added successfully and is shared both ways
		assertEquals(3, blogManager1.getBlogs().size());
		Collection<Contact> sharedWith =
				blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(2, sharedWith.size());
		assertTrue(sharedWith.contains(contact1From0));
		assertTrue(sharedWith.contains(contact2From0));
		Collection<Contact> sharedBy =
				blogSharingManager1.getSharedWith(blog2.getId());
		assertEquals(1, sharedBy.size());
		assertEquals(contact0From1, sharedBy.iterator().next());

		// shared blog can be removed
		assertTrue(blogManager1.canBeRemoved(blog2));

		// invitee removes blog again
		blogManager1.removeBlog(blog2);

		// sync LEAVE message
		sync1To0(1, true);

		// sharer does not share this blog anymore with invitee
		sharedWith =
				blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(1, sharedWith.size());
		assertTrue(sharedWith.contains(contact2From0));
	}

	@Test
	public void testRemovePreSharedBlog() throws Exception {
		// let invitee accept all requests
		listenToEvents(true);

		// 0 and 1 are sharing blog 1 with each other
		assertTrue(blogSharingManager0.getSharedWith(blog1.getId())
				.contains(contact1From0));
		assertTrue(blogSharingManager1.getSharedWith(blog1.getId())
				.contains(contact0From1));

		// 0 removes blog 1
		assertTrue(blogManager0.getBlogs().contains(blog1));
		blogManager0.removeBlog(blog1);
		assertFalse(blogManager0.getBlogs().contains(blog1));

		// sync leave message to 0
		sync0To1(1, true);

		// 0 and 1 are no longer sharing blog 1 with each other
		assertFalse(blogSharingManager0.getSharedWith(blog1.getId())
				.contains(contact1From0));
		assertFalse(blogSharingManager1.getSharedWith(blog1.getId())
				.contains(contact0From1));

		// 1 can again share blog 1 with 0
		assertTrue(
				blogSharingManager1.canBeShared(blog1.getId(), contact0From1));
	}

	@Test
	public void testSharerIsInformedWhenBlogIsRemovedDueToContactDeletion()
			throws Exception {
		// initialize and let invitee accept all requests
		listenToEvents(true);

		// sharer sends invitation for 2's blog to 1
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1From0, "Hi!",
						clock.currentTimeMillis());

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// 1 and 2 are adding each other
		addContacts1And2();
		assertEquals(3, blogManager1.getBlogs().size());

		// make sure blog2 is shared between 0 and 1
		Collection<Contact> contacts =
				blogSharingManager1.getSharedWith(blog2.getId());
		assertEquals(2, contacts.size());
		assertTrue(contacts.contains(contact0From1));
		contacts = blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(2, contacts.size());
		assertTrue(contacts.contains(contact1From0));

		// 1 removes contact 2
		assertNotNull(contactId2From1);
		contactManager1.removeContact(contactId2From1);

		// sync leave message to 0
		sync1To0(1, true);

		// make sure blog2 is no longer shared between 0 and 1
		contacts = blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(1, contacts.size());
		assertFalse(contacts.contains(contact1From0));

		// 1 doesn't even have blog2 anymore
		try {
			blogManager1.getBlog(blog2.getId());
			fail();
		} catch (NoSuchGroupException e) {
			// expected
		}

		// 0 can share blog2 again with 1
		assertTrue(
				blogSharingManager0.canBeShared(blog2.getId(), contact1From0));
	}

	@NotNullByDefault
	private class SharerListener implements EventListener {

		private volatile boolean responseReceived = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof BlogInvitationResponseReceivedEvent) {
				BlogInvitationResponseReceivedEvent event =
						(BlogInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId1From0, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
			// this is only needed for tests where a blog is re-shared
			else if (e instanceof BlogInvitationRequestReceivedEvent) {
				BlogInvitationRequestReceivedEvent event =
						(BlogInvitationRequestReceivedEvent) e;
				eventWaiter.assertEquals(contactId1From0, event.getContactId());
				Blog b = event.getShareable();
				try {
					Contact c = contactManager0.getContact(contactId1From0);
					blogSharingManager0.respondToInvitation(b, c, true);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
		}
	}

	@NotNullByDefault
	private class InviteeListener implements EventListener {

		private volatile boolean requestReceived = false;

		private final boolean accept, answer;

		private InviteeListener(boolean accept, boolean answer) {
			this.accept = accept;
			this.answer = answer;
		}

		private InviteeListener(boolean accept) {
			this(accept, true);
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof BlogInvitationRequestReceivedEvent) {
				BlogInvitationRequestReceivedEvent event =
						(BlogInvitationRequestReceivedEvent) e;
				requestReceived = true;
				if (!answer) return;
				Blog b = event.getShareable();
				try {
					eventWaiter.assertEquals(1,
							blogSharingManager1.getInvitations().size());
					Contact c =
							contactManager1.getContact(event.getContactId());
					blogSharingManager1.respondToInvitation(b, c, accept);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
			// this is only needed for tests where a blog is re-shared
			else if (e instanceof BlogInvitationResponseReceivedEvent) {
				BlogInvitationResponseReceivedEvent event =
						(BlogInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId0From1, event.getContactId());
				eventWaiter.resume();
			}
		}
	}

	private void listenToEvents(boolean accept) throws DbException {
		listener0 = new SharerListener();
		c0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(accept);
		c1.getEventBus().addListener(listener1);
		SharerListener listener2 = new SharerListener();
		c2.getEventBus().addListener(listener2);
	}

}
