package org.briarproject.blogs;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.system.SystemClock;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_COMMENT;
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_CURRENT_MSG_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_ORIGINAL_MSG_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.blogs.MessageType.COMMENT;
import static org.briarproject.api.blogs.MessageType.POST;
import static org.briarproject.api.blogs.MessageType.WRAPPED_COMMENT;
import static org.briarproject.api.blogs.MessageType.WRAPPED_POST;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BlogPostValidatorTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final Blog blog;
	private final Author author;
	private final BdfDictionary authorDict;
	private final ClientId clientId;
	private final byte[] descriptor;
	private final Group group;
	private final Message message;
	private final BlogPostValidator validator;
	private final CryptoComponent cryptoComponent =
			context.mock(CryptoComponent.class);
	private final GroupFactory groupFactory = context.mock(GroupFactory.class);
	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final Clock clock = new SystemClock();
	private final byte[] body = TestUtils.getRandomBytes(
			MAX_BLOG_POST_BODY_LENGTH);
	private final String contentType = "text/plain";

	public BlogPostValidatorTest() {
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		clientId = BlogManagerImpl.CLIENT_ID;
		descriptor = TestUtils.getRandomBytes(42);
		group = new Group(groupId, clientId, descriptor);
		AuthorId authorId =
				new AuthorId(TestUtils.getRandomBytes(AuthorId.LENGTH));
		byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		author = new Author(authorId, "Author", publicKey);
		authorDict = BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, author.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, author.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, author.getPublicKey())
		);
		blog = new Blog(group, "Test Blog", "", author);

		MessageId messageId = new MessageId(TestUtils.getRandomId());
		long timestamp = System.currentTimeMillis();
		byte[] raw = TestUtils.getRandomBytes(123);
		message = new Message(messageId, group.getId(), timestamp, raw);

		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		validator = new BlogPostValidator(cryptoComponent, groupFactory,
				messageFactory, blogFactory, clientHelper, metadataEncoder,
				clock);
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateProperBlogPost()
			throws IOException, GeneralSecurityException {
		// content type, title (optional), post body, attachments
		BdfList content = BdfList.of(null, contentType, null, body, null);
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(POST.getInt(), content, sigBytes);

		BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), content);
		expectCrypto(signed, sigBytes, true);
		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertEquals(contentType, result.getString(KEY_CONTENT_TYPE));
		assertEquals(authorDict, result.getDictionary(KEY_AUTHOR));
		assertFalse(result.getBoolean(KEY_READ));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateBlogPostWithoutAttachments()
			throws IOException, GeneralSecurityException {
		BdfList content = BdfList.of(null, contentType, null, body);
		BdfList m = BdfList.of(POST.getInt(), content, null);

		validator.validateMessage(message, group, m).getDictionary();
	}

	@Test(expected = FormatException.class)
	public void testValidateBlogPostWithoutSignature()
			throws IOException, GeneralSecurityException {
		BdfList content = BdfList.of(null, contentType, null, body, null);
		BdfList m = BdfList.of(POST.getInt(), content, null);

		validator.validateMessage(message, group, m).getDictionary();
	}

	@Test(expected = InvalidMessageException.class)
	public void testValidateBlogPostWithBadSignature()
			throws IOException, GeneralSecurityException {
		// content type, title (optional), post body, attachments
		BdfList content = BdfList.of(null, contentType, null, body, null);
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(POST.getInt(), content, sigBytes);

		BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), content);
		expectCrypto(signed, sigBytes, false);
		validator.validateMessage(message, group, m).getDictionary();
	}

	@Test
	public void testValidateProperBlogComment()
			throws IOException, GeneralSecurityException {
		// comment, parent_original_id, signature, parent_current_id
		String comment = "This is a blog comment";
		MessageId originalId = new MessageId(TestUtils.getRandomId());
		byte[] currentId = TestUtils.getRandomId();
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(COMMENT.getInt(), comment, originalId,
				sigBytes, currentId);

		BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), comment,
						originalId);
		expectCrypto(signed, sigBytes, true);
		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertEquals(comment, result.getString(KEY_COMMENT));
		assertEquals(authorDict, result.getDictionary(KEY_AUTHOR));
		assertEquals(originalId.getBytes(), result.getRaw(KEY_ORIGINAL_MSG_ID));
		assertEquals(currentId, result.getRaw(KEY_CURRENT_MSG_ID));
		assertFalse(result.getBoolean(KEY_READ));
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateProperEmptyBlogComment()
			throws IOException, GeneralSecurityException {
		// comment, parent_original_id, signature, parent_current_id
		MessageId originalId = new MessageId(TestUtils.getRandomId());
		byte[] currentId = TestUtils.getRandomId();
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(COMMENT.getInt(), null, originalId, sigBytes,
				currentId);

		BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), null,
						originalId);
		expectCrypto(signed, sigBytes, true);
		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertFalse(result.containsKey(KEY_COMMENT));
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateProperWrappedPost()
			throws IOException, GeneralSecurityException {
		// group descriptor, timestamp, content, signature
		BdfList content = BdfList.of(null, contentType, null, body, null);
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m =
				BdfList.of(WRAPPED_POST.getInt(), descriptor,
						message.getTimestamp(), content, sigBytes);

		BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), content);
		expectCrypto(signed, sigBytes, true);

		final BdfList originalList = BdfList.of(content, sigBytes);
		final byte[] originalBody = TestUtils.getRandomBytes(42);

		context.checking(new Expectations() {{
			oneOf(groupFactory).createGroup(clientId, descriptor);
			will(returnValue(blog.getGroup()));
			oneOf(clientHelper).toByteArray(originalList);
			will(returnValue(originalBody));
			oneOf(messageFactory)
					.createMessage(group.getId(), message.getTimestamp(),
							originalBody);
			will(returnValue(message));
		}});

		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertEquals(contentType, result.getString(KEY_CONTENT_TYPE));
		assertEquals(authorDict, result.getDictionary(KEY_AUTHOR));
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateProperWrappedComment()
			throws IOException, GeneralSecurityException {
		// group descriptor, timestamp, comment, parent_original_id, signature,
		// parent_current_id
		String comment = "This is another comment";
		MessageId originalId = new MessageId(TestUtils.getRandomId());
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		MessageId currentId = new MessageId(TestUtils.getRandomId());
		BdfList m = BdfList.of(WRAPPED_COMMENT.getInt(), descriptor,
				message.getTimestamp(), comment, originalId, sigBytes,
				currentId);

		BdfList signed = BdfList.of(blog.getId(), message.getTimestamp(),
				comment, originalId);
		expectCrypto(signed, sigBytes, true);

		final BdfList originalList = BdfList.of(comment, originalId, sigBytes,
				currentId);
		final byte[] originalBody = TestUtils.getRandomBytes(42);

		context.checking(new Expectations() {{
			oneOf(groupFactory).createGroup(clientId, descriptor);
			will(returnValue(blog.getGroup()));
			oneOf(clientHelper).toByteArray(originalList);
			will(returnValue(originalBody));
			oneOf(messageFactory)
					.createMessage(group.getId(), message.getTimestamp(),
							originalBody);
			will(returnValue(message));
		}});

		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertEquals(comment, result.getString(KEY_COMMENT));
		assertEquals(authorDict, result.getDictionary(KEY_AUTHOR));
		assertEquals(
				message.getId().getBytes(), result.getRaw(KEY_ORIGINAL_MSG_ID));
		assertEquals(currentId.getBytes(), result.getRaw(KEY_CURRENT_MSG_ID));
		context.assertIsSatisfied();
	}

	private void expectCrypto(final BdfList signed, final byte[] sig,
			final boolean pass) throws IOException, GeneralSecurityException {
		final Signature signature = context.mock(Signature.class);
		final KeyParser keyParser = context.mock(KeyParser.class);
		final PublicKey publicKey = context.mock(PublicKey.class);

		context.checking(new Expectations() {{
			oneOf(blogFactory).parseBlog(group, "");
			will(returnValue(blog));
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(blog.getAuthor().getPublicKey());
			will(returnValue(publicKey));
			oneOf(cryptoComponent).getSignature();
			will(returnValue(signature));
			oneOf(signature).initVerify(publicKey);
			oneOf(clientHelper).toByteArray(signed);
			will(returnValue(sig));
			oneOf(signature).update(sig);
			oneOf(signature).verify(sig);
			will(returnValue(pass));
		}});
	}

}
