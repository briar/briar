package org.briarproject.privategroup;

import org.briarproject.ValidatorTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.invitation.GroupInvitationFactory;
import org.briarproject.api.sync.InvalidMessageException;
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
import static org.briarproject.api.privategroup.GroupMessageFactory.SIGNING_LABEL_JOIN;
import static org.briarproject.api.privategroup.GroupMessageFactory.SIGNING_LABEL_POST;
import static org.briarproject.api.privategroup.MessageType.JOIN;
import static org.briarproject.api.privategroup.MessageType.POST;
import static org.briarproject.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;
import static org.briarproject.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.privategroup.GroupConstants.KEY_MEMBER_ID;
import static org.briarproject.privategroup.GroupConstants.KEY_MEMBER_NAME;
import static org.briarproject.privategroup.GroupConstants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.privategroup.GroupConstants.KEY_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupMessageValidatorTest extends ValidatorTestCase {

	private final PrivateGroupFactory privateGroupFactory =
			context.mock(PrivateGroupFactory.class);
	private final GroupInvitationFactory groupInvitationFactory =
			context.mock(GroupInvitationFactory.class);


	private final String creatorName = "Member Name";
	private final String memberName = "Member Name";
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
	private final PrivateGroup privateGroup =
			new PrivateGroup(group, "Private Group Name", creator,
					getRandomBytes(GROUP_SALT_LENGTH));
	private final BdfList token = BdfList.of("token");
	private MessageId parentId = new MessageId(getRandomId());
	private MessageId previousMsgId = new MessageId(getRandomId());
	private String postContent = "Post text";

	private GroupMessageValidator validator =
			new GroupMessageValidator(privateGroupFactory, clientHelper,
					metadataEncoder, clock, authorFactory,
					groupInvitationFactory);

	@Test(expected = FormatException.class)
	public void testRejectTooShortMemberName() throws Exception {
		BdfList list = BdfList.of(JOIN.getInt(), "", memberKey, null,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectTooLongMemberName() throws Exception {
		BdfList list = BdfList.of(JOIN.getInt(),
				getRandomString(MAX_AUTHOR_NAME_LENGTH + 1), memberKey, null,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectTooShortMemberKey() throws Exception {
		BdfList list = BdfList.of(JOIN.getInt(), memberName, new byte[0], null,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectTooLongMemberKey() throws Exception {
		BdfList list = BdfList.of(JOIN.getInt(), memberName,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), null,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectNonRawMemberKey() throws Exception {
		BdfList list =
				BdfList.of(JOIN.getInt(), memberName, "non raw key", null,
						signature);
		validator.validateMessage(message, group, list);
	}

	// JOIN message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortJoinMessage() throws Exception {
		BdfList list = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				null);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongJoinMessage() throws Exception {
		expectCreateAuthor(creator);
		BdfList list = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				null, signature, "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNonListInvitation() throws Exception {
		expectCreateAuthor(creator);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				"not a list", signature);
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptCreatorJoinMessage() throws Exception {
		final BdfList invite = null;
		expectJoinMessage(creator, invite, true, true);
		BdfList list = BdfList.of(JOIN.getInt(), creatorName, creatorKey,
				invite, signature);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertMessageContext(messageContext, creator);
		assertTrue(messageContext.getDictionary()
				.getBoolean(KEY_INITIAL_JOIN_MSG));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsMemberJoinMessageWithoutInvitation()
			throws Exception {
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, null,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortInvitation() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongInvitation() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature, "");
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsJoinMessageWithEqualInvitationTime()
			throws Exception {
		BdfList invite = BdfList.of(message.getTimestamp(), creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsJoinMessageWithLaterInvitationTime()
			throws Exception {
		BdfList invite =
				BdfList.of(message.getTimestamp() + 1, creatorSignature);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithNonRawCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, "non-raw signature");
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooShortCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, new byte[0]);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsJoinMessageWithTooLongCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp,
				getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsJoinMessageWithInvalidCreatorSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, false, true);
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsJoinMessageWithInvalidMemberSignature()
			throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, true, false);
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptMemberJoinMessage() throws Exception {
		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		expectJoinMessage(member, invite, true, true);
		BdfList list = BdfList.of(JOIN.getInt(), memberName, memberKey, invite,
				signature);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertMessageContext(messageContext, member);
		assertFalse(messageContext.getDictionary()
				.getBoolean(KEY_INITIAL_JOIN_MSG));
	}

	private void expectCreateAuthor(final Author member) {
		context.checking(new Expectations() {{
			oneOf(authorFactory)
					.createAuthor(member.getName(), member.getPublicKey());
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
		final BdfList signed =
				BdfList.of(group.getId(), message.getTimestamp(), JOIN.getInt(),
						member.getName(), member.getPublicKey(), invite);
		expectCreateAuthor(member);
		expectParsePrivateGroup();
		context.checking(new Expectations() {{
			if (invite != null) {
				oneOf(groupInvitationFactory)
						.createInviteToken(creator.getId(), member.getId(),
								privateGroup.getId(), inviteTimestamp);
				will(returnValue(token));
				oneOf(clientHelper)
						.verifySignature(SIGNING_LABEL_INVITE, creatorSignature,
								creatorKey, token);
				if (!memberSigValid)
					will(throwException(new GeneralSecurityException()));
			}
			if (memberSigValid) {
				oneOf(clientHelper)
						.verifySignature(SIGNING_LABEL_JOIN, signature,
								member.getPublicKey(), signed);
				if (!creatorSigValid)
					will(throwException(new GeneralSecurityException()));
			}
		}});
	}

	// POST Message

	@Test(expected = FormatException.class)
	public void testRejectsTooShortPost() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongPost() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature, "");
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawParentId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, "non-raw",
						previousMsgId, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortParentId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey,
						getRandomBytes(MessageId.LENGTH - 1), previousMsgId,
						postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongParentId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey,
						getRandomBytes(MessageId.LENGTH + 1), previousMsgId,
						postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawPreviousMsgId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						"non-raw", postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooShortPreviousMsgId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						getRandomBytes(MessageId.LENGTH - 1),
						postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongPreviousMsgId() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						getRandomBytes(MessageId.LENGTH + 1),
						postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithEmptyContent() throws Exception {
		postContent = "";
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongContent() throws Exception {
		postContent = getRandomString(MAX_GROUP_POST_BODY_LENGTH + 1);
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonStringContent() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, getRandomBytes(5), signature);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithEmptySignature() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, new byte[0]);
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithTooLongSignature() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent,
						getRandomBytes(MAX_SIGNATURE_LENGTH + 1));
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPostWithNonRawSignature() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, "non-raw");
		expectCreateAuthor(member);
		validator.validateMessage(message, group, list);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsPostWithInvalidSignature() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature);
		expectPostMessage(member, false);
		validator.validateMessage(message, group, list);
	}

	@Test
	public void testAcceptPost() throws Exception {
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature);
		expectPostMessage(member, true);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertMessageContext(messageContext, member);
		assertEquals(previousMsgId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PREVIOUS_MSG_ID));
		assertEquals(parentId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PARENT_MSG_ID));
	}

	@Test
	public void testAcceptTopLevelPost() throws Exception {
		parentId = null;
		BdfList list =
				BdfList.of(POST.getInt(), memberName, memberKey, parentId,
						previousMsgId, postContent, signature);
		expectPostMessage(member, true);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, list);
		assertMessageContext(messageContext, member);
		assertEquals(previousMsgId.getBytes(),
				messageContext.getDictionary().getRaw(KEY_PREVIOUS_MSG_ID));
		assertFalse(
				messageContext.getDictionary().containsKey(KEY_PARENT_MSG_ID));
	}

	private void expectPostMessage(final Author member, final boolean sigValid)
			throws Exception {
		final BdfList signed =
				BdfList.of(group.getId(), message.getTimestamp(), POST.getInt(),
						member.getName(), member.getPublicKey(),
						parentId == null ? null : parentId.getBytes(),
						previousMsgId == null ? null : previousMsgId.getBytes(),
						postContent);
		expectCreateAuthor(member);
		context.checking(new Expectations() {{
			oneOf(clientHelper)
					.verifySignature(SIGNING_LABEL_POST, signature,
							member.getPublicKey(), signed);
			if (!sigValid) will(throwException(new GeneralSecurityException()));
		}});
	}

	private void assertMessageContext(BdfMessageContext c, Author member)
			throws FormatException {
		BdfDictionary d = c.getDictionary();
		assertTrue(message.getTimestamp() == d.getLong(KEY_TIMESTAMP));
		assertFalse(d.getBoolean(KEY_READ));
		assertEquals(member.getId().getBytes(), d.getRaw(KEY_MEMBER_ID));
		assertEquals(member.getName(), d.getString(KEY_MEMBER_NAME));
		assertEquals(member.getPublicKey(), d.getRaw(KEY_MEMBER_PUBLIC_KEY));

		// assert message dependencies
		if (d.getLong(KEY_TYPE) == POST.getInt()) {
			assertTrue(c.getDependencies().contains(previousMsgId));
			if (parentId != null) {
				assertTrue(c.getDependencies().contains(parentId));
			} else {
				assertFalse(c.getDependencies().contains(parentId));
			}
		} else {
			assertEquals(JOIN.getInt(), d.getLong(KEY_TYPE).intValue());
		}
	}

}
