package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxPairingState;

class MailboxState {

	static class NotSetup extends MailboxState {
		final boolean tellUserToWipeMailbox;

		NotSetup() {
			this(false);
		}

		NotSetup(boolean tellUserToWipeMailbox) {
			this.tellUserToWipeMailbox = tellUserToWipeMailbox;
		}
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
		final boolean isOnline;

		IsPaired(boolean isOnline) {
			this.isOnline = isOnline;
		}
	}

}
