package org.briarproject.briar.android.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxStatus;

import androidx.annotation.Nullable;

class MailboxState {

	static class NotSetup extends MailboxState {
	}

	static class ScanningQrCode extends MailboxState {
	}

	static class Pairing extends MailboxState {
		final MailboxPairingState pairingState;

		Pairing(MailboxPairingState pairingState) {
			this.pairingState = pairingState;
		}

		@Nullable
		String getQrCodePayload() {
			return pairingState.qrCodePayload;
		}
	}

	static class OfflineWhenPairing extends MailboxState {
		@Nullable
		final String qrCodePayload;

		OfflineWhenPairing(@Nullable String qrCodePayload) {
			this.qrCodePayload = qrCodePayload;
		}

		OfflineWhenPairing() {
			this(null);
		}
	}

	static class IsPaired extends MailboxState {
		final MailboxStatus mailboxStatus;

		IsPaired(MailboxStatus mailboxStatus) {
			this.mailboxStatus = mailboxStatus;
		}
	}

}
