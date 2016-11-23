package org.briarproject.privategroup.invitation;

import org.briarproject.ValidatorTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jmock.Expectations;
import org.junit.Test;

import java.security.GeneralSecurityException;

import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.briarproject.TestUtils.getRandomString;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_INVITATION_MSG_LENGTH;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.privategroup.invitation.MessageType.ABORT;
import static org.briarproject.privategroup.invitation.MessageType.INVITE;
import static org.briarproject.privategroup.invitation.MessageType.JOIN;
import static org.briarproject.privategroup.invitation.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroupInvitationValidatorTest extends ValidatorTestCase {

	private final PrivateGroupFactory privateGroupFactory =
			context.mock(PrivateGroupFactory.class);
	private final MessageEncoder messageEncoder =
			context.mock(MessageEncoder.class);

	private final String groupName = "Group Name";
	private final String creatorName = "Creator Name";
	private final byte[] creatorKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final Author creator =
			new Author(new AuthorId(getRandomId()), creatorName, creatorKey);
	private final byte[] salt = getRandomBytes(GROUP_SALT_LENGTH);
	private final PrivateGroup privateGroup =
			new PrivateGroup(group, groupName, creator, salt);
	private final String inviteText = "Invitation Text";
	private final byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
	private final BdfDictionary meta =
			BdfDictionary.of(new BdfEntry("meta", "data"));
	private final MessageId previousMessageId = new MessageId(getRandomId());

	private GroupInvitationValidator validator =
			new GroupInvitationValidator(clientHelper, metadataEncoder,
					clock, authorFactory, privateGroupFactory, messageEncoder);

	@Test(expected = FormatException.class)
	public void testRejectsTooShortInviteMessage() throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongInviteMessage() throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, signature, "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongGroupName()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(),
				getRandomString(MAX_GROUP_NAME_LENGTH + 1), creatorName,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithEmptyGroupName()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), "", creatorName,
				creatorKey, salt, inviteText, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongCreatorName()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName,
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1), creatorKey, salt,
				inviteText, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithEmptyCreatorName()
			throws Exception {
		BdfList list =
				BdfList.of(INVITE.getValue(), groupName, "", creatorKey, salt,
						inviteText, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongCreatorKey()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), salt, inviteText,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithEmptyCreatorKey()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				new byte[0], salt, inviteText, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongGroupSalt()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, getRandomBytes(GROUP_SALT_LENGTH + 1), inviteText,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooShortGroupSalt()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, getRandomBytes(GROUP_SALT_LENGTH - 1), inviteText,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongMessage()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt,
				getRandomString(MAX_GROUP_INVITATION_MSG_LENGTH + 1),
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithTooLongSignature()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText,
				getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithEmptySignature()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, new byte[0]);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNullSignature()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, null);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithNonRawSignature()
			throws Exception {
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, "non raw signature");
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptsInviteMessageWithNullMessage()
			throws Exception {
		expectInviteMessage(false);
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, null, signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInviteMessageWithInvalidSignature()
			throws Exception {
		expectInviteMessage(true);
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, null, signature);
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptsProperInviteMessage()
			throws Exception {
		expectInviteMessage(false);
		BdfList list = BdfList.of(INVITE.getValue(), groupName, creatorName,
				creatorKey, salt, inviteText, signature);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertTrue(messageContext.getDependencies().isEmpty());
		assertEquals(meta ,messageContext.getDictionary());
	}

	private void expectInviteMessage(final boolean exception) throws Exception {
		final BdfList toSign =
				BdfList.of(message.getTimestamp(), message.getGroupId(),
						privateGroup.getId());
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(creatorName, creatorKey);
			will(returnValue(creator));
			oneOf(privateGroupFactory)
					.createPrivateGroup(groupName, creator, salt);
			will(returnValue(privateGroup));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_INVITE, signature,
					creatorKey, toSign);
			if (exception) will(throwException(new GeneralSecurityException()));
			else {
				oneOf(messageEncoder)
						.encodeMetadata(INVITE, message.getGroupId(),
								message.getTimestamp(), false, false, false,
								false);
				will(returnValue(meta));
			}
		}});
	}

	// JOIN Message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortJoinMessage() throws Exception {
		BdfList list = BdfList.of(JOIN.getValue(), privateGroup.getId());
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongJoinMessage() throws Exception {
		BdfList list = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				previousMessageId, "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongGroupId() throws Exception {
		BdfList list =
				BdfList.of(JOIN.getValue(), getRandomBytes(GroupId.LENGTH + 1),
						previousMessageId);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortGroupId() throws Exception {
		BdfList list =
				BdfList.of(JOIN.getValue(), getRandomBytes(GroupId.LENGTH - 1),
						previousMessageId);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongPreviousMessageId()
			throws Exception {
		BdfList list = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH + 1));
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortPreviousMessageId()
			throws Exception {
		BdfList list = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH - 1));
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptsProperJoinMessage()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder)
					.encodeMetadata(JOIN, message.getGroupId(),
							message.getTimestamp(), false, false, false,
							false);
			will(returnValue(meta));
		}});
		BdfList list = BdfList.of(JOIN.getValue(), privateGroup.getId(),
				previousMessageId);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertEquals(1, messageContext.getDependencies().size());
		assertEquals(previousMessageId,
				messageContext.getDependencies().iterator().next());
		assertEquals(meta ,messageContext.getDictionary());
	}

	// LEAVE message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortLeaveMessage() throws Exception {
		BdfList list = BdfList.of(LEAVE.getValue(), privateGroup.getId());
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongLeaveMessage() throws Exception {
		BdfList list = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				previousMessageId, "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooLongGroupId() throws Exception {
		BdfList list =
				BdfList.of(LEAVE.getValue(), getRandomBytes(GroupId.LENGTH + 1),
						previousMessageId);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooShortGroupId() throws Exception {
		BdfList list =
				BdfList.of(LEAVE.getValue(), getRandomBytes(GroupId.LENGTH - 1),
						previousMessageId);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooLongPreviousMessageId()
			throws Exception {
		BdfList list = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH + 1));
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsLeaveMessageWithTooShortPreviousMessageId()
			throws Exception {
		BdfList list = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				getRandomBytes(UniqueId.LENGTH - 1));
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptsProperLeaveMessage()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(LEAVE, message.getGroupId(),
					message.getTimestamp(), false, false, false, false);
			will(returnValue(meta));
		}});
		BdfList list = BdfList.of(LEAVE.getValue(), privateGroup.getId(),
				previousMessageId);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertEquals(1, messageContext.getDependencies().size());
		assertEquals(previousMessageId,
				messageContext.getDependencies().iterator().next());
		assertEquals(meta ,messageContext.getDictionary());
	}

	// ABORT message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortAbortMessage() throws Exception {
		BdfList list = BdfList.of(ABORT.getValue());
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAbortMessage() throws Exception {
		BdfList list = BdfList.of(ABORT.getValue(), privateGroup.getId(), "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithTooLongGroupId() throws Exception {
		BdfList list = BdfList.of(ABORT.getValue(),
				getRandomBytes(GroupId.LENGTH + 1));
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAbortMessageWithTooShortGroupId() throws Exception {
		BdfList list = BdfList.of(ABORT.getValue(),
				getRandomBytes(GroupId.LENGTH - 1));
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptsProperAbortMessage()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(ABORT, message.getGroupId(),
					message.getTimestamp(), false, false, false, false);
			will(returnValue(meta));
		}});
		BdfList list = BdfList.of(ABORT.getValue(), privateGroup.getId());
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertEquals(0, messageContext.getDependencies().size());
		assertEquals(meta ,messageContext.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsMessageWithUnknownType() throws Exception {
		BdfList list = BdfList.of(ABORT.getValue() + 1);
		validator.validateMessage(message, group, list);
	}

}
