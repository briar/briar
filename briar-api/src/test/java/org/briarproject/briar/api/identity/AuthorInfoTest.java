package org.briarproject.briar.api.identity;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.briar.api.media.AttachmentHeader;
import org.junit.Test;

import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.NONE;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_BYTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AuthorInfoTest extends BrambleTestCase {

	private final String contentType = getRandomString(MAX_CONTENT_TYPE_BYTES);
	private final AttachmentHeader avatarHeader =
			new AttachmentHeader(new MessageId(getRandomId()), contentType);

	@Test
	public void testEquals() {
		assertEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(NONE, null, null)
		);
		assertEquals(
				new AuthorInfo(NONE, "test", null),
				new AuthorInfo(NONE, "test", null)
		);
		assertEquals(
				new AuthorInfo(NONE, "test", avatarHeader),
				new AuthorInfo(NONE, "test", avatarHeader)
		);

		assertNotEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(VERIFIED)
		);
		assertNotEquals(
				new AuthorInfo(NONE, "test", null),
				new AuthorInfo(NONE)
		);
		assertNotEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(NONE, "test", null)
		);
		assertNotEquals(
				new AuthorInfo(NONE, "a", null),
				new AuthorInfo(NONE, "b", null)
		);
		assertNotEquals(
				new AuthorInfo(NONE, "a", null),
				new AuthorInfo(NONE, "a", avatarHeader)
		);
	}

}
