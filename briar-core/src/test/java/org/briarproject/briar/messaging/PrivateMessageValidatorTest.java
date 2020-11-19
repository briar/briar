package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageContext;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.InputStream;

import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.MAX_AUTO_DELETE_TIMER_MS;
import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.attachment.MediaConstants.MAX_CONTENT_TYPE_BYTES;
import static org.briarproject.briar.api.attachment.MediaConstants.MSG_KEY_CONTENT_TYPE;
import static org.briarproject.briar.api.attachment.MediaConstants.MSG_KEY_DESCRIPTOR_LENGTH;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_ATTACHMENTS_PER_MESSAGE;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_INCOMING_TEXT_LENGTH;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.briarproject.briar.messaging.MessageTypes.ATTACHMENT;
import static org.briarproject.briar.messaging.MessageTypes.PRIVATE_MESSAGE;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_ATTACHMENT_HEADERS;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_AUTO_DELETE_TIMER;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_HAS_TEXT;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_MSG_TYPE;
import static org.briarproject.briar.messaging.MessagingConstants.MSG_KEY_TIMESTAMP;
import static org.junit.Assert.assertEquals;

public class PrivateMessageValidatorTest extends BrambleMockTestCase {

	private final BdfReaderFactory bdfReaderFactory =
			context.mock(BdfReaderFactory.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final Clock clock = context.mock(Clock.class);
	private final BdfReader reader = context.mock(BdfReader.class);

	private final Group group = getGroup(getClientId(), 123);
	private final Message message = getMessage(group.getId());
	private final long now = message.getTimestamp() + 1000;
	private final String text =
			getRandomString(MAX_PRIVATE_MESSAGE_INCOMING_TEXT_LENGTH);
	private final BdfList attachmentHeader = getAttachmentHeader();
	private final MessageId attachmentId = new MessageId(getRandomId());
	private final String contentType = getRandomString(MAX_CONTENT_TYPE_BYTES);
	private final BdfDictionary legacyMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_TIMESTAMP, message.getTimestamp()),
			new BdfEntry(MSG_KEY_LOCAL, false),
			new BdfEntry(MSG_KEY_READ, false)
	);
	private final BdfDictionary noAttachmentsMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_TIMESTAMP, message.getTimestamp()),
			new BdfEntry(MSG_KEY_LOCAL, false),
			new BdfEntry(MSG_KEY_READ, false),
			new BdfEntry(MSG_KEY_MSG_TYPE, PRIVATE_MESSAGE),
			new BdfEntry(MSG_KEY_HAS_TEXT, true),
			new BdfEntry(MSG_KEY_ATTACHMENT_HEADERS, new BdfList())
	);
	private final BdfDictionary noTextMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_TIMESTAMP, message.getTimestamp()),
			new BdfEntry(MSG_KEY_LOCAL, false),
			new BdfEntry(MSG_KEY_READ, false),
			new BdfEntry(MSG_KEY_MSG_TYPE, PRIVATE_MESSAGE),
			new BdfEntry(MSG_KEY_HAS_TEXT, false),
			new BdfEntry(MSG_KEY_ATTACHMENT_HEADERS,
					BdfList.of(attachmentHeader))
	);
	private final BdfDictionary attachmentMeta = BdfDictionary.of(
			new BdfEntry(MSG_KEY_TIMESTAMP, message.getTimestamp()),
			new BdfEntry(MSG_KEY_LOCAL, false),
			new BdfEntry(MSG_KEY_MSG_TYPE, ATTACHMENT),
			// Descriptor length is zero as the test doesn't read from the
			// counting input stream
			new BdfEntry(MSG_KEY_DESCRIPTOR_LENGTH, 0L),
			new BdfEntry(MSG_KEY_CONTENT_TYPE, contentType)
	);

	private final PrivateMessageValidator validator =
			new PrivateMessageValidator(bdfReaderFactory, metadataEncoder,
					clock);

	@Test(expected = InvalidMessageException.class)
	public void testRejectsFarFutureTimestamp() throws Exception {
		expectCheckTimestamp(message.getTimestamp() - MAX_CLOCK_DIFFERENCE - 1);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortBody() throws Exception {
		expectCheckTimestamp(now);
		expectParseList(new BdfList());

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTrailingDataForLegacyMessage() throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(text));
		expectReadEof(false);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullTextForLegacyMessage() throws Exception {
		testRejectsLegacyMessage(BdfList.of((String) null));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonStringTextForLegacyMessage() throws Exception {
		testRejectsLegacyMessage(BdfList.of(123));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongTextForLegacyMessage() throws Exception {
		String invalidText =
				getRandomString(MAX_PRIVATE_MESSAGE_INCOMING_TEXT_LENGTH + 1);

		testRejectsLegacyMessage(BdfList.of(invalidText));
	}

	@Test
	public void testAcceptsMaxLengthTextForLegacyMessage() throws Exception {
		testAcceptsLegacyMessage(BdfList.of(text));
	}

	@Test
	public void testAcceptsMinLengthTextForLegacyMessage() throws Exception {
		testAcceptsLegacyMessage(BdfList.of(""));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTrailingDataForPrivateMessage() throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(PRIVATE_MESSAGE, text, new BdfList()));
		expectReadEof(false);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortBodyForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongBodyForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(
				BdfList.of(PRIVATE_MESSAGE, text, new BdfList(), 123));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullTextWithoutAttachmentsForPrivateMessage()
			throws Exception {
		testRejectsPrivateMessage(
				BdfList.of(PRIVATE_MESSAGE, null, new BdfList()));
	}

	@Test
	public void testAcceptsNullTextWithAttachmentsForPrivateMessage()
			throws Exception {
		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, null,
				BdfList.of(attachmentHeader)), noTextMeta);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonStringTextForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(
				BdfList.of(PRIVATE_MESSAGE, 123, new BdfList()));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongTextForPrivateMessage() throws Exception {
		String invalidText =
				getRandomString(MAX_PRIVATE_MESSAGE_INCOMING_TEXT_LENGTH + 1);

		testRejectsPrivateMessage(
				BdfList.of(PRIVATE_MESSAGE, invalidText, new BdfList()));
	}

	@Test
	public void testAcceptsMaxLengthTextForPrivateMessage() throws Exception {
		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList()), noAttachmentsMeta);
	}

	@Test
	public void testAcceptsMinLengthTextForPrivateMessage() throws Exception {
		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, "",
				new BdfList()), noAttachmentsMeta);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullAttachmentListForPrivateMessage()
			throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text, null));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonListAttachmentListForPrivateMessage()
			throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text, 123));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongAttachmentListForPrivateMessage()
			throws Exception {
		BdfList invalidList = new BdfList();
		for (int i = 0; i < MAX_ATTACHMENTS_PER_MESSAGE + 1; i++) {
			invalidList.add(getAttachmentHeader());
		}

		testRejectsPrivateMessage(
				BdfList.of(PRIVATE_MESSAGE, text, invalidList));
	}

	@Test
	public void testAcceptsMaxLengthAttachmentListForPrivateMessage()
			throws Exception {
		BdfList attachmentList = new BdfList();
		for (int i = 0; i < MAX_ATTACHMENTS_PER_MESSAGE; i++) {
			attachmentList.add(getAttachmentHeader());
		}
		BdfDictionary maxAttachmentsMeta = new BdfDictionary(noAttachmentsMeta);
		maxAttachmentsMeta.put(MSG_KEY_ATTACHMENT_HEADERS, attachmentList);

		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				attachmentList), maxAttachmentsMeta);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullAttachmentIdForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(null, contentType);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonRawAttachmentIdForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(123, contentType);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortAttachmentIdForPrivateMessage()
			throws Exception {
		BdfList invalidHeader =
				BdfList.of(getRandomBytes(UniqueId.LENGTH - 1), contentType);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongAttachmentIdForPrivateMessage()
			throws Exception {
		BdfList invalidHeader =
				BdfList.of(getRandomBytes(UniqueId.LENGTH + 1), contentType);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}


	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullContentTypeForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(attachmentId, null);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonStringContentTypeForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(attachmentId, 123);

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortContentTypeForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(attachmentId, "");

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongContentTypeForPrivateMessage()
			throws Exception {
		BdfList invalidHeader = BdfList.of(attachmentId,
				getRandomString(MAX_CONTENT_TYPE_BYTES + 1));

		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				BdfList.of(invalidHeader)));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortDescriptorWithoutTrailingDataForAttachment()
			throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(ATTACHMENT));
		// Single-element list is interpreted as a legacy private message, so
		// EOF is expected
		expectReadEof(true);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortDescriptorWithTrailingDataForAttachment()
			throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(ATTACHMENT));
		// Single-element list is interpreted as a legacy private message, so
		// EOF is expected
		expectReadEof(false);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongDescriptorForAttachment() throws Exception {
		testRejectsAttachment(BdfList.of(ATTACHMENT, contentType, 123));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNullContentTypeForAttachment() throws Exception {
		testRejectsAttachment(BdfList.of(ATTACHMENT, null));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonStringContentTypeForAttachment()
			throws Exception {
		testRejectsAttachment(BdfList.of(ATTACHMENT, 123));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortContentTypeForAttachment() throws Exception {
		testRejectsAttachment(BdfList.of(ATTACHMENT, ""));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooLongContentTypeForAttachment() throws Exception {
		String invalidContentType = getRandomString(MAX_CONTENT_TYPE_BYTES + 1);

		testRejectsAttachment(BdfList.of(ATTACHMENT, invalidContentType));
	}

	@Test
	public void testAcceptsValidDescriptorForAttachment() throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(ATTACHMENT, contentType));
		expectEncodeMetadata(attachmentMeta);

		validator.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsUnknownMessageType() throws Exception {
		expectCheckTimestamp(now);
		expectParseList(BdfList.of(ATTACHMENT + 1, contentType));

		validator.validateMessage(message, group);
	}

	@Test
	public void testAcceptsNullTimerForPrivateMessage() throws Exception {
		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), null), noAttachmentsMeta);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsNonLongTimerForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), "foo"));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooSmallTimerForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), MIN_AUTO_DELETE_TIMER_MS - 1));
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooBigTimerForPrivateMessage() throws Exception {
		testRejectsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), MAX_AUTO_DELETE_TIMER_MS + 1));
	}

	@Test
	public void testAcceptsMinTimerForPrivateMessage() throws Exception {
		BdfDictionary minTimerMeta = new BdfDictionary(noAttachmentsMeta);
		minTimerMeta.put(MSG_KEY_AUTO_DELETE_TIMER, MIN_AUTO_DELETE_TIMER_MS);

		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), MIN_AUTO_DELETE_TIMER_MS), minTimerMeta);
	}

	@Test
	public void testAcceptsMaxTimerForPrivateMessage() throws Exception {
		BdfDictionary maxTimerMeta = new BdfDictionary(noAttachmentsMeta);
		maxTimerMeta.put(MSG_KEY_AUTO_DELETE_TIMER, MAX_AUTO_DELETE_TIMER_MS);

		testAcceptsPrivateMessage(BdfList.of(PRIVATE_MESSAGE, text,
				new BdfList(), MAX_AUTO_DELETE_TIMER_MS), maxTimerMeta);
	}

	private void testRejectsLegacyMessage(BdfList body) throws Exception {
		expectCheckTimestamp(now);
		expectParseList(body);
		expectReadEof(true);

		validator.validateMessage(message, group);
	}

	private void testAcceptsLegacyMessage(BdfList body) throws Exception {
		expectCheckTimestamp(now);
		expectParseList(body);
		expectReadEof(true);
		expectEncodeMetadata(legacyMeta);

		MessageContext result = validator.validateMessage(message, group);
		assertEquals(0, result.getDependencies().size());
	}

	private void testRejectsPrivateMessage(BdfList body) throws Exception {
		expectCheckTimestamp(now);
		expectParseList(body);
		expectReadEof(true);

		validator.validateMessage(message, group);
	}

	private void testAcceptsPrivateMessage(BdfList body, BdfDictionary meta)
			throws Exception {
		expectCheckTimestamp(now);
		expectParseList(body);
		expectReadEof(true);
		expectEncodeMetadata(meta);

		MessageContext result = validator.validateMessage(message, group);
		assertEquals(0, result.getDependencies().size());
	}

	private void testRejectsAttachment(BdfList descriptor) throws Exception {
		expectCheckTimestamp(now);
		expectParseList(descriptor);

		validator.validateMessage(message, group);
	}

	private void expectCheckTimestamp(long now) {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
		}});
	}

	private void expectParseList(BdfList body) throws Exception {
		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(with(any(InputStream.class)));
			will(returnValue(reader));
			oneOf(reader).readList();
			will(returnValue(body));
		}});
	}

	private void expectReadEof(boolean eof) throws Exception {
		context.checking(new Expectations() {{
			oneOf(reader).eof();
			will(returnValue(eof));
		}});
	}

	private void expectEncodeMetadata(BdfDictionary meta) throws Exception {
		context.checking(new Expectations() {{
			oneOf(metadataEncoder).encode(meta);
			will(returnValue(new Metadata()));
		}});
	}

	private BdfList getAttachmentHeader() {
		return BdfList.of(new MessageId(getRandomId()),
				getRandomString(MAX_CONTENT_TYPE_BYTES));
	}
}
