package org.briarproject.briar.android.conversation;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

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
	private final ImageManager imageManager = context.mock(ImageManager.class);
	private final AttachmentController controller =
			new AttachmentController(
					messagingManager,
					dimensions,
					imageManager
			);

	@Test
	public void testNoSize() {
		String mimeType = "image/jpeg";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageManager).getExtensionFromMimeType(mimeType);
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
			oneOf(imageManager).getExtensionFromMimeType(mimeType);
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

		context.checking(new BitmapDecoderExpectations() {{
			withDecodeStream(imageManager, options -> {
				options.outWidth = 160;
				options.outHeight = 240;
				options.outMimeType = mimeType;
			});
			oneOf(imageManager).getExtensionFromMimeType(mimeType);
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
	public void testImageHealsWrongMimeType() {
		AttachmentHeader h = getAttachmentHeader("image/png");

		context.checking(new BitmapDecoderExpectations() {{
			withDecodeStream(imageManager, options -> {
				options.outWidth = 160;
				options.outHeight = 240;
				options.outMimeType = "image/jpeg";
			});
			oneOf(imageManager).getExtensionFromMimeType("image/jpeg");
			will(returnValue("jpg"));
		}});

		AttachmentItem item = controller.getAttachmentItem(h, attachment, true);
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testBigJpegImage() {
		String mimeType = "image/jpeg";
		AttachmentHeader h = getAttachmentHeader(mimeType);

		context.checking(new BitmapDecoderExpectations() {{
			withDecodeStream(imageManager, options -> {
				options.outWidth = 1728;
				options.outHeight = 2592;
				options.outMimeType = mimeType;
			});
			oneOf(imageManager).getExtensionFromMimeType(mimeType);
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

		context.checking(new BitmapDecoderExpectations() {{
			withDecodeStream(imageManager, options -> {
				options.outWidth = 0;
				options.outHeight = 0;
			});
			oneOf(imageManager).getExtensionFromMimeType("");
			will(returnValue(null));
		}});

		AttachmentItem item = controller.getAttachmentItem(h, attachment, true);
		assertTrue(item.hasError());
	}

	private AttachmentHeader getAttachmentHeader(String contentType) {
		return new AttachmentHeader(msgId, contentType);
	}

}
