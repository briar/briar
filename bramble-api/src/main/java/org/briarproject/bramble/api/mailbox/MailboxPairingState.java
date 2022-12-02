package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType;

public abstract class MailboxPairingState {

	public abstract static class Pending extends MailboxPairingState {

		public final long timeStarted;

		private Pending(long timeStarted) {
			this.timeStarted = timeStarted;
		}
	}

	public static class QrCodeReceived extends Pending {

		public QrCodeReceived(long timeStarted) {
			super(timeStarted);
		}
	}

	public static class Pairing extends Pending {

		public Pairing(long timeStarted) {
			super(timeStarted);
		}
	}

	public static class Paired extends MailboxPairingState {
	}

	public static class InvalidQrCode extends MailboxPairingState {

		public final QrCodeType qrCodeType;
		public final int formatVersion;

		public InvalidQrCode(QrCodeType qrCodeType, int formatVersion) {
			this.qrCodeType = qrCodeType;
			this.formatVersion = formatVersion;
		}
	}

	public static class MailboxAlreadyPaired extends MailboxPairingState {
	}

	public static class ConnectionError extends MailboxPairingState {
	}

	public static class UnexpectedError extends MailboxPairingState {
	}
}
