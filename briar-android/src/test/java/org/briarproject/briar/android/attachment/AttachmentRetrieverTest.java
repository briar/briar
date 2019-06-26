package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttachmentRetrieverTest extends BrambleMockTestCase {

	private final AttachmentDimensions dimensions = new AttachmentDimensions(
			100, 50, 200, 75, 300
	);
	private final MessageId msgId = new MessageId(getRandomId());
	private final ImageHelper imageHelper = context.mock(ImageHelper.class);
	private final ImageSizeCalculator imageSizeCalculator;
	private final AttachmentRetriever retriever;

	public AttachmentRetrieverTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		MessagingManager messagingManager =
				context.mock(MessagingManager.class);
		imageSizeCalculator = context.mock(ImageSizeCalculator.class);
		retriever = new AttachmentRetrieverImpl(messagingManager, dimensions,
				imageHelper, imageSizeCalculator);
	}

	@Test
	public void testNoSize() {
		String mimeType = "image/jpeg";
		Attachment attachment = getAttachment(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item = retriever.getAttachmentItem(attachment, false);
		assertEquals(mimeType, item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testNoSizeWrongMimeTypeProducesError() {
		String mimeType = "application/octet-stream";
		Attachment attachment = getAttachment(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue(null));
		}});

		AttachmentItem item = retriever.getAttachmentItem(attachment, false);
		assertTrue(item.hasError());
	}

	@Test
	public void testSmallJpegImage() {
		String mimeType = "image/jpeg";
		Attachment attachment = getAttachment(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageSizeCalculator).getSize(with(any(InputStream.class)),
					with(mimeType));
			will(returnValue(new Size(160, 240, mimeType)));
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item = retriever.getAttachmentItem(attachment, true);
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
		Attachment attachment = getAttachment(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageSizeCalculator).getSize(with(any(InputStream.class)),
					with(mimeType));
			will(returnValue(new Size(1728, 2592, mimeType)));
			oneOf(imageHelper).getExtensionFromMimeType(mimeType);
			will(returnValue("jpg"));
		}});

		AttachmentItem item = retriever.getAttachmentItem(attachment, true);
		assertEquals(1728, item.getWidth());
		assertEquals(2592, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxHeight, item.getThumbnailHeight());
		assertFalse(item.hasError());
	}

	@Test
	public void testMalformedError() {
		String mimeType = "image/jpeg";
		Attachment attachment = getAttachment(mimeType);

		context.checking(new Expectations() {{
			oneOf(imageSizeCalculator).getSize(with(any(InputStream.class)),
					with(mimeType));
			will(returnValue(new Size()));
			oneOf(imageHelper).getExtensionFromMimeType("");
			will(returnValue(null));
		}});

		AttachmentItem item = retriever.getAttachmentItem(attachment, true);
		assertTrue(item.hasError());
	}

	private Attachment getAttachment(String contentType) {
		AttachmentHeader header = new AttachmentHeader(msgId, contentType);
		InputStream in = new ByteArrayInputStream(getRandomBytes(42));
		return new Attachment(header, in);
	}
}
