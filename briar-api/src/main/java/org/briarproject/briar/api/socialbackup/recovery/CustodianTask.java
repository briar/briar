package org.briarproject.briar.api.socialbackup.recovery;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface CustodianTask {

	void start(Observer observer);

	void cancel();

	interface Observer {
		void onStateChanged(State state);
	}

	class State {

		static class Connecting extends State {
		}

		static class SendingShard extends State {
		}

		static class ReceivingAck extends State {
		}

		static class Success extends State {
		}

		static class Failure extends State {

			enum Reason {
				QR_CODE_INVALID,
				QR_CODE_TOO_OLD,
				QR_CODE_TOO_NEW,
				NO_CONNECTION,
				OTHER
			}

			private final Reason reason;

			Failure(Reason reason) {
				this.reason = reason;
			}

			public Reason getReason() {
				return reason;
			}
		}
	}
}
