package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxStatus;

class MailboxState {

	static class NotSetup extends MailboxState {
	}

	static class SettingUp extends MailboxState {
	}

	static class QrCodeWrong extends MailboxState {
	}

	static class OfflineInSetup extends MailboxState {
		final MailboxProperties mailboxProperties;

		OfflineInSetup(MailboxProperties mailboxProperties) {
			this.mailboxProperties = mailboxProperties;
		}
	}

	static class IsSetup extends MailboxState {
		final MailboxStatus mailboxStatus;

		IsSetup(MailboxStatus mailboxStatus) {
			this.mailboxStatus = mailboxStatus;
		}
	}

}
