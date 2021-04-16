package org.briarproject.briar.api.socialbackup.recovery;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@NotNullByDefault
public interface SecretOwnerTask {

	void start(Observer observer, InetAddress inetAddress);

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
			private final ReturnShardPayload remotePayload;

			public Success(ReturnShardPayload remotePayload) { this.remotePayload = remotePayload; }

			public ReturnShardPayload getRemotePayload() { return remotePayload; }
		}

		public static class Failure extends State {

			public enum Reason {
				CANCELLED,
				SECURITY,
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
