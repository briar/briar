package org.briarproject.bramble.mailbox;


import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface MailboxPairingTaskFactory {

	MailboxPairingTask createPairingTask(String qrCodePayload);

}
