package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.privategroup.Visibility.INVISIBLE;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_CONTACT;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_US;
import static org.briarproject.briar.api.privategroup.Visibility.VISIBLE;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateGroupManagerIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private PrivateGroup privateGroup0;
	private GroupId groupId0;
	private PrivateGroupManager groupManager0, groupManager1, groupManager2;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		groupManager0 = c0.getPrivateGroupManager();
		groupManager1 = c1.getPrivateGroupManager();
		groupManager2 = c2.getPrivateGroupManager();

		privateGroup0 =
				privateGroupFactory.createPrivateGroup("Testgroup", author0);
		groupId0 = privateGroup0.getId();
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
	public void testSendingMessage() throws Exception {
		addGroup();

		// create and add test message
		long time = clock.currentTimeMillis();
		String body = "This is a test message!";
		MessageId previousMsgId =
				groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, time, null, author0, body,
						previousMsgId);
		groupManager0.addLocalMessage(msg);
		assertEquals(msg.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// sync test message
		sync0To1(1, true);

		// assert that message arrived as expected
		Collection<GroupMessageHeader> headers =
				groupManager1.getHeaders(groupId0);
		assertEquals(3, headers.size());
		GroupMessageHeader header = null;
		for (GroupMessageHeader h : headers) {
			if (!(h instanceof JoinMessageHeader)) {
				header = h;
			}
		}
		assertTrue(header != null);
		assertFalse(header.isRead());
		assertEquals(author0, header.getAuthor());
		assertEquals(time, header.getTimestamp());
		assertEquals(VERIFIED, header.getAuthorStatus());
		assertEquals(body, groupManager1.getMessageBody(header.getId()));
		GroupCount count = groupManager1.getGroupCount(groupId0);
		assertEquals(2, count.getUnreadCount());
		assertEquals(time, count.getLatestMsgTime());
		assertEquals(3, count.getMsgCount());
	}

	@Test
	public void testMessageWithWrongPreviousMsgId() throws Exception {
		addGroup();

		// create and add test message with no previousMsgId
		@SuppressWarnings("ConstantConditions")
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", null);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with random previousMsgId
		MessageId previousMsgId = new MessageId(getRandomId());
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with wrong previousMsgId
		previousMsgId = groupManager1.getPreviousMsgId(groupId0);
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(), null,
						author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testMessageWithWrongParentMsgId() throws Exception {
		addGroup();

		// create and add test message with random parentMsgId
		MessageId parentMsgId = new MessageId(getRandomId());
		MessageId previousMsgId = groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(),
						parentMsgId, author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with wrong parentMsgId
		parentMsgId = previousMsgId;
		msg = groupMessageFactory
				.createGroupMessage(groupId0, clock.currentTimeMillis(),
						parentMsgId, author0, "test", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testMessageWithWrongTimestamp() throws Exception {
		addGroup();

		// create and add test message with wrong timestamp
		MessageId previousMsgId = groupManager0.getPreviousMsgId(groupId0);
		GroupMessage msg = groupMessageFactory
				.createGroupMessage(groupId0, 42, null, author0, "test",
						previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(2, groupManager1.getHeaders(groupId0).size());

		// create and add test message with good timestamp
		long time = clock.currentTimeMillis();
		msg = groupMessageFactory
				.createGroupMessage(groupId0, time, null, author0, "test",
						previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, true);
		assertEquals(3, groupManager1.getHeaders(groupId0).size());

		// create and add test message with same timestamp as previous message
		previousMsgId = msg.getMessage().getId();
		msg = groupMessageFactory
				.createGroupMessage(groupId0, time, previousMsgId, author0,
						"test2", previousMsgId);
		groupManager0.addLocalMessage(msg);

		// sync test message
		sync0To1(1, false);

		// assert that message did not arrive
		assertEquals(3, groupManager1.getHeaders(groupId0).size());
	}

	@Test
	public void testWrongJoinMessages1() throws Exception {
		// author0 joins privateGroup0 with wrong join message
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0,
						joinTime, getRandomBytes(12));
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1From0, privateGroup0.getId(),
				SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0 with wrong timestamp
		joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime;
		Contact c1 = contactManager0.getContact(contactId1From0);
		byte[] creatorSignature = groupInvitationFactory
				.signInvitation(c1, privateGroup0.getId(), inviteTime,
						author0.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId0From1, privateGroup0.getId(),
				SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// sync join messages
		sync0To1(1, false);

		// assert that 0 never joined the group from 1's perspective
		assertEquals(1, groupManager1.getHeaders(groupId0).size());

		sync1To0(1, false);

		// assert that 1 never joined the group from 0's perspective
		assertEquals(1, groupManager0.getHeaders(groupId0).size());
	}

	@Test
	public void testWrongJoinMessages2() throws Exception {
		// author0 joins privateGroup0 with wrong member's join message
		long joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		BdfList toSign = groupInvitationFactory
				.createInviteToken(author0.getId(), author0.getId(),
						privateGroup0.getId(), inviteTime);
		byte[] creatorSignature = clientHelper
				.sign(SIGNING_LABEL_INVITE, toSign, author0.getPrivateKey());
		// join message should not include invite time and creator's signature
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0,
						inviteTime, creatorSignature);
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1From0, privateGroup0.getId(),
				SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0 with wrong signature in join message
		joinTime = clock.currentTimeMillis();
		inviteTime = joinTime - 1;
		// signature uses joiner's key, not creator's key
		Contact c1 = contactManager0.getContact(contactId1From0);
		creatorSignature = groupInvitationFactory
				.signInvitation(c1, privateGroup0.getId(), inviteTime,
						author1.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId0From1, privateGroup0.getId(),
				SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// sync join messages
		sync0To1(1, false);

		// assert that 0 never joined the group from 1's perspective
		assertEquals(1, groupManager1.getHeaders(groupId0).size());

		sync1To0(1, false);

		// assert that 1 never joined the group from 0's perspective
		assertEquals(1, groupManager0.getHeaders(groupId0).size());
	}

	@Test
	public void testGetMembers() throws Exception {
		addGroup();

		Collection<GroupMember> members0 = groupManager0.getMembers(groupId0);
		assertEquals(2, members0.size());
		for (GroupMember m : members0) {
			if (m.getAuthor().equals(author0)) {
				assertEquals(VISIBLE, m.getVisibility());
			} else {
				assertEquals(author1, m.getAuthor());
				assertEquals(VISIBLE, m.getVisibility());
			}
		}

		Collection<GroupMember> members1 = groupManager1.getMembers(groupId0);
		assertEquals(2, members1.size());
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(VISIBLE, m.getVisibility());
			} else {
				assertEquals(author0, m.getAuthor());
				assertEquals(VISIBLE, m.getVisibility());
			}
		}
	}

	@Test
	public void testJoinMessages() throws Exception {
		addGroup();

		Collection<GroupMessageHeader> headers0 =
				groupManager0.getHeaders(groupId0);
		for (GroupMessageHeader h : headers0) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				// all relationships of the creator are visible
				assertEquals(VISIBLE, j.getVisibility());
			}
		}

		Collection<GroupMessageHeader> headers1 =
				groupManager1.getHeaders(groupId0);
		for (GroupMessageHeader h : headers1) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author1))
					// we are visible to ourselves
					assertEquals(VISIBLE, j.getVisibility());
				else
					// our relationship to the creator is visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
	}

	@Test
	public void testRevealingRelationships() throws Exception {
		addGroup();

		// share the group with 2
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId2From0, privateGroup0.getId(),
				SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author2 joins privateGroup0
		long joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		Contact c2 = contactManager0.getContact(contactId2From0);
		byte[] creatorSignature = groupInvitationFactory
				.signInvitation(c2, privateGroup0.getId(), inviteTime,
						author0.getPrivateKey());
		GroupMessage joinMsg2 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author2,
						inviteTime, creatorSignature);
		Transaction txn2 = db2.startTransaction(false);
		groupManager2.addPrivateGroup(txn2, privateGroup0, joinMsg2, false);

		// share the group with 0
		db2.setGroupVisibility(txn2, contactId0From1, privateGroup0.getId(),
				SHARED);
		db2.commitTransaction(txn2);
		db2.endTransaction(txn2);

		// sync join messages
		sync2To0(1, true);
		sync0To2(2, true);
		sync0To1(1, true);

		// check that everybody sees everybody else as joined
		Collection<GroupMember> members0 = groupManager0.getMembers(groupId0);
		assertEquals(3, members0.size());
		Collection<GroupMember> members1 = groupManager1.getMembers(groupId0);
		assertEquals(3, members1.size());
		Collection<GroupMember> members2 = groupManager2.getMembers(groupId0);
		assertEquals(3, members2.size());

		// 1 and 2 add each other
		addContacts1And2();

		// assert that contact relationship is not revealed initially
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author2)) {
				assertEquals(INVISIBLE, m.getVisibility());
			}
		}
		for (GroupMember m : members2) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(INVISIBLE, m.getVisibility());
			}
		}

		// reveal contact relationship
		Transaction txn1 = db1.startTransaction(false);
		groupManager1
				.relationshipRevealed(txn1, groupId0, author2.getId(), false);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);
		txn2 = db2.startTransaction(false);
		groupManager2
				.relationshipRevealed(txn2, groupId0, author1.getId(), true);
		db2.commitTransaction(txn2);
		db2.endTransaction(txn2);

		// assert that contact relationship is now revealed properly
		members1 = groupManager1.getMembers(groupId0);
		for (GroupMember m : members1) {
			if (m.getAuthor().equals(author2)) {
				assertEquals(REVEALED_BY_US, m.getVisibility());
			}
		}
		members2 = groupManager2.getMembers(groupId0);
		for (GroupMember m : members2) {
			if (m.getAuthor().equals(author1)) {
				assertEquals(REVEALED_BY_CONTACT, m.getVisibility());
			}
		}

		// assert that join messages reflect revealed relationship
		Collection<GroupMessageHeader> headers1 =
				groupManager1.getHeaders(groupId0);
		for (GroupMessageHeader h : headers1) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author2))
					// 1 revealed the relationship to 2
					assertEquals(REVEALED_BY_US, j.getVisibility());
				else
					// 1's other relationship (to 1 and creator) are visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
		Collection<GroupMessageHeader> headers2 =
				groupManager2.getHeaders(groupId0);
		for (GroupMessageHeader h : headers2) {
			if (h instanceof JoinMessageHeader) {
				JoinMessageHeader j = (JoinMessageHeader) h;
				if (h.getAuthor().equals(author1))
					// 2's relationship was revealed by 1
					assertEquals(REVEALED_BY_CONTACT, j.getVisibility());
				else
					// 2's other relationship (to 2 and creator) are visible
					assertEquals(VISIBLE, j.getVisibility());
			}
		}
	}

	@Test
	public void testDissolveGroup() throws Exception {
		addGroup();

		// group is not dissolved initially
		assertFalse(groupManager1.isDissolved(groupId0));

		// creator dissolves group
		Transaction txn1 = db1.startTransaction(false);
		groupManager1.markGroupDissolved(txn1, groupId0);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);

		// group is dissolved now
		assertTrue(groupManager1.isDissolved(groupId0));
	}

	private void addGroup() throws Exception {
		// author0 joins privateGroup0
		long joinTime = clock.currentTimeMillis();
		GroupMessage joinMsg0 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author0);
		groupManager0.addPrivateGroup(privateGroup0, joinMsg0, true);
		assertEquals(joinMsg0.getMessage().getId(),
				groupManager0.getPreviousMsgId(groupId0));

		// share the group with 1
		Transaction txn0 = db0.startTransaction(false);
		db0.setGroupVisibility(txn0, contactId1From0, privateGroup0.getId(),
				SHARED);
		db0.commitTransaction(txn0);
		db0.endTransaction(txn0);

		// author1 joins privateGroup0
		joinTime = clock.currentTimeMillis();
		long inviteTime = joinTime - 1;
		Contact c1 = contactManager0.getContact(contactId1From0);
		byte[] creatorSignature = groupInvitationFactory
				.signInvitation(c1, privateGroup0.getId(), inviteTime,
						author0.getPrivateKey());
		GroupMessage joinMsg1 = groupMessageFactory
				.createJoinMessage(privateGroup0.getId(), joinTime, author1,
						inviteTime, creatorSignature);
		groupManager1.addPrivateGroup(privateGroup0, joinMsg1, false);

		// share the group with 0
		Transaction txn1 = db1.startTransaction(false);
		db1.setGroupVisibility(txn1, contactId0From1, privateGroup0.getId(),
				SHARED);
		db1.commitTransaction(txn1);
		db1.endTransaction(txn1);
		assertEquals(joinMsg1.getMessage().getId(),
				groupManager1.getPreviousMsgId(groupId0));

		// sync join messages
		sync0To1(1, true);
		sync1To0(1, true);
	}

}
