package org.briarproject.briar.api.socialbackup.recovery;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.net.InetSocketAddress;

@NotNullByDefault
public interface SecretOwnerTask {

	void start(Observer observer);

	void cancel();

	interface Observer {
		void onStateChanged(State state);
	}

	class State {

		public static class Listening extends State {

			private final byte[] localPayload;

			public Listening(byte[] localPayload) {
				this.localPayload = localPayload;
			}

			public byte[] getLocalPayload() {
				return localPayload;
			}
		}

		public static class ReceivingShard extends State {
		}

		public static class SendingAck extends State {
		}

		public static class Success extends State {
		}

		public static class Failure extends State {
		}
	}
}
