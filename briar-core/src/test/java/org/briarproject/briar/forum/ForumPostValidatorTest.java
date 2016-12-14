package org.briarproject.briar.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Collection;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.briar.api.forum.ForumPostFactory.SIGNING_LABEL_POST;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ForumPostValidatorTest extends ValidatorTestCase {

	private final MessageId parentId = new MessageId(TestUtils.getRandomId());
	private final String authorName =
			TestUtils.getRandomString(MAX_AUTHOR_NAME_LENGTH);
	private final byte[] authorPublicKey =
			TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final BdfList authorList = BdfList.of(authorName, authorPublicKey);
	private final String content =
			TestUtils.getRandomString(MAX_FORUM_POST_BODY_LENGTH);
	private final byte[] signature =
			TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH);
	private final AuthorId authorId = new AuthorId(TestUtils.getRandomId());
	private final Author author =
			new Author(authorId, authorName, authorPublicKey);
	private final BdfList signedWithParent = BdfList.of(groupId, timestamp,
			parentId.getBytes(), authorList, content);
	private final BdfList signedWithoutParent = BdfList.of(groupId, timestamp,
			null, authorList, content);

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBody() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBody() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, signature, 123));
	}

	@Test
	public void testAcceptsNullParentId() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					authorPublicKey, signedWithoutParent);
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(null, authorList, content, signature));
		assertExpectedContext(messageContext, false, authorName);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawParentId() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(123, authorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortParentId() throws Exception {
		byte[] invalidParentId = TestUtils.getRandomBytes(UniqueId.LENGTH - 1);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(invalidParentId, authorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongParentId() throws Exception {
		byte[] invalidParentId = TestUtils.getRandomBytes(UniqueId.LENGTH + 1);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(invalidParentId, authorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullAuthorList() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, null, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonListAuthorList() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, 123, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortAuthorList() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, new BdfList(), content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAuthorList() throws Exception {
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, BdfList.of(1, 2, 3), content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullAuthorName() throws Exception {
		BdfList invalidAuthorList = BdfList.of(null, authorPublicKey);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringAuthorName() throws Exception {
		BdfList invalidAuthorList = BdfList.of(123, authorPublicKey);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortAuthorName() throws Exception {
		BdfList invalidAuthorList = BdfList.of("", authorPublicKey);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test
	public void testAcceptsMinLengthAuthorName() throws Exception {
		final String shortAuthorName = TestUtils.getRandomString(1);
		BdfList shortNameAuthorList =
				BdfList.of(shortAuthorName, authorPublicKey);
		final Author shortNameAuthor =
				new Author(authorId, shortAuthorName, authorPublicKey);
		final BdfList signedWithShortNameAuthor = BdfList.of(groupId, timestamp,
				parentId.getBytes(), shortNameAuthorList, content);

		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(shortAuthorName, authorPublicKey);
			will(returnValue(shortNameAuthor));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					authorPublicKey, signedWithShortNameAuthor);
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(parentId, shortNameAuthorList, content, signature));
		assertExpectedContext(messageContext, true, shortAuthorName);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAuthorName() throws Exception {
		String invalidAuthorName =
				TestUtils.getRandomString(MAX_AUTHOR_NAME_LENGTH + 1);
		BdfList invalidAuthorList =
				BdfList.of(invalidAuthorName, authorPublicKey);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullAuthorPublicKey() throws Exception {
		BdfList invalidAuthorList = BdfList.of(authorName, null);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawAuthorPublicKey() throws Exception {
		BdfList invalidAuthorList = BdfList.of(authorName, 123);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongAuthorPublicKey() throws Exception {
		byte[] invalidAuthorPublicKey =
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1);
		BdfList invalidAuthorList =
				BdfList.of(authorName, invalidAuthorPublicKey);
		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, invalidAuthorList, content, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullContent() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, null, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, 123, signature));
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		String shortContent = "";
		final BdfList signedWithShortContent = BdfList.of(groupId, timestamp,
				parentId.getBytes(), authorList, shortContent);

		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					authorPublicKey, signedWithShortContent);
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(parentId, authorList, shortContent, signature));
		assertExpectedContext(messageContext, true, authorName);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		String invalidContent =
				TestUtils.getRandomString(MAX_FORUM_POST_BODY_LENGTH + 1);

		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, invalidContent, signature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSignature() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSignature() throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSignature() throws Exception {
		byte[] invalidSignature =
				TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH + 1);

		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, invalidSignature));
	}

	@Test(expected = FormatException.class)
	public void testRejectsIfVerifyingSignatureThrowsFormatException()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					authorPublicKey, signedWithParent);
			will(throwException(new FormatException()));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, signature));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsIfVerifyingSignatureThrowsGeneralSecurityException()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(authorName, authorPublicKey);
			will(returnValue(author));
			oneOf(clientHelper).verifySignature(SIGNING_LABEL_POST, signature,
					authorPublicKey, signedWithParent);
			will(throwException(new GeneralSecurityException()));
		}});

		ForumPostValidator v = new ForumPostValidator(authorFactory,
				clientHelper, metadataEncoder, clock);
		v.validateMessage(message, group,
				BdfList.of(parentId, authorList, content, signature));
	}

	private void assertExpectedContext(BdfMessageContext messageContext,
			boolean hasParent, String authorName) throws FormatException {
		BdfDictionary meta = messageContext.getDictionary();
		Collection<MessageId> dependencies = messageContext.getDependencies();
		if (hasParent) {
			assertEquals(4, meta.size());
			assertArrayEquals(parentId.getBytes(), meta.getRaw("parent"));
			assertEquals(1, dependencies.size());
			assertEquals(parentId, dependencies.iterator().next());
		} else {
			assertEquals(3, meta.size());
			assertEquals(0, dependencies.size());
		}
		assertEquals(timestamp, meta.getLong("timestamp").longValue());
		assertFalse(meta.getBoolean("read"));
		BdfDictionary authorMeta = meta.getDictionary("author");
		assertEquals(3, authorMeta.size());
		assertArrayEquals(authorId.getBytes(), authorMeta.getRaw("id"));
		assertEquals(authorName, authorMeta.getString("name"));
		assertArrayEquals(authorPublicKey, authorMeta.getRaw("publicKey"));
	}
}
