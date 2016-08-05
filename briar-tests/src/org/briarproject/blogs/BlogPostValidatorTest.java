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
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
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
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BlogPostValidatorTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final Blog blog;
	private final Author author;
	private final Group group;
	private final Message message;
	private final BlogPostValidator validator;
	private final CryptoComponent cryptoComponent =
			context.mock(CryptoComponent.class);
	private final BlogFactory blogFactory = context.mock(BlogFactory.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final Clock clock = new SystemClock();
	private final byte[] body = TestUtils.getRandomBytes(
			MAX_BLOG_POST_BODY_LENGTH);
	private final String contentType = "text/plain";

	public BlogPostValidatorTest() {
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		ClientId clientId = new ClientId(TestUtils.getRandomId());
		byte[] descriptor = TestUtils.getRandomBytes(12);
		group = new Group(groupId, clientId, descriptor);
		AuthorId authorId = new AuthorId(TestUtils.getRandomBytes(AuthorId.LENGTH));
		byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		author = new Author(authorId, "Author", publicKey);
		blog = new Blog(group, "Test Blog", "", author);

		MessageId messageId = new MessageId(TestUtils.getRandomId());
		long timestamp = System.currentTimeMillis();
		byte[] raw = TestUtils.getRandomBytes(123);
		message = new Message(messageId, group.getId(), timestamp, raw);

		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		validator = new BlogPostValidator(cryptoComponent, blogFactory,
				clientHelper, metadataEncoder, clock);
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateProperBlogPost()
			throws IOException, GeneralSecurityException {
		// Parent ID, content type, title (optional), post body, attachments
		BdfList content = BdfList.of(null, contentType, null, body, null);
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(content, sigBytes);

		expectCrypto(m, true);
		final BdfDictionary result =
				validator.validateMessage(message, group, m).getDictionary();

		assertEquals(contentType, result.getString(KEY_CONTENT_TYPE));
		BdfDictionary authorDict = BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, author.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, author.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, author.getPublicKey())
		);
		assertEquals(authorDict, result.getDictionary(KEY_AUTHOR));
		assertFalse(result.getBoolean(KEY_READ));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateBlogPostWithoutAttachments()
			throws IOException, GeneralSecurityException {
		BdfList content = BdfList.of(null, contentType, null, body);
		BdfList m = BdfList.of(content, null);

		validator.validateMessage(message, group, m).getDictionary();
	}

	@Test(expected = FormatException.class)
	public void testValidateBlogPostWithoutSignature()
			throws IOException, GeneralSecurityException {
		BdfList content = BdfList.of(null, contentType, null, body, null);
		BdfList m = BdfList.of(content, null);

		validator.validateMessage(message, group, m).getDictionary();
	}

	@Test(expected = InvalidMessageException.class)
	public void testValidateBlogPostWithBadSignature()
			throws IOException, GeneralSecurityException {
		// Parent ID, content type, title (optional), post body, attachments
		BdfList content = BdfList.of(null, contentType, null, body, null);
		final byte[] sigBytes = TestUtils.getRandomBytes(42);
		BdfList m = BdfList.of(content, sigBytes);

		expectCrypto(m, false);
		validator.validateMessage(message, group, m).getDictionary();
	}

	private void expectCrypto(BdfList m, final boolean pass)
			throws IOException, GeneralSecurityException {
		final Signature signature = context.mock(Signature.class);
		final KeyParser keyParser = context.mock(KeyParser.class);
		final PublicKey publicKey = context.mock(PublicKey.class);

		final BdfList content = m.getList(0);
		final byte[] sigBytes = m.getRaw(1);

		final BdfList signed =
				BdfList.of(blog.getId(), message.getTimestamp(), content);

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
			will(returnValue(sigBytes));
			oneOf(signature).update(sigBytes);
			oneOf(signature).verify(sigBytes);
			will(returnValue(pass));
		}});
	}

}
