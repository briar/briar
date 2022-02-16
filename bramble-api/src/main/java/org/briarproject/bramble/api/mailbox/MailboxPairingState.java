package org.briarproject.bramble.api.mailbox;

import javax.annotation.Nullable;

public abstract class MailboxPairingState {

	/**
	 * The QR code payload that was scanned by the user.
	 * This is null if the code should not be re-used anymore in this state.
	 */
	@Nullable
	public final String qrCodePayload;

	MailboxPairingState(@Nullable String qrCodePayload) {
		this.qrCodePayload = qrCodePayload;
	}

	public static class QrCodeReceived extends MailboxPairingState {
		public QrCodeReceived(String qrCodePayload) {
			super(qrCodePayload);
		}
	}

	public static class Pairing extends MailboxPairingState {
		public Pairing(String qrCodePayload) {
			super(qrCodePayload);
		}
	}

	public static class Paired extends MailboxPairingState {
		public Paired() {
			super(null);
		}
	}

	public static class InvalidQrCode extends MailboxPairingState {
		public InvalidQrCode() {
			super(null);
		}
	}

	public static class MailboxAlreadyPaired extends MailboxPairingState {
		public MailboxAlreadyPaired() {
			super(null);
		}
	}

	public static class ConnectionError extends MailboxPairingState {
		public ConnectionError(String qrCodePayload) {
			super(qrCodePayload);
		}
	}

	public static class AssertionError extends MailboxPairingState {
		public AssertionError(String qrCodePayload) {
			super(qrCodePayload);
		}
	}
}
