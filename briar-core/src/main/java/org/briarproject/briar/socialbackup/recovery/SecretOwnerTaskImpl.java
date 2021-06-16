package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.logging.Logger.getLogger;

public class SecretOwnerTaskImpl extends ReturnShardTaskImpl
		implements SecretOwnerTask {

	private boolean closedSocket = false;
	private InetSocketAddress socketAddress;
	private ClientHelper clientHelper;
	private Observer observer;
	private ServerSocket serverSocket;
	private Socket socket;

	private static final Logger LOG =
			getLogger(SecretOwnerTaskImpl.class.getName());

	@Inject
	SecretOwnerTaskImpl(Provider<AuthenticatedCipher> cipherProvider, CryptoComponent crypto,
			ClientHelper clientHelper) {
		super(cipherProvider, crypto);
		this.clientHelper = clientHelper;
	}

	@Override
	public void start(Observer observer, InetAddress inetAddress) {
		this.observer = observer;
		if (inetAddress == null) {
			LOG.warning("Cannot retrieve local IP address, failing.");
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.NO_CONNECTION));
		}
		LOG.info("InetAddress is " + inetAddress);

		// If we have a socket already open, close it and start fresh
		if (serverSocket != null) {
			try {
				LOG.info("Closing existing socket with port " + serverSocket.getLocalPort());
				closedSocket = true;
				serverSocket.close();
			} catch (IOException ignored) {
			    LOG.info("Ignoring error when closing socket");
			}
		}

		// Start listening on socketAddress
		try {
			LOG.info("Binding socket");
			serverSocket = new ServerSocket(0, 1, inetAddress);
//			serverSocket.bind(socketAddress);
			LOG.info("Binding socket done");
		} catch (IOException e) {
			LOG.warning(
					"IO Error when listening on local socket" + e.getMessage());
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.NO_CONNECTION));
			// TODO could try incrementing the port number
			return;
		}

		socketAddress = new InetSocketAddress(inetAddress, serverSocket.getLocalPort());
		LOG.info("Using port: " + serverSocket.getLocalPort());
		try {
			// TODO add version number
			BdfList payloadList = new BdfList();
			payloadList.add(localKeyPair.getPublic().getEncoded());
			payloadList.add(socketAddress.getAddress().getAddress());
			payloadList.add(socketAddress.getPort());
			observer.onStateChanged(
					new State.Listening(clientHelper.toByteArray(payloadList)));
		} catch (FormatException e) {
			LOG.warning("Error encoding QR code");
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.OTHER));
			return;
		}
		LOG.info("Receiving payload");
		receivePayload();
	}

	private void receivePayload() {
		try {
			LOG.info("Waiting for a connection on port:" + serverSocket.getLocalPort());
			socket = serverSocket.accept();
			LOG.info("Client connected");
			observer.onStateChanged(new State.ReceivingShard());

			DataInputStream inputStream =
					new DataInputStream(socket.getInputStream());

			AgreementPublicKey remotePublicKey =
					new AgreementPublicKey(
							read(inputStream, AGREEMENT_PUBLIC_KEY_LENGTH));
			LOG.info("Read remote public key");

			byte[] addressRaw = socketAddress.getAddress().getAddress();
			deriveSharedSecret(remotePublicKey, addressRaw);

			byte[] payloadNonce = read(inputStream, NONCE_LENGTH);
			LOG.info("Read payload nonce");

			byte[] payloadLengthRaw = read(inputStream, 4);
			int payloadLength = ByteBuffer.wrap(payloadLengthRaw).getInt();
			LOG.info("Expected payload length " + payloadLength + " bytes");

			byte[] payloadRaw = read(inputStream, payloadLength);

			byte[] payloadClear = decrypt(payloadRaw, payloadNonce);
			ReturnShardPayload returnShardPayload = ReturnShardPayload
					.fromList(clientHelper.toList(payloadClear));

			LOG.info("Payload decrypted and parsed successfully");

			DataOutputStream outputStream =
					new DataOutputStream(socket.getOutputStream());
			byte[] ackNonce = generateNonce();
			outputStream.write(ackNonce);

			byte[] ackMessage = encrypt("ack".getBytes(), ackNonce);
			outputStream.write(ackMessage);
			LOG.info("Acknowledgement sent");

			serverSocket.close();

			observer.onStateChanged(new State.Success(returnShardPayload));
		} catch (IOException e) {
			LOG.warning("IO Error receiving payload " + e.getMessage());
			// Only change state to fail if we did not intentionally close
			if (closedSocket) {
				closedSocket = false;
				return;
			}
			// TODO reasons
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.NO_CONNECTION));
		} catch (GeneralSecurityException e) {
			LOG.warning("Security Error receiving payload " + e.getMessage());
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.SECURITY));
		}
	}

	@Override
	public void cancel() {
		LOG.info("Cancel called, failing...");
		if (serverSocket != null) {
			try {
				closedSocket = true;
				serverSocket.close();
			} catch (IOException e) {
				observer.onStateChanged(
						new State.Failure(State.Failure.Reason.OTHER));
			}
		}

		if (observer != null) {
			observer.onStateChanged(
					new State.Failure(State.Failure.Reason.OTHER));
		}
	}
}
