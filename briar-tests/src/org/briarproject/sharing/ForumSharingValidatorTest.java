package org.briarproject.sharing;

import org.briarproject.TestUtils;
import org.briarproject.ValidatorTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.system.Clock;
import org.junit.Test;

import javax.annotation.Nullable;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.api.sharing.SharingConstants.LOCAL;
import static org.briarproject.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.sharing.SharingConstants.TIME;
import static org.briarproject.api.sharing.SharingConstants.TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ForumSharingValidatorTest extends ValidatorTestCase {

	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final Clock clock = context.mock(Clock.class);

	private final SessionId sessionId = new SessionId(TestUtils.getRandomId());
	private final String forumName =
			TestUtils.getRandomString(MAX_FORUM_NAME_LENGTH);
	private final byte[] salt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH);
	private final String content =
			TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH);

	@Test
	public void testAcceptsInvitationWithContent() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, content));
		assertExpectedContextForInvitation(messageContext, forumName, content);
	}

	@Test
	public void testAcceptsInvitationWithoutContent() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt));
		assertExpectedContextForInvitation(messageContext, forumName, null);
	}

	@Test
	public void testAcceptsAccept() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ACCEPT, sessionId));
		assertExpectedContext(messageContext, SHARE_MSG_TYPE_ACCEPT);
	}

	@Test
	public void testAcceptsDecline() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_DECLINE, sessionId));
		assertExpectedContext(messageContext, SHARE_MSG_TYPE_DECLINE);
	}

	@Test
	public void testAcceptsLeave() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_LEAVE, sessionId));
		assertExpectedContext(messageContext, SHARE_MSG_TYPE_LEAVE);
	}

	@Test
	public void testAcceptsAbort() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, sessionId));
		assertExpectedContext(messageContext, SHARE_MSG_TYPE_ABORT);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullMessageType() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of(null, sessionId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongMessageType() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of("", sessionId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidMessageType() throws Exception {
		int invalidMessageType = SHARE_MSG_TYPE_ABORT + 1;
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(invalidMessageType, sessionId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSessionId() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSessionId() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSessionId() throws Exception {
		byte[] invalidSessionId = TestUtils.getRandomBytes(UniqueId.LENGTH - 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, invalidSessionId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSessionId() throws Exception {
		byte[] invalidSessionId = TestUtils.getRandomBytes(UniqueId.LENGTH + 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, invalidSessionId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAbort() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of(SHARE_MSG_TYPE_ABORT));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAbort() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_ABORT, sessionId, 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForInvitation() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForInvitation() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, content, 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullForumName() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, null,
						salt, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringForumName() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, 123,
						salt, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortForumName() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, "",
						salt, content));
	}

	@Test
	public void testAcceptsMinLengthForumName() throws Exception {
		String shortForumName = TestUtils.getRandomString(1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, shortForumName,
						salt, content));
		assertExpectedContextForInvitation(messageContext, shortForumName,
				content);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongForumName() throws Exception {
		String invalidForumName =
				TestUtils.getRandomString(MAX_FORUM_NAME_LENGTH + 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId,
						invalidForumName, salt, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSalt() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						null, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSalt() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						123, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSalt() throws Exception {
		byte[] invalidSalt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH - 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						invalidSalt, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSalt() throws Exception {
		byte[] invalidSalt = TestUtils.getRandomBytes(FORUM_SALT_LENGTH + 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						invalidSalt, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullContent() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, 123));
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, ""));
		assertExpectedContextForInvitation(messageContext, forumName, "");
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		String invalidContent =
				TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH + 1);
		ForumSharingValidator v = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(SHARE_MSG_TYPE_INVITATION, sessionId, forumName,
						salt, invalidContent));
	}

	private void assertExpectedContextForInvitation(
			BdfMessageContext messageContext, String forumName,
			@Nullable String content) throws FormatException {
		BdfDictionary meta = messageContext.getDictionary();
		if (content == null) {
			assertEquals(6, meta.size());
		} else {
			assertEquals(7, meta.size());
			assertEquals(content, meta.getString(INVITATION_MSG));
		}
		assertEquals(forumName, meta.getString(FORUM_NAME));
		assertEquals(salt, meta.getRaw(FORUM_SALT));
		assertEquals(SHARE_MSG_TYPE_INVITATION, meta.getLong(TYPE).intValue());
		assertEquals(sessionId.getBytes(), meta.getRaw(SESSION_ID));
		assertFalse(meta.getBoolean(LOCAL));
		assertEquals(timestamp, meta.getLong(TIME).longValue());
		assertEquals(0, messageContext.getDependencies().size());
	}

	private void assertExpectedContext(BdfMessageContext messageContext,
			int type) throws FormatException {
		BdfDictionary meta = messageContext.getDictionary();
		assertEquals(4, meta.size());
		assertEquals(type, meta.getLong(TYPE).intValue());
		assertEquals(sessionId.getBytes(), meta.getRaw(SESSION_ID));
		assertFalse(meta.getBoolean(LOCAL));
		assertEquals(timestamp, meta.getLong(TIME).longValue());
		assertEquals(0, messageContext.getDependencies().size());
	}
}
