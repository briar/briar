package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.logging.Logger.getLogger;

public class CustodianTaskImpl extends ReturnShardTaskImpl
		implements CustodianTask {

	private Observer observer;
	private final ClientHelper clientHelper;
	private InetSocketAddress remoteSocketAddress;
	private Socket socket;
	private byte[] payload;

	private static final Logger LOG =
			getLogger(CustodianTaskImpl.class.getName());

	@Inject
	CustodianTaskImpl(CryptoComponent crypto, ClientHelper clientHelper,
			Provider<AuthenticatedCipher> cipherProvider) {
		super(cipherProvider, crypto);
		this.clientHelper = clientHelper;

	}

	@Override
	public void start(Observer observer, byte[] payload) {
		this.observer = observer;
		this.payload = payload;
		observer.onStateChanged(new CustodianTask.State.Connecting());
	}

	@Override
	public void cancel() {
		if (socket != null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				observer.onStateChanged(new CustodianTask.State.Failure(
						State.Failure.Reason.OTHER));
			}
		}
		if (observer != null) {
			observer.onStateChanged(
					new CustodianTask.State.Failure(State.Failure.Reason.OTHER));
		}
	}

	@Override
	public void qrCodeDecoded(byte[] qrCodePayloadRaw) {
		try {
			BdfList qrCodePayload = clientHelper.toList(qrCodePayloadRaw);
			AgreementPublicKey remotePublicKey =
					new AgreementPublicKey(qrCodePayload.getRaw(0));
			byte[] addressRaw = qrCodePayload.getRaw(1);
			int port = qrCodePayload.getLong(2).intValue();
			remoteSocketAddress =
					new InetSocketAddress(InetAddress.getByAddress(addressRaw),
							port);
			deriveSharedSecret(remotePublicKey, addressRaw);

			LOG.info("Qr code payload parsed successfully");
		} catch (Exception e) {
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			return;
		}
		connectAndSendShard();
	}

	private void connectAndSendShard() {
		observer.onStateChanged(new CustodianTask.State.SendingShard());
		try {
			LOG.info("Connecting to secret owner " + remoteSocketAddress);
			socket = new Socket();
			socket.connect(remoteSocketAddress, TIMEOUT);
			LOG.info("Connected to secret owner " + remoteSocketAddress);

			OutputStream outputStream = socket.getOutputStream();

			byte[] payloadNonce = generateNonce();

			byte[] payloadEncrypted = encrypt(payload, payloadNonce);
			outputStream.write(localKeyPair.getPublic().getEncoded());
			outputStream.write(payloadNonce);
			outputStream.write(ByteBuffer.allocate(4)
					.putInt(payloadEncrypted.length)
					.array());
			LOG.info("Written payload header");

			outputStream.write(payloadEncrypted);

			LOG.info("Written payload");

			observer.onStateChanged(new CustodianTask.State.ReceivingAck());
		} catch (IOException e) {
			if (e instanceof SocketTimeoutException) {
				LOG.warning("Timed out connecting to secret owner");
				observer.onStateChanged(new CustodianTask.State.Failure(
						State.Failure.Reason.NO_CONNECTION));
				return;
			}
			LOG.warning("IO Error connecting to secret owner " + e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			closeSocket();
			return;
		} catch (GeneralSecurityException e) {
			LOG.warning("Security error "+ e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.OTHER));
			closeSocket();
			return;
		}
		receiveAck();
	}

	private void receiveAck() {
		try {
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			byte[] ackNonce = read(inputStream, NONCE_LENGTH);
			byte[] ackMessageEncrypted =
					read(inputStream, 3 + AUTH_TAG_BYTES);
			byte[] ackMessage = decrypt(ackMessageEncrypted, ackNonce);
			String ackMessageString = new String(ackMessage);
			LOG.info("Received ack message: " + new String(ackMessage));
			if (!ackMessageString.equals("ack"))
				throw new GeneralSecurityException("Bad ack message");
			observer.onStateChanged(new CustodianTask.State.Success());
			socket.close();
		} catch (IOException e) {
			LOG.warning("IO Error reading ack " + e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
		} catch (GeneralSecurityException e) {
			LOG.warning("Security Error reading ack " + e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.OTHER));
		}
	}

	private void closeSocket() {
		if (socket.isClosed()) return;
		try {
		   socket.close();
		} catch (IOException ignored) {}
	}
}
