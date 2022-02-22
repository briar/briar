package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxStatus;

class MailboxState {

	static class NotSetup extends MailboxState {
	}

	static class ShowDownload extends MailboxState {
	}

	static class ScanningQrCode extends MailboxState {
	}

	static class Pairing extends MailboxState {
		final MailboxPairingState pairingState;

		Pairing(MailboxPairingState pairingState) {
			this.pairingState = pairingState;
		}
	}

	static class OfflineWhenPairing extends MailboxState {
	}

	static class CameraError extends MailboxState {
	}

	static class IsPaired extends MailboxState {
		final MailboxStatus mailboxStatus;

		IsPaired(MailboxStatus mailboxStatus) {
			this.mailboxStatus = mailboxStatus;
		}
	}

}
