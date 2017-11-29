package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.blog.Blog;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.sharing.MessageType.INVITE;

public class BlogSharingValidatorTest extends SharingValidatorTest {

	private final Author author = getAuthor();
	private final Blog blog = new Blog(group, author, false);
	private final BdfList authorList = BdfList.of(author.getFormatVersion(),
			author.getName(), author.getPublicKey());
	private final BdfList descriptor = BdfList.of(authorList, false);
	private final String content =
			getRandomString(MAX_INVITATION_MESSAGE_LENGTH);

	@Override
	SharingValidator getValidator() {
		return new BlogSharingValidator(messageEncoder, clientHelper,
				metadataEncoder, clock, blogFactory);
	}

	@Test
	public void testAcceptsInvitationWithContent() throws Exception {
		expectCreateBlog();
		expectEncodeMetadata(INVITE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						content));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullContent() throws Exception {
		expectCreateBlog();
		expectEncodeMetadata(INVITE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsInvitationWithNullPreviousMsgId() throws Exception {
		expectCreateBlog();
		expectEncodeMetadata(INVITE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), null, descriptor, content));
		assertExpectedContext(context, null);
	}

	@Test
	public void testAcceptsInvitationForRssBlog() throws Exception {
		expectCreateRssBlog();
		expectEncodeMetadata(INVITE);
		BdfList rssDescriptor = BdfList.of(authorList, true);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, rssDescriptor,
						content));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullAuthor() throws Exception {
		BdfList invalidDescriptor = BdfList.of(null, false);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonListAuthor() throws Exception {
		BdfList invalidDescriptor = BdfList.of(123, false);
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, invalidDescriptor,
						null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		expectCreateBlog();
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						123));
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		String shortContent = getRandomString(1);
		expectCreateBlog();
		expectEncodeMetadata(INVITE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						shortContent));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		String invalidContent =
				getRandomString(MAX_INVITATION_MESSAGE_LENGTH + 1);
		expectCreateBlog();
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor,
						invalidContent));
	}

	private void expectCreateBlog() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(authorList);
			will(returnValue(author));
			oneOf(blogFactory).createBlog(author);
			will(returnValue(blog));
		}});
	}

	private void expectCreateRssBlog() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAuthor(authorList);
			will(returnValue(author));
			oneOf(blogFactory).createFeedBlog(author);
			will(returnValue(blog));
		}});
	}
}
