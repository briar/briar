package org.briarproject;

import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.forum.ForumConstants;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.system.SystemModule;
import org.briarproject.util.StringUtils;
import org.junit.Test;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;
import static org.junit.Assert.assertTrue;

public class MessageSizeIntegrationTest extends BriarTestCase {

	@Inject
	CryptoComponent crypto;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	PrivateMessageFactory privateMessageFactory;
	@Inject
	ForumPostFactory forumPostFactory;

	public MessageSizeIntegrationTest() throws Exception {
		MessageSizeIntegrationTestComponent component =
				DaggerMessageSizeIntegrationTestComponent.builder().build();
		component.inject(this);
		component.inject(new SystemModule.EagerSingletons());
	}

	@Test
	public void testPrivateMessageFitsIntoPacket() throws Exception {
		// Create a maximum-length private message
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		long timestamp = Long.MAX_VALUE;
		String body =
				StringUtils.fromUtf8(new byte[MAX_PRIVATE_MESSAGE_BODY_LENGTH]);
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, body);
		// Check the size of the serialised message
		int length = message.getMessage().getRaw().length;
		assertTrue(
				length > UniqueId.LENGTH + 8 + MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		assertTrue(length <= MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testForumPostFitsIntoPacket() throws Exception {
		// Create a maximum-length author
		String authorName = TestUtils.getRandomString(
				MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		PrivateKey privateKey = crypto.generateSignatureKeyPair().getPrivate();
		LocalAuthor author = authorFactory
				.createLocalAuthor(authorName, authorPublic,
						privateKey.getEncoded());
		// Create a maximum-length forum post
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		long timestamp = Long.MAX_VALUE;
		MessageId parent = new MessageId(TestUtils.getRandomId());
		String body = TestUtils.getRandomString(MAX_FORUM_POST_BODY_LENGTH);
		ForumPost post = forumPostFactory.createPost(groupId,
				timestamp, parent, author, body);
		// Check the size of the serialised message
		int length = post.getMessage().getRaw().length;
		assertTrue(length > UniqueId.LENGTH + 8 + UniqueId.LENGTH
				+ MAX_AUTHOR_NAME_LENGTH + MAX_PUBLIC_KEY_LENGTH
				+ ForumConstants.MAX_CONTENT_TYPE_LENGTH
				+ MAX_FORUM_POST_BODY_LENGTH);
		assertTrue(length <= MAX_PACKET_PAYLOAD_LENGTH);
	}
}
