package org.briarproject.briar.attachment;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.api.attachment.Attachment;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static java.lang.System.arraycopy;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.briar.api.attachment.MediaConstants.MSG_KEY_CONTENT_TYPE;
import static org.briarproject.briar.api.attachment.MediaConstants.MSG_KEY_DESCRIPTOR_LENGTH;
import static org.junit.Assert.assertArrayEquals;

public class AttachmentReaderImplTest extends BrambleMockTestCase {

	private final ClientHelper clientHelper = context.mock(ClientHelper.class);

	private final GroupId groupId = new GroupId(getRandomId());
	private final Message message = getMessage(groupId, 1234);
	private final String contentType = "image/jpeg";
	private final AttachmentHeader header = new AttachmentHeader(groupId,
			message.getId(), contentType);

	private final AttachmentReaderImpl attachmentReader =
			new AttachmentReaderImpl(clientHelper);

	@Test(expected = NoSuchMessageException.class)
	public void testWrongGroup() throws Exception {
		GroupId wrongGroupId = new GroupId(getRandomId());
		AttachmentHeader wrongGroup = new AttachmentHeader(wrongGroupId,
				message.getId(), contentType);

		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessage(message.getId());
			will(returnValue(message));
		}});

		attachmentReader.getAttachment(wrongGroup);
	}

	@Test(expected = NoSuchMessageException.class)
	public void testMissingContentType() throws Exception {
		BdfDictionary meta = new BdfDictionary();

		testInvalidMetadata(meta);
	}

	@Test(expected = NoSuchMessageException.class)
	public void testWrongContentType() throws Exception {
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_CONTENT_TYPE, "image/png"));

		testInvalidMetadata(meta);
	}

	@Test(expected = NoSuchMessageException.class)
	public void testMissingDescriptorLength() throws Exception {
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_CONTENT_TYPE, contentType));

		testInvalidMetadata(meta);
	}

	private void testInvalidMetadata(BdfDictionary meta) throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessage(message.getId());
			will(returnValue(message));
			oneOf(clientHelper).getMessageMetadataAsDictionary(message.getId());
			will(returnValue(meta));
		}});

		attachmentReader.getAttachment(header);
	}

	@Test
	public void testSkipsDescriptor() throws Exception {
		int descriptorLength = 123;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_CONTENT_TYPE, contentType),
				new BdfEntry(MSG_KEY_DESCRIPTOR_LENGTH, descriptorLength));

		byte[] body = message.getBody();
		byte[] expectedData = new byte[body.length - descriptorLength];
		arraycopy(body, descriptorLength, expectedData, 0, expectedData.length);

		context.checking(new Expectations() {{
			oneOf(clientHelper).getMessage(message.getId());
			will(returnValue(message));
			oneOf(clientHelper).getMessageMetadataAsDictionary(message.getId());
			will(returnValue(meta));
		}});

		Attachment attachment = attachmentReader.getAttachment(header);
		InputStream in = attachment.getStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		copyAndClose(in, out);
		byte[] data = out.toByteArray();

		assertArrayEquals(expectedData, data);
	}
}
