package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.retryUntilSuccessOrTimeout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MailboxIntegrationTest extends AbstractMailboxIntegrationTest {

	private static final Logger LOG =
			getLogger(MailboxIntegrationTest.class.getSimpleName());

	@Test
	public void testSendMessageViaMailbox() throws Exception {
		addContacts();

		// c1 one pairs the mailbox
		MailboxProperties props1 = pair(c1, mailbox);

		// Check for number of contacts on mailbox via API every 100ms.
		// This should be quick and will succeed with first call.
		retryUntilSuccessOrTimeout(1_000, 100, () -> {
			Collection<ContactId> contacts = api.getContacts(props1);
			return contacts.size() == 1;
		});

		// tell contact about mailbox
		sync1To2(1, true);
		ack2To1(1);

		// contact should have received their MailboxProperties
		MailboxProperties props2 =
				getMailboxProperties(c2, contact1From2.getId());
		assertNotNull(props2.getInboxId());

		// wait until file containing mailbox properties arrived on mailbox
		retryUntilSuccessOrTimeout(5_000, 500, () -> {
			List<MailboxFile> files = api.getFiles(props2, props2.getInboxId());
			return files.size() == 1;
		});
		LOG.info("Mailbox properties uploaded");

		// send message and wait for it to arrive via mailbox
		broadcastMessage(c1);

		// we don't check for two messages now, because 2 (Bob) might have
		// download the first message still in their 1st download cycle.

		// wait for message to arrive
		// this might require 2nd download cycle after Tor reachability period
		LOG.info("Waiting for delivery of broadcast message");
		awaitPendingMessageDelivery(1);

		// assert that message arrived for c2
		assertNumMessages(c2, contact1From2.getId(), 1);

		// all files were deleted from mailbox
		assertEquals(0, api.getFiles(props2, props2.getInboxId()).size());
	}

}
