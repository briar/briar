package org.briarproject.bramble.api.mailbox;

public abstract class MailboxPairingState {

	public static class QrCodeReceived extends MailboxPairingState {
	}

	public static class Pairing extends MailboxPairingState {
	}

	public static class Paired extends MailboxPairingState {
	}

	public static class InvalidQrCode extends MailboxPairingState {
	}

	public static class MailboxAlreadyPaired extends MailboxPairingState {
	}

	public static class ConnectionError extends MailboxPairingState {
	}

	public static class UnexpectedError extends MailboxPairingState {
	}
}
