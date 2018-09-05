package org.briarproject.bramble.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageContext;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import static org.briarproject.bramble.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BdfMessageValidatorTest extends ValidatorTestCase {

	@NotNullByDefault
	private final BdfMessageValidator failIfSubclassIsCalled =
			new BdfMessageValidator(clientHelper, metadataEncoder, clock) {
				@Override
				protected BdfMessageContext validateMessage(Message m, Group g,
						BdfList body) {
					throw new AssertionError();
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

		failIfSubclassIsCalled.validateMessage(message, group);
	}

	@Test
	public void testAcceptsMaxTimestamp() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp - MAX_CLOCK_DIFFERENCE));
			oneOf(clientHelper).toList(message.getBody());
			will(returnValue(body));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(meta));
		}});

		@NotNullByDefault
		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) {
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

	@Test
	public void testAcceptsMinLengthMessage() throws Exception {
		Message shortMessage = getMessage(groupId, 1);

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).toList(shortMessage.getBody());
			will(returnValue(body));
			oneOf(metadataEncoder).encode(dictionary);
			will(returnValue(meta));
		}});

		@NotNullByDefault
		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) {
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
			oneOf(clientHelper).toList(message.getBody());
			will(throwException(new FormatException()));
		}});

		failIfSubclassIsCalled.validateMessage(message, group);
	}

	@Test(expected = InvalidMessageException.class)
	public void testRethrowsFormatExceptionFromSubclass() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(timestamp));
			oneOf(clientHelper).toList(message.getBody());
			will(returnValue(body));
		}});

		@NotNullByDefault
		BdfMessageValidator v = new BdfMessageValidator(clientHelper,
				metadataEncoder, clock) {
			@Override
			protected BdfMessageContext validateMessage(Message m, Group g,
					BdfList b) throws FormatException {
				throw new FormatException();
			}
		};
		v.validateMessage(message, group);
	}
}
