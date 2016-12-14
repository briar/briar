package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.briar.api.privategroup.Visibility.INVISIBLE;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_CONTACT;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_US;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This class tests how PrivateGroupManager and GroupInvitationManager
 * play together.
 */
public class PrivateGroupIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private GroupId groupId0;
	private PrivateGroup privateGroup0;
	private PrivateGroupManager groupManager0, groupManager1, groupManager2;
	private GroupInvitationManager groupInvitationManager0,
			groupInvitationManager1, groupInvitationManager2;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		groupManager0 = c0.getPrivateGroupManager();
		groupManager1 = c1.getPrivateGroupManager();
		groupManager2 = c2.getPrivateGroupManager();
		groupInvitationManager0 = c0.getGroupInvitationManager();
		groupInvitationManager1 = c1.getGroupInvitationManager();
		groupInvitationManager2 = c2.getGroupInvitationManager();

		privateGroup0 =
				privateGroupFactory.createPrivateGroup("Test Group", author0);
		groupId0 = privateGroup0.getId();
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(groupId0, joinTime, author0);
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
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

	@Test
	public void testMembership() throws Exception {
		sendInvitation(contactId1From0, clock.currentTimeMillis(), "Hi!");

		// our group has only one member (ourselves)
		Collection<GroupMember> members = groupManager0.getMembers(groupId0);
		assertEquals(1, members.size());
		assertEquals(author0, members.iterator().next().getAuthor());
		assertEquals(OURSELVES, members.iterator().next().getStatus());

		sync0To1(1, true);
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup0, true);
		sync1To0(1, true);

		// sync group join messages
		sync0To1(2, true); // + one invitation protocol join message
		sync1To0(1, true);

		// now the group has two members
		members = groupManager0.getMembers(groupId0);
		assertEquals(2, members.size());
		for (GroupMember m : members) {
			if (m.getStatus() == OURSELVES) {
				assertEquals(author0.getId(), m.getAuthor().getId());
			} else {
				assertEquals(author1.getId(), m.getAuthor().getId());
			}
		}

		members = groupManager1.getMembers(groupId0);
		assertEquals(2, members.size());
		for (GroupMember m : members) {
			if (m.getStatus() == OURSELVES) {
				assertEquals(author1.getId(), m.getAuthor().getId());
			} else {
				assertEquals(author0.getId(), m.getAuthor().getId());
			}
		}
	}

	@Test
	public void testRevealContacts() throws Exception {
		// invite two contacts
		sendInvitation(contactId1From0, clock.currentTimeMillis(), "Hi 1!");
		sendInvitation(contactId2From0, clock.currentTimeMillis(), "Hi 2!");
		sync0To1(1, true);
		sync0To2(1, true);

		// accept both invitations
		groupInvitationManager1
				.respondToInvitation(contactId0From1, privateGroup0, true);
		groupInvitationManager2
				.respondToInvitation(contactId0From2, privateGroup0, true);
		sync1To0(1, true);
		sync2To0(1, true);

		// sync group join messages
		sync0To1(2, true); // + one invitation protocol join message
		assertEquals(2, groupManager1.getMembers(groupId0).size());
		sync1To0(1, true);
		assertEquals(2, groupManager0.getMembers(groupId0).size());
		sync0To2(3, true); // 2 join messages and 1 invite join message
		assertEquals(3, groupManager2.getMembers(groupId0).size());
		sync2To0(1, true);
		assertEquals(3, groupManager0.getMembers(groupId0).size());
		sync0To1(1, true);
		assertEquals(3, groupManager1.getMembers(groupId0).size());

		// 1 and 2 add each other as contacts
		addContacts1And2();

		// their relationship is still invisible
		assertEquals(INVISIBLE,
				getGroupMember(groupManager1, author2.getId()).getVisibility());
		assertEquals(INVISIBLE,
				getGroupMember(groupManager2, author1.getId()).getVisibility());

		// 1 reveals the contact relationship to 2
		assertTrue(contactId2From1 != null);
		groupInvitationManager1.revealRelationship(contactId2From1, groupId0);
		sync1To2(1, true);
		sync2To1(1, true);

		// their relationship is now revealed
		assertEquals(REVEALED_BY_US,
				getGroupMember(groupManager1, author2.getId()).getVisibility());
		assertEquals(REVEALED_BY_CONTACT,
				getGroupMember(groupManager2, author1.getId()).getVisibility());

		// 2 sends a message to the group
		long time = clock.currentTimeMillis();
		String body = "This is a test message!";
		MessageId previousMsgId = groupManager2.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, time, null, author2, body,
						previousMsgId);
		groupManager2.addLocalMessage(msg);

		// 1 has only the three join messages in the group
		Collection<GroupMessageHeader> headers =
				groupManager1.getHeaders(groupId0);
		assertEquals(3, headers.size());

		// message should sync to 1 without creator (0) being involved
		sync2To1(1, true);
		headers = groupManager1.getHeaders(groupId0);
		assertEquals(4, headers.size());
		boolean foundPost = false;
		for (GroupMessageHeader h : headers) {
			if (h instanceof JoinMessageHeader) continue;
			foundPost = true;
			assertEquals(time, h.getTimestamp());
			assertEquals(groupId0, h.getGroupId());
			assertEquals(author2.getId(), h.getAuthor().getId());
		}
		assertTrue(foundPost);

		// message should sync from 1 to 0 without 2 being involved
		sync1To0(1, true);
		headers = groupManager0.getHeaders(groupId0);
		assertEquals(4, headers.size());
	}

	private void sendInvitation(ContactId c, long timestamp,
			@Nullable String msg) throws DbException {
		Contact contact = contactManager0.getContact(c);
		byte[] signature = groupInvitationFactory
				.signInvitation(contact, groupId0, timestamp,
						author0.getPrivateKey());
		groupInvitationManager0
				.sendInvitation(groupId0, c, msg, timestamp, signature);
	}

	private GroupMember getGroupMember(PrivateGroupManager groupManager,
			AuthorId a) throws DbException {
		Collection<GroupMember> members = groupManager.getMembers(groupId0);
		for (GroupMember m : members) {
			if (m.getAuthor().getId().equals(a)) return m;
		}
		throw new AssertionError();
	}

}
