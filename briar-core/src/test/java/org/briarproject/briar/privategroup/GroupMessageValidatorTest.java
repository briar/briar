package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.privategroup.MessageType;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;
import org.jmock.Expectations;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getRandomString;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_JOIN;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_POST;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_NAME;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TYPE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupMessageValidatorTest extends ValidatorTestCase {

	private final PrivateGroupFactory privateGroupFactory =
			context.mock(PrivateGroupFactory.class);
	private final GroupInvitationFactory groupInvitationFactory =
			context.mock(GroupInvitationFactory.class);

	private final String creatorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final String memberName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final byte[] creatorKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final byte[] memberKey = getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final byte[] creatorSignature =
			getRandomBytes(MAX_SIGNATURE_LENGTH);
	private final byte[] signature = getRandomBytes(MAX_SIGNATURE_LENGTH);
	private final Author member =
			new Author(new AuthorId(getRandomId()), memberName, memberKey);
	private final Author creator =
			new Author(new AuthorId(getRandomId()), creatorName, creatorKey);
	private final long inviteTimestamp = 42L;
	private final PrivateGroup privateGroup = new PrivateGroup(group,
			getRandomString(MAX_GROUP_NAME_LENGTH), creator,
			getRandomBytes(GROUP_SALT_LENGTH));
	private final BdfList token = BdfList.of("token");
	private final MessageId parentId = new MessageId(getRandomId());
	private final MessageId previousMsgId = new MessageId(getRandomId());
	private final String postContent =
			getRandomString(MAX_GROUP_POST_BODY_LENGTH);

	private final GroupMessageValidator validator =
			new GroupMessageValidator(privateGroupFactory, clientHelper,
					metadataEncoder, clock, authorFactory,
					groupInvitationFactory);

	// JOIN message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortJoinMessage() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), creatorName, creatorKey, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongJoinMessage() throws Exception {
		expectCreateAuthor(creator);
		BdfList body = BdfList.of(JOIN.getInt(), creatorName, creatorKey, null,
				signature, "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithTooShortMemberName() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), "", memberKey, null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongMemberName() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(),
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1), memberKey, null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNullMemberName() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), null, memberKey, null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNonStringMemberName() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), getRandomBytes(5), memberKey,
				null, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithTooShortMemberKey() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), memberName, new byte[0], null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithTooLongMemberKey() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), memberName,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), null, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithNoullMemberKey() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), memberName, null, null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithNonRawMemberKey() throws Exception {
		BdfList body = BdfList.of(JOIN.getInt(), memberName, "not raw", null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithNonListInvitation() throws Exception {
		expectCreateAuthor(creator);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				"not a list", signature);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsCreatorJoin() throws Exception {
		expectJoinMessage(creator, null, true, true);
		BdfList body = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				null, signature);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertExpectedMessageContext(messageContext, JOIN, creator,
				Collections.<MessageId>emptyList());
		assertTrue(messageContext.getDictionary()
				.getBoolean(KEY_INITIAL_JOIN_MSG));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinWithNullInvitation() throws Exception {
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, null,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithTooShortInvitation() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithTooLongInvitation() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature, "");
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinWithEqualInvitationTime()
			throws Exception {
		BdfList invite = BdfList.of(message.getTimestamp(), creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinWithLaterInvitationTime()
			throws Exception {
		BdfList invite = BdfList.of(message.getTimestamp() + 1,
				creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithNullInvitationTime()
			throws Exception {
		BdfList invite = BdfList.of(null, creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithNonLongInvitationTime()
			throws Exception {
		BdfList invite = BdfList.of("not long", creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithTooShortCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, new byte[0]);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinWithTooLongCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp,
				getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithNullCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, null);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsMemberJoinWithNonRawCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, "not raw");
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinWithInvalidCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, false, true);
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinWithInvalidMemberSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, true, false);
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsMemberJoin() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, true, true);
		BdfList body = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertExpectedMessageContext(messageContext, JOIN, member,
				Collections.<MessageId>emptyList());
		assertFalse(messageContext.getDictionary()
				.getBoolean(KEY_INITIAL_JOIN_MSG));
	}

	private void expectCreateAuthor(final Author member) {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(member.getName(),
					member.getPublicKey());
			will(returnValue(member));
		}});
	}

	private void expectParsePrivateGroup() throws Exception {
		context.checking(new Expectations() {{
			oneOf(privateGroupFactory).parsePrivateGroup(group);
			will(returnValue(privateGroup));
		}});
	}

	private void expectJoinMessage(final Author member, final BdfList invite,
			final boolean creatorSigValid, final boolean memberSigValid)
			throws Exception {
		final BdfList signed = BdfList.of(group.getId(), message.getTimestamp(),
				JOIN.getInt(), member.getName(), member.getPublicKey(), invite);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		context.checking(new Expectations() {{
			if (invite != null) {
				oneOf(groupInvitationFactory).createInviteToken(creator.getId(),
						member.getId(), privateGroup.getId(), inviteTimestamp);
				will(returnValue(token));
				oneOf(clientHelper).verifySignature(SIGNING_LABEL_INVITE,
						creatorSignature, creatorKey, token);
				if (!memberSigValid)
					will(throwException(new GeneralSecurityException()));
			}
			if (memberSigValid) {
				oneOf(clientHelper).verifySignature(SIGNING_LABEL_JOIN,
						signature, member.getPublicKey(), signed);
				if (!creatorSigValid)
					will(throwException(new GeneralSecurityException()));
			}
		}});
	}

	// POST Message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortPost() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongPost() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent, signature, "");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortMemberName() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), "", memberKey, parentId,
				previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongMemberName() throws Exception {
		BdfList body = BdfList.of(POST.getInt(),
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1), memberKey,
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNullMemberName() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), null, memberKey,
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonStringMemberName() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), getRandomBytes(5), memberKey,
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortMemberKey() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, new byte[0],
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongMemberKey() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), parentId,
				previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNullMemberKey() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, null,
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawMemberKey() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, "not raw",
				parentId, previousMsgId, postContent, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortParentId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				getRandomBytes(MessageId.LENGTH - 1), previousMsgId,
				postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongParentId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				getRandomBytes(MessageId.LENGTH + 1), previousMsgId,
				postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawParentId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				"not raw", previousMsgId, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortPreviousMsgId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, getRandomBytes(MessageId.LENGTH - 1), postContent,
				signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongPreviousMsgId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, getRandomBytes(MessageId.LENGTH + 1), postContent,
				signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNullPreviousMsgId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, null, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawPreviousMsgId() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, "not raw", postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortContent() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, "", signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongContent() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId,
				getRandomString(MAX_GROUP_POST_BODY_LENGTH + 1), signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNullContent() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, null, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonStringContent() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, getRandomBytes(5), signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortSignature() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent, new byte[0]);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongSignature() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent,
				getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNullSignature() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent,null);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawSignature() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent, "not raw");
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsPostWithInvalidSignature() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent, signature);
		expectPostMessage(member, parentId, false);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsPost() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey,
				parentId, previousMsgId, postContent, signature);
		expectPostMessage(member, parentId, true);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertExpectedMessageContext(messageContext, POST, member,
				Arrays.asList(parentId, previousMsgId));
		assertArrayEquals(previousMsgId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PREVIOUS_MSG_ID));
		assertArrayEquals(parentId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PARENT_MSG_ID));
	}

	@Test
	public void testAcceptsTopLevelPost() throws Exception {
		BdfList body = BdfList.of(POST.getInt(), memberName, memberKey, null,
				previousMsgId, postContent, signature);
		expectPostMessage(member, null, true);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);
		assertExpectedMessageContext(messageContext, POST, member,
				Collections.singletonList(previousMsgId));
		assertArrayEquals(previousMsgId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PREVIOUS_MSG_ID));
		assertFalse(
				messageContext.getDictionary().containsKey(KEY_PARENT_MSG_ID));
	}

	private void expectPostMessage(final Author member,
			final MessageId parentId, final boolean sigValid) throws Exception {
		final BdfList signed = BdfList.of(group.getId(), message.getTimestamp(),
				POST.getInt(), member.getName(), member.getPublicKey(),
				parentId == null ? null : parentId.getBytes(),
				previousMsgId.getBytes(), postContent);
		expectCreateAuthor(member);
		context.checking(new Expectations() {{
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					member.getPublicKey(), signed);
			if (!sigValid) will(throwException(new GeneralSecurityException()));
		}});
	}

	private void assertExpectedMessageContext(BdfMessageContext c,
			MessageType type, Author member,
			Collection<MessageId> dependencies) throws FormatException {
		BdfDictionary d = c.getDictionary();
		assertEquals(type.getInt(), d.getLong(KEY_TYPE).intValue());
		assertEquals(message.getTimestamp(),
				d.getLong(KEY_TIMESTAMP).longValue());
		assertFalse(d.getBoolean(KEY_READ));
		assertEquals(member.getId().getBytes(), d.getRaw(KEY_MEMBER_ID));
		assertEquals(member.getName(), d.getString(KEY_MEMBER_NAME));
		assertEquals(member.getPublicKey(), d.getRaw(KEY_MEMBER_PUBLIC_KEY));
		assertEquals(dependencies, c.getDependencies());
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMessageWithUnknownType() throws Exception {
		BdfList body = BdfList.of(POST.getInt() + 1, memberName, memberKey,
				parentId, previousMsgId, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, body);
	}
}
