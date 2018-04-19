package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostFactory;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.test.BriarTestCase;
import org.junit.Test;

import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
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
		injectEagerSingletons(component);
	}

	@Test
	public void testPrivateMessageFitsIntoRecord() throws Exception {
		// Create a maximum-length private message
		GroupId groupId = new GroupId(getRandomId());
		long timestamp = Long.MAX_VALUE;
		String body = getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, body);
		// Check the size of the serialised message
		int length = message.getMessage().getRaw().length;
		assertTrue(
				length > UniqueId.LENGTH + 8 + MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		assertTrue(length <= MAX_RECORD_PAYLOAD_BYTES);
	}

	@Test
	public void testForumPostFitsIntoRecord() throws Exception {
		// Create a maximum-length author
		int formatVersion = Integer.MAX_VALUE;
		String authorName = getRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		PrivateKey privateKey = crypto.generateSignatureKeyPair().getPrivate();
		LocalAuthor author = authorFactory.createLocalAuthor(formatVersion,
				authorName, authorPublic, privateKey.getEncoded());
		// Create a maximum-length forum post
		GroupId groupId = new GroupId(getRandomId());
		long timestamp = Long.MAX_VALUE;
		MessageId parent = new MessageId(getRandomId());
		String body = getRandomString(MAX_FORUM_POST_BODY_LENGTH);
		ForumPost post = forumPostFactory.createPost(groupId,
				timestamp, parent, author, body);
		// Check the size of the serialised message
		int length = post.getMessage().getRaw().length;
		assertTrue(length > UniqueId.LENGTH + 8 + UniqueId.LENGTH + 4
				+ MAX_AUTHOR_NAME_LENGTH + MAX_PUBLIC_KEY_LENGTH
				+ MAX_FORUM_POST_BODY_LENGTH);
		assertTrue(length <= MAX_RECORD_PAYLOAD_BYTES);
	}

	private static void injectEagerSingletons(
			MessageSizeIntegrationTestComponent component) {
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new CryptoExecutorModule.EagerSingletons());
		component.inject(new ForumModule.EagerSingletons());
		component.inject(new IdentityModule.EagerSingletons());
		component.inject(new MessagingModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new SystemModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new VersioningModule.EagerSingletons());
	}
}
