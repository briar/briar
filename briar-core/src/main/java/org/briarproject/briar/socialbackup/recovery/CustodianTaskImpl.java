package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

public class CustodianTaskImpl implements CustodianTask {

	private boolean cancelled = false;
	private Observer observer;
	private final ClientHelper clientHelper;
	private InetSocketAddress remoteSocketAddress;
	private final Socket socket = new Socket();
	private final CryptoComponent crypto;
	private final AuthenticatedCipher cipher;
	private final KeyPair localKeyPair;
	private final SecureRandom secureRandom;
	private SecretKey sharedSecret;
	private final int TIMEOUT = 120 * 1000;
	private final int NONCE_LENGTH = 24; // TODO get this constant
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;

	private static final Logger LOG =
			getLogger(CustodianTaskImpl.class.getName());

	@Inject
	CustodianTaskImpl(CryptoComponent crypto, ClientHelper clientHelper,
			AuthenticatedCipher cipher, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		this.clientHelper = clientHelper;
		this.crypto = crypto;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.secureRandom = crypto.getSecureRandom();

		this.cipher = cipher;
		localKeyPair = crypto.generateAgreementKeyPair();
	}

	@Override
	public void start(Observer observer) {
		this.observer = observer;
		observer.onStateChanged(new CustodianTask.State.Connecting());
	}

	@Override
	public void cancel() {
		cancelled = true;
		try {
			socket.close();
		} catch (IOException e) {
			// The reason here is OTHER rather than NO_CONNECTION because
			// the socket could fail to close because it is already closed
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.OTHER));
		}
		observer.onStateChanged(
				new CustodianTask.State.Failure(State.Failure.Reason.OTHER));
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
			sharedSecret =
					crypto.deriveSharedSecret("ShardReturn", remotePublicKey,
							localKeyPair, addressRaw);

			LOG.info(
					" Qr code decoded " + remotePublicKey.getEncoded().length +
							" " +
							remoteSocketAddress);
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
			socket.connect(remoteSocketAddress, TIMEOUT);
			LOG.info("Connected to secret owner " + remoteSocketAddress);

			OutputStream outputStream = socket.getOutputStream();

			// TODO insert the actual payload
			byte[] payload = "crunchy".getBytes();

			byte[] payloadNonce = new byte[NONCE_LENGTH];
			secureRandom.nextBytes(payloadNonce);
			byte[] payloadEncrypted = encrypt(payload, payloadNonce);
			outputStream.write(localKeyPair.getPublic().getEncoded());
			outputStream.write(payloadNonce);
			outputStream.write(ByteBuffer.allocate(4).putInt(payloadEncrypted.length)
					.array());
			LOG.info("Written payload header");

			outputStream.write(payloadEncrypted);

//			OutputStream encryptedOutputStream = streamWriterFactory
//					.createContactExchangeStreamWriter(outputStream,
//							sharedSecret).getOutputStream();
//			encryptedOutputStream.write(payload);

			LOG.info("Written payload");

			observer.onStateChanged(new CustodianTask.State.ReceivingAck());
		} catch (IOException e) {
			if (e instanceof SocketTimeoutException) {
				observer.onStateChanged(new CustodianTask.State.Failure(
						State.Failure.Reason.NO_CONNECTION));
				return;
			}
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			return;
//		}
		} catch (GeneralSecurityException e) {
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.OTHER));
			return;
		}
		receiveAck();
	}

	private byte[] encrypt(byte[] message, byte[] nonce)
			throws GeneralSecurityException {
		cipher.init(true, sharedSecret, nonce);
		byte[] cipherText = new byte[message.length + cipher.getMacBytes()];
		cipher.process(message, 0, message.length, cipherText, 0);
		return cipherText;
	}

	private byte[] decrypt(byte[] cipherText, byte[] nonce)
			throws GeneralSecurityException {
		cipher.init(false, sharedSecret, nonce);
		byte[] message = new byte[cipherText.length - cipher.getMacBytes()];
		cipher.process(cipherText, 0, cipherText.length, message, 0);
		return message;
	}

	private void receiveAck() {
		try {
			InputStream inputStream = socket.getInputStream();
//			InputStream inputStream = streamReaderFactory
//					.createContactExchangeStreamReader(socket.getInputStream(),
//							sharedSecret);
		    byte[] ackNonce = read(inputStream, NONCE_LENGTH);
			byte[] ackMessageEncrypted = read(inputStream, 3 + cipher.getMacBytes());
			byte[] ackMessage = decrypt(ackMessageEncrypted, ackNonce);
			String ackMessageString = new String(ackMessage);
			LOG.info("Received ack message: " + new String(ackMessage));
		    if (!ackMessageString.equals("ack")) throw new GeneralSecurityException("Bad ack message");
			observer.onStateChanged(new CustodianTask.State.Success());
			socket.close();
		} catch (IOException e) {
			LOG.warning("IO Error reading ack" + e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
		} catch (GeneralSecurityException e) {
			LOG.warning("Security Error reading ack" + e.getMessage());
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.OTHER));
		}
	}

	private byte[] read(InputStream inputStream, int length)
			throws IOException {
		byte[] output = new byte[length];
		int bytesRead = inputStream.read(output);
		if (bytesRead < 0) throw new IOException("Cannot read from socket");
		return output;
	}
}
