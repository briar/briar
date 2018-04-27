package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.forum.ForumFactory;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Collection;

import javax.annotation.Nullable;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class SharingValidatorTest extends ValidatorTestCase {

	final MessageEncoder messageEncoder = context.mock(MessageEncoder.class);
	final ForumFactory forumFactory = context.mock(ForumFactory.class);
	final BlogFactory blogFactory = context.mock(BlogFactory.class);

	final SharingValidator validator = getValidator();

	final MessageId previousMsgId = new MessageId(getRandomId());
	private final BdfDictionary meta = new BdfDictionary();

	abstract SharingValidator getValidator();

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForInvitation() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForInvitation() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(INVITE.getValue(), previousMsgId, descriptor, null,
						123));
	}

	@Test
	public void testAcceptsAccept() throws Exception {
		expectEncodeMetadata(ACCEPT);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(ACCEPT.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsDecline() throws Exception {
		expectEncodeMetadata(DECLINE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(DECLINE.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsLeave() throws Exception {
		expectEncodeMetadata(LEAVE);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(LEAVE.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsAbort() throws Exception {
		expectEncodeMetadata(ABORT);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullMessageType() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(null, groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongMessageType() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of("", groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidMessageType() throws Exception {
		int invalidMessageType = ABORT.getValue() + 1;
		validator.validateMessage(message, group,
				BdfList.of(invalidMessageType, groupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullSessionId() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), null, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonRawSessionId() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSessionId() throws Exception {
		byte[] invalidGroupId = getRandomBytes(UniqueId.LENGTH - 1);
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), invalidGroupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSessionId() throws Exception {
		byte[] invalidGroupId = getRandomBytes(UniqueId.LENGTH + 1);
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), invalidGroupId, previousMsgId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAbort() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAbort() throws Exception {
		validator.validateMessage(message, group,
				BdfList.of(ABORT.getValue(), groupId, previousMsgId, 123));
	}

	void expectEncodeMetadata(MessageType type) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(type, groupId, timestamp,
					false, false, false, false, false);
			will(returnValue(meta));
		}});
	}

	void assertExpectedContext(BdfMessageContext messageContext,
			@Nullable MessageId previousMsgId) {
		Collection<MessageId> dependencies = messageContext.getDependencies();
		if (previousMsgId == null) {
			assertTrue(dependencies.isEmpty());
		} else {
			assertEquals(1, dependencies.size());
			assertTrue(dependencies.contains(previousMsgId));
		}
		assertEquals(meta, messageContext.getDictionary());
	}

}
