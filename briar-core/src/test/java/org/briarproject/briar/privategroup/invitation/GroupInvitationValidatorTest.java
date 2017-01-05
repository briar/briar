package org.briarproject.briar.privategroup.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.jmock.Expectations;
import org.junit.Test;

import java.security.GeneralSecurityException;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getRandomString;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_MSG_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.briar.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.briar.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.briar.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.briar.privategroup.invitation.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroupInvitationValidatorTest extends ValidatorTestCase {

	private final PrivateGroupFactory privateGroupFactory =
			context.mock(PrivateGroupFactory.class);
	private final MessageEncoder messageEncoder =
			context.mock(MessageEncoder.class);

	private final String groupName = getRandomString(MAX_GROUP_NAME_LENGTH);
	private final String creatorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final byte[] creatorKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final Author creator =
			new Author(new AuthorId(getRandomId()), creatorName, creatorKey);
	private final byte[] salt = getRandomBytes(GROUP_SALT_LENGTH);
	private final PrivateGroup privateGroup =
			new PrivateGroup(group, groupName, creator, salt);
	private final String inviteText =
			getRandomString(MAX_GROUP_INVITATION_MSG_LENGTH);
	private final byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
	private final BdfDictionary meta =
			BdfDictionary.of(new BdfEntry("meta", "data"));
	private final MessageId previousMessageId = new MessageId(getRandomId());

	private final GroupInvitationValidator validator =
			new GroupInvitationValidator(clientHelper, metadataEncoder,
					clock, authorFactory, privateGroupFactory, messageEncoder);

	// INVITE Message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortInviteMessage() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongInviteMessage() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, signature, "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortGroupName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), "", creatorName,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongGroupName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(),
				getRandomString(MAX_GROUP_NAME_LENGTH + 1), creatorName,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullGroupName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), null, creatorName,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonStringGroupName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), getRandomBytes(5),
				creatorName, creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortCreatorName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, "", creatorKey,
				salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongCreatorName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName,
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1), creatorKey, salt,
				inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullCreatorName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, null,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonStringCreatorName()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName,
				getRandomBytes(5), creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortCreatorKey()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				new byte[0], salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongCreatorKey()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), salt, inviteText,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullCreatorKey()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				null, salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonRawCreatorKey()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				"not raw", salt, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortGroupSalt()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, getRandomBytes(GROUP_SALT_LENGTH - 1), inviteText,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongGroupSalt()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, getRandomBytes(GROUP_SALT_LENGTH + 1), inviteText,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullGroupSalt()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, null, inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonRawGroupSalt()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, "not raw", inviteText, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortContent() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, "", signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongContent() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt,
				getRandomString(MAX_GROUP_INVITATION_MSG_LENGTH + 1),
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsInviteMessageWithNullContent() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, null, signature);
		expectInviteMessage(false);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonStringContent()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, getRandomBytes(5), signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortSignature()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, new byte[0]);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongSignature()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText,
				getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullSignature()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonRawSignature()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, "not raw");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithInvalidSignature()
			throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, null, signature);
		expectInviteMessage(true);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsValidInviteMessage() throws Exception {
		BdfList body = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, signature);
		expectInviteMessage(false);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertTrue(messageContext.getDependencies().isEmpty());
		assertEquals(meta, messageContext.getDictionary());
	}

	private void expectInviteMessage(final boolean exception) throws Exception {
		final BdfList signed = BdfList.of(message.getTimestamp(),
				message.getGroupId(), privateGroup.getId());
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(creatorName, creatorKey);
			will(returnValue(creator));
			oneOf(privateGroupFactory).createPrivateGroup(groupName, creator,
					salt);
			will(returnValue(privateGroup));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_INVITE, signature,
					creatorKey, signed);
			if (exception) {
				will(throwException(new GeneralSecurityException()));
			} else {
				oneOf(messageEncoder).encodeMetadata(INVITE,
						message.getGroupId(), message.getTimestamp(), false,
						false, false, false, false);
				will(returnValue(meta));
			}
		}});
	}

	// JOIN Message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortJoinMessage() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongJoinMessage() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				previousMessageId, "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortGroupId() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(),
				getRandomBytes(GroupId.LENGTH - 1), previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongGroupId() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(),
				getRandomBytes(GroupId.LENGTH + 1), previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNullGroupId() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), null, previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNonRawGroupId() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), "not raw",
				previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH - 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH + 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNonRawPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				"not raw");
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsJoinMessageWithNullPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(), null);
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(JOIN, message.getGroupId(),
					message.getTimestamp(), false, false, false, false, false);
			will(returnValue(meta));
		}});
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertEquals(0, messageContext.getDependencies().size());
		assertEquals(meta, messageContext.getDictionary());
	}

	@Test
	public void testAcceptsValidJoinMessage() throws Exception {
		BdfList body = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				previousMessageId);
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(JOIN, message.getGroupId(),
					message.getTimestamp(), false, false, false, false, false);
			will(returnValue(meta));
		}});
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertEquals(1, messageContext.getDependencies().size());
		assertEquals(previousMessageId,
				messageContext.getDependencies().iterator().next());
		assertEquals(meta, messageContext.getDictionary());
	}

	// LEAVE message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortLeaveMessage() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongLeaveMessage() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				previousMessageId, "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooShortGroupId() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(),
				getRandomBytes(GroupId.LENGTH - 1), previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooLongGroupId() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(),
				getRandomBytes(GroupId.LENGTH + 1), previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithNullGroupId() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), null, previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithNonRawGroupId() throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), "not raw",
				previousMessageId);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooShortPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH - 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooLongPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH + 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithNonRawPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				"not raw");
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsLeaveMessageWithNullPreviousMessageId()
			throws Exception {
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(), null);
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(LEAVE, message.getGroupId(),
					message.getTimestamp(), false, false, false, false, false);
			will(returnValue(meta));
		}});
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertEquals(0, messageContext.getDependencies().size());
		assertEquals(meta, messageContext.getDictionary());
	}

	@Test
	public void testAcceptsValidLeaveMessage() throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(LEAVE, message.getGroupId(),
					message.getTimestamp(), false, false, false, false, false);
			will(returnValue(meta));
		}});
		BdfList body = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				previousMessageId);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertEquals(1, messageContext.getDependencies().size());
		assertEquals(previousMessageId,
				messageContext.getDependencies().iterator().next());
		assertEquals(meta, messageContext.getDictionary());
	}

	// ABORT message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortAbortMessage() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAbortMessage() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), privateGroup.getId(), "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithTooShortGroupId() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(),
				getRandomBytes(GroupId.LENGTH - 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithTooLongGroupId() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(),
				getRandomBytes(GroupId.LENGTH + 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithNullGroupId() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithNonRawGroupId() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), "not raw");
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsValidAbortMessage() throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(ABORT, message.getGroupId(),
					message.getTimestamp(), false, false, false, false, false);
			will(returnValue(meta));
		}});
		BdfList body = BdfList.of(ABORT.getValue(), privateGroup.getId());
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertEquals(0, messageContext.getDependencies().size());
		assertEquals(meta, messageContext.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsMessageWithUnknownType() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue() + 1);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsEmptyMessage() throws Exception {
		BdfList body = new BdfList();
		validator.validateMessage(message, group, body);
	}

}
