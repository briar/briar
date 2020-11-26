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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MAX_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.sharing.MessageType.ABORT;
import static org.briarproject.briar.sharing.MessageType.ACCEPT;
import static org.briarproject.briar.sharing.MessageType.DECLINE;
import static org.briarproject.briar.sharing.MessageType.INVITE;
import static org.briarproject.briar.sharing.MessageType.LEAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
		expectEncodeMetadata(ACCEPT, NO_AUTO_DELETE_TIMER);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(ACCEPT.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsAcceptWithMinAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(ACCEPT,
				MIN_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsAcceptWithMaxAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(ACCEPT,
				MAX_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsAcceptWithNullAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(ACCEPT, null);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAcceptWithTooBigAutoDeleteTimer() throws Exception {
		testRejectsResponseWithAutoDeleteTimer(ACCEPT,
				MAX_AUTO_DELETE_TIMER_MS + 1);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAcceptWithTooSmallAutoDeleteTimer()
			throws Exception {
		testRejectsResponseWithAutoDeleteTimer(ACCEPT,
				MIN_AUTO_DELETE_TIMER_MS - 1);
	}

	@Test
	public void testAcceptsDecline() throws Exception {
		expectEncodeMetadata(DECLINE, NO_AUTO_DELETE_TIMER);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(DECLINE.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsDeclineWithMinAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(DECLINE,
				MIN_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsDeclineWithMaxAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(DECLINE,
				MAX_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsDeclineWithNullAutoDeleteTimer() throws Exception {
		testAcceptsResponseWithAutoDeleteTimer(DECLINE, null);
	}

	@Test(expected = FormatException.class)
	public void testRejectsDeclineWithTooBigAutoDeleteTimer()
			throws Exception {
		testRejectsResponseWithAutoDeleteTimer(DECLINE,
				MAX_AUTO_DELETE_TIMER_MS + 1);
	}

	@Test(expected = FormatException.class)
	public void testRejectsDeclineWithTooSmallAutoDeleteTimer()
			throws Exception {
		testRejectsResponseWithAutoDeleteTimer(DECLINE,
				MIN_AUTO_DELETE_TIMER_MS - 1);
	}

	@Test
	public void testAcceptsLeave() throws Exception {
		expectEncodeMetadata(LEAVE, NO_AUTO_DELETE_TIMER);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(LEAVE.getValue(), groupId, previousMsgId));
		assertExpectedContext(context, previousMsgId);
	}

	@Test
	public void testAcceptsAbort() throws Exception {
		expectEncodeMetadata(ABORT, NO_AUTO_DELETE_TIMER);
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

	void expectEncodeMetadata(MessageType type, long autoDeleteTimer) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(type, groupId, timestamp,
					false, false, false, false, false, autoDeleteTimer);
			will(returnValue(meta));
		}});
	}

	void assertExpectedContext(BdfMessageContext messageContext,
			@Nullable MessageId previousMsgId) {
		Collection<MessageId> dependencies = messageContext.getDependencies();
		if (previousMsgId == null) {
			assertEquals(emptyList(), dependencies);
		} else {
			assertEquals(singletonList(previousMsgId), dependencies);
		}
		assertEquals(meta, messageContext.getDictionary());
	}

	private void testAcceptsResponseWithAutoDeleteTimer(MessageType type,
			@Nullable Long timer) throws Exception {
		expectEncodeMetadata(type,
				timer == null ? NO_AUTO_DELETE_TIMER : timer);
		BdfMessageContext context = validator.validateMessage(message, group,
				BdfList.of(type.getValue(), groupId, previousMsgId, timer));
		assertExpectedContext(context, previousMsgId);
	}

	private void testRejectsResponseWithAutoDeleteTimer(MessageType type,
			long timer) throws FormatException {
		validator.validateMessage(message, group,
				BdfList.of(type.getValue(), groupId, previousMsgId, timer));
		fail();
	}
}
