package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.blog.Blog;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_NAME_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.sharing.MessageType.INVITE;

public class BlogSharingValidatorTest extends SharingValidatorTest {

	private final AuthorId authorId = new AuthorId(getRandomId());
	private final String authorName = TestUtils.getRandomString(42);
	private final byte[] publicKey =
			TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final Author author = new Author(authorId, authorName, publicKey);
	private final Blog blog = new Blog(group, author, false);
	private final BdfList descriptor = BdfList.of(authorName, publicKey, false);
	private final String content =
			TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH);

	@Override
	SharingValidator getValidator() {
		return new BlogSharingValidator(messageEncoder, clientHelper,
				metadataEncoder, clock, blogFactory, authorFactory);
	}

	@Test
	public void testAcceptsInvitationWithContent() throws Exception {
		expectCreateBlog(authorName, publicKey);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						content));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullContent() throws Exception {
		expectCreateBlog(authorName, publicKey);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullPreviousMsgId() throws Exception {
		expectCreateBlog(authorName, publicKey);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), null, descriptor, null));
		assertExpectedContext(messageContext, null);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullBlogName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(null, publicKey, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringBlogName() throws Exception {
		BdfList invalidDescriptor = BdfList.of(123, publicKey, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBlogName() throws Exception {
		BdfList invalidDescriptor = BdfList.of("", publicKey, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test
	public void testAcceptsMinLengthBlogName() throws Exception {
		String shortBlogName = TestUtils.getRandomString(1);
		BdfList validDescriptor = BdfList.of(shortBlogName, publicKey, false);
		expectCreateBlog(shortBlogName, publicKey);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, validDescriptor,
						null));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBlogName() throws Exception {
		String invalidBlogName =
				TestUtils.getRandomString(MAX_BLOG_NAME_LENGTH + 1);
		BdfList invalidDescriptor =
				BdfList.of(invalidBlogName, publicKey, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullPublicKey() throws Exception {
		BdfList invalidDescriptor = BdfList.of(authorName, null, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawPublicKey() throws Exception {
		BdfList invalidDescriptor = BdfList.of(authorName, 123, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongPublicKey() throws Exception {
		byte[] invalidKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1);
		BdfList invalidDescriptor = BdfList.of(authorName, invalidKey, false);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test
	public void testAcceptsMinLengthPublicKey() throws Exception {
		byte[] key = TestUtils.getRandomBytes(1);
		BdfList validDescriptor = BdfList.of(authorName, key, false);

		expectCreateBlog(authorName, key);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, validDescriptor,
						null));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		expectCreateBlog(authorName, publicKey);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						123));
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		expectCreateBlog(authorName, publicKey);
		expectEncodeMetadata(INVITE);
		BdfMessageContext messageContext = v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, "1"));
		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		String invalidContent =
				TestUtils.getRandomString(MAX_INVITATION_MESSAGE_LENGTH + 1);
		expectCreateBlog(authorName, publicKey);
		v.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						invalidContent));
	}

	private void expectCreateBlog(final String name, final byte[] key) {
		context.checking(new Expectations() {{
			oneOf(authorFactory).createAuthor(name, key);
			will(returnValue(author));
			oneOf(blogFactory).createBlog(author);
			will(returnValue(blog));
		}});
	}

}
