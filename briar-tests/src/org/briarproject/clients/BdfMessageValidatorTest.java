package org.briarproject.clients;

import org.briarproject.ValidatorTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageContext;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class BdfMessageValidatorTest extends ValidatorTestCase {

	private final BdfMessageValidator subclassNotCalled =
			new BdfMessageValidator(clientHelper, metadataEncoder, clock) {
				@Override
				protected BdfMessageContext validateMessage(Message m, Group g,
						BdfList body)
						throws InvalidMessageException, FormatException {
					fail();
					return null;
				}
			};

	private final BdfList body = BdfList.of(123, 456);
	private final BdfDictionary dictionary = new BdfDictionary();
	private final Metadata meta = new Metadata();

	public BdfMessageValidatorTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsFarFutureTimestamp() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp - MAX_CLOCK_DIFFERENCE - 1));
		}});

		subclassNotCalled.validateMessage(message, group);
	}

	@Test
	public void testAcceptsMaxTimestamp() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp - MAX_CLOCK_DIFFERENCE));
			oneOf(clientHelper).toList(raw, MESSAGE_HEADER_LENGTH,
					raw.length - MESSAGE_HEADER_LENGTH);
			will(returnValue(body));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(meta));
		}});

		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) throws InvalidMessageException, FormatException {
				assertSame(message, m);
				assertSame(group, g);
				assertSame(body, b);
				return new BdfMessageContext(dictionary);
			}
		};
		MessageContext messageContext = v.validateMessage(message, group);
		assertEquals(0, messageContext.getDependencies().size());
		assertSame(meta, messageContext.getMetadata());
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsTooShortMessage() throws Exception {
		final byte[] invalidRaw = new byte[MESSAGE_HEADER_LENGTH];
		// Use a mock message so the length of the raw message can be invalid
		final Message invalidMessage = context.mock(Message.class);

		context.checking(new Expectations() {{
			oneOf(invalidMessage).getTimestamp();
			will(returnValue(timestamp));
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(invalidMessage).getRaw();
			will(returnValue(invalidRaw));
		}});

		subclassNotCalled.validateMessage(invalidMessage, group);
	}

	@Test
	public void testAcceptsMinLengthMessage() throws Exception {
		final byte[] shortRaw = new byte[MESSAGE_HEADER_LENGTH + 1];
		final Message shortMessage =
				new Message(messageId, groupId, timestamp, shortRaw);

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).toList(shortRaw, MESSAGE_HEADER_LENGTH,
					shortRaw.length - MESSAGE_HEADER_LENGTH);
			will(returnValue(body));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(meta));
		}});

		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) throws InvalidMessageException, FormatException {
				assertSame(shortMessage, m);
				assertSame(group, g);
				assertSame(body, b);
				return new BdfMessageContext(dictionary);
			}
		};
		MessageContext messageContext = v.validateMessage(shortMessage, group);
		assertEquals(0, messageContext.getDependencies().size());
		assertSame(meta, messageContext.getMetadata());
	}

	@Test(expected = InvalidMessageException.class)
	public void testRejectsInvalidBdfList() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).toList(raw, MESSAGE_HEADER_LENGTH,
					raw.length - MESSAGE_HEADER_LENGTH);
			will(throwException(new FormatException()));
		}});

		subclassNotCalled.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRethrowsFormatExceptionFromSubclass() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).toList(raw, MESSAGE_HEADER_LENGTH,
					raw.length - MESSAGE_HEADER_LENGTH);
			will(returnValue(body));
		}});

		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) throws InvalidMessageException, FormatException {
				throw new FormatException();
			}
		};
		v.validateMessage(message, group);
	}
}
