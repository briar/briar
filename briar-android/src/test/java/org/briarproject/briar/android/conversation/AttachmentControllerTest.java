package org.briarproject.briar.android.conversation;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.android.conversation.ImageHelper.DecodeResult;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttachmentControllerTest extends BrambleMockTestCase {

	private final AttachmentDimensions dimensions = new AttachmentDimensions(
			100, 50, 200, 75, 300
	);
	private final MessageId msgId = new MessageId(getRandomId());
	private final Attachment attachment = new Attachment(
			new BufferedInputStream(
					new ByteArrayInputStream(getRandomBytes(42))));

	private final MessagingManager messagingManager =
			context.mock(MessagingManager.class);
	private final ImageHelper imageHelper = context.mock(ImageHelper.class);
	private final AttachmentController controller =
			new AttachmentController(
					messagingManager,
					dimensions,
					imageHelper
			);

	@Test
	public void testNoSize() {
		String mimeType = "image/jpeg";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item =
				controller.getAttachmentItem(h, attachment, false);
		assertEquals(mimeType, item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testNoSizeWrongMimeTypeProducesError() {
		String mimeType = "application/octet-stream";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue(null));
		}});

		AttachmentItem item =
				controller.getAttachmentItem(h, attachment, false);
		assertTrue(item.hasError());
	}

	@Test
	public void testSmallJpegImage() {
		String mimeType = "image/jpeg";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).decodeStream(with(any(InputStream.class)));
			will(returnValue(new DecodeResult(160, 240, mimeType)));
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item = controller.getAttachmentItem(h, attachment, true);
		assertEquals(msgId, item.getMessageId());
		assertEquals(160, item.getWidth());
		assertEquals(240, item.getHeight());
		assertEquals(160, item.getThumbnailWidth());
		assertEquals(240, item.getThumbnailHeight());
		assertEquals(mimeType, item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testBigJpegImage() {
		String mimeType = "image/jpeg";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).decodeStream(with(any(InputStream.class)));
			will(returnValue(new DecodeResult(1728, 2592, mimeType)));
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item = controller.getAttachmentItem(h, attachment, true);
		assertEquals(1728, item.getWidth());
		assertEquals(2592, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxHeight, item.getThumbnailHeight());
		assertFalse(item.hasError());
	}

	@Test
	public void testMalformedError() {
		AttachmentHeader h = getAttachmentHeader("image/jpeg");

		context.checking(new Expectations() {{
			oneOf(imageHelper).decodeStream(with(any(InputStream.class)));
			will(returnValue(new DecodeResult(0, 0, "")));
			oneOf(imageHelper).getExtensionFromMimeType("");
			will(returnValue(null));
		}});

		AttachmentItem item = controller.getAttachmentItem(h, attachment, true);
		assertTrue(item.hasError());
	}

	private AttachmentHeader getAttachmentHeader(String contentType) {
		return new AttachmentHeader(msgId, contentType);
	}

}
