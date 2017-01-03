package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumFactory;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Collection;

import javax.annotation.Nullable;

import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ForumSharingValidatorTest extends ValidatorTestCase {

	private final MessageEncoder messageEncoder =
			context.mock(MessageEncoder.class);
	private final ForumFactory forumFactory = context.mock(ForumFactory.class);
	private final ForumSharingValidator v =
			new ForumSharingValidator(messageEncoder, clientHelper,
					metadataEncoder, clock, forumFactory);

	private final MessageId previousMsgId = new MessageId(getRandomId());
	private final String forumName =
			TestUtils.getRandomString(MAX_FORUM_NAME_LENGTH);
	private final byte[] salt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH);
	private final Forum forum = new Forum(group, forumName, salt);
	private final BdfList descriptor = BdfList.of(forumName, salt);
	private final String content =
			TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH);
	private final BdfDictionary meta =
			BdfDictionary.of(new BdfEntry("meta", "data"));

	@Test
	public void testAcceptsInvitationWithContent() throws Exception {
		expectCreateForum(forumName);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						content));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullContent() throws Exception {
		expectCreateForum(forumName);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullPreviousMsgId() throws Exception {
		expectCreateForum(forumName);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), null, descriptor, null));
		assertExpectedContext(messageContext, null);
	}

	@Test
	public void testAcceptsAccept() throws Exception {
		expectEncodeMetadata(ACCEPT);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(ACCEPT.getValue(), groupId, previousMsgId));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsDecline() throws Exception {
		expectEncodeMetadata(DECLINE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(DECLINE.getValue(), groupId, previousMsgId));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsLeave() throws Exception {
		expectEncodeMetadata(LEAVE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(LEAVE.getValue(), groupId, previousMsgId));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsAbort() throws Exception {
		expectEncodeMetadata(ABORT);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId, previousMsgId));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullMessageType() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(null, groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongMessageType() throws Exception {
		v.validateMessage(message, group,
				BdfList.of("", groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidMessageType() throws Exception {
		int invalidMessageType = ABORT.getValue() + 1;
		v.validateMessage(message, group,
				BdfList.of(invalidMessageType, groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSessionId() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), null, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSessionId() throws Exception {
		v.validateMessage(message, group, BdfList.of(ABORT.getValue(), 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSessionId() throws Exception {
		byte[] invalidGroupId = TestUtils.getRandomBytes(UniqueId.LENGTH - 1);
		v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), invalidGroupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSessionId() throws Exception {
		byte[] invalidGroupId = TestUtils.getRandomBytes(UniqueId.LENGTH + 1);
		v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), invalidGroupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAbort() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAbort() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId, previousMsgId, 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForInvitation() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForInvitation() throws Exception {
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null,
						123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(null, salt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(123, salt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortForumName() throws Exception {
		BdfList invalidDescriptor = BdfList.of("", salt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test
	public void testAcceptsMinLengthForumName() throws Exception {
		String shortForumName = TestUtils.getRandomString(1);
		BdfList validDescriptor = BdfList.of(shortForumName, salt);
		expectCreateForum(shortForumName);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, validDescriptor,
						null));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongForumName() throws Exception {
		String invalidForumName =
				TestUtils.getRandomString(MAX_FORUM_NAME_LENGTH + 1);
		BdfList invalidDescriptor = BdfList.of(invalidForumName, salt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSalt() throws Exception {
		BdfList invalidDescriptor = BdfList.of(forumName, null);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSalt() throws Exception {
		BdfList invalidDescriptor = BdfList.of(forumName, 123);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSalt() throws Exception {
		byte[] invalidSalt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH - 1);
		BdfList invalidDescriptor = BdfList.of(forumName, invalidSalt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSalt() throws Exception {
		byte[] invalidSalt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH + 1);
		BdfList invalidDescriptor = BdfList.of(forumName, invalidSalt);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		expectCreateForum(forumName);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						123));
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		expectCreateForum(forumName);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, "1"));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		String invalidContent =
				TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH + 1);
		expectCreateForum(forumName);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						invalidContent));
	}

	private void expectCreateForum(final String name) {
		context.checking(new Expectations() {{
			oneOf(forumFactory).createForum(name, salt);
			will(returnValue(forum));
		}});
	}

	private void expectEncodeMetadata(final MessageType type) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder)
					.encodeMetadata(type, groupId, timestamp, false, false,
							false, false);
			will(returnValue(meta));
		}});
	}

	private void assertExpectedContext(BdfMessageContext messageContext,
			@Nullable MessageId previousMsgId) throws FormatException {
		Collection<MessageId> dependencies = messageContext.getDependencies();
		if (previousMsgId == null) {
			assertTrue(dependencies.isEmpty());
		} else {
			assertEquals(1, dependencies.size());
			assertTrue(dependencies.contains(previousMsgId));
		}
		assertEquals(meta, messageContext.getDictionary());
	}

}
