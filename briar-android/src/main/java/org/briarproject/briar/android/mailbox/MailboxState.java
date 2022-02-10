package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxStatus;

import androidx.annotation.Nullable;

class MailboxState {

	static class NotSetup extends MailboxState {
	}

	static class ScanningQrCode extends MailboxState {
	}

	static class SettingUp extends MailboxState {
	}

	static class QrCodeWrong extends MailboxState {
	}

	static class OfflineInSetup extends MailboxState {
		@Nullable
		final MailboxProperties mailboxProperties;

		OfflineInSetup(@Nullable MailboxProperties mailboxProperties) {
			this.mailboxProperties = mailboxProperties;
		}

		OfflineInSetup() {
			this(null);
		}
	}

	static class IsSetup extends MailboxState {
		final MailboxStatus mailboxStatus;

		IsSetup(MailboxStatus mailboxStatus) {
			this.mailboxStatus = mailboxStatus;
		}
	}

}
