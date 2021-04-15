package org.briarproject.briar.api.socialbackup.recovery;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface CustodianTask {

	void start(Observer observer, byte[] payload);

	void cancel();

	void qrCodeDecoded(byte[] qrCodePayload);

	interface Observer {
		void onStateChanged(State state);
	}

	class State {

		public static class Connecting extends State {
		}

		public static class SendingShard extends State {
		}

		public static class ReceivingAck extends State {
		}

		public static class Success extends State {
		}

		public static class Failure extends State {

			public enum Reason {
				QR_CODE_INVALID,
				QR_CODE_TOO_OLD,
				QR_CODE_TOO_NEW,
				NO_CONNECTION,
				OTHER
			}

			private final Reason reason;

			public Failure(Reason reason) {
				this.reason = reason;
			}

			public Reason getReason() {
				return reason;
			}
		}
	}
}
