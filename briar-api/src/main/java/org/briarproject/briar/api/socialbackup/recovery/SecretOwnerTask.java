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

		static class Listening extends State {

			private final PublicKey publicKey;
			private final InetSocketAddress socketAddress;

			public Listening(PublicKey publicKey,
					InetSocketAddress socketAddress) {
				this.publicKey = publicKey;
				this.socketAddress = socketAddress;
			}

			public PublicKey getPublicKey() {
				return publicKey;
			}

			public InetSocketAddress getSocketAddress() {
				return socketAddress;
			}
		}

		static class ReceivingShard extends State {
		}

		static class SendingAck extends State {
		}

		static class Success extends State {
		}

		static class Failure extends State {
		}
	}
}
