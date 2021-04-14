package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

public class SecretOwnerTaskImpl implements SecretOwnerTask {

	private final CryptoComponent crypto;
	private final Executor ioExecutor;
	private final KeyPair localKeyPair;
	private final AuthenticatedCipher cipher;
	private boolean cancelled = false;
	private InetSocketAddress socketAddress;
	private ClientHelper clientHelper;
	private final int PORT = 3002;
	private Observer observer;
	private ServerSocket serverSocket;
	private Socket socket;
	private SecretKey sharedSecret;
	private final int NONCE_LENGTH = 24;
	private final SecureRandom secureRandom;
	private final StreamReaderFactory streamReaderFactory;
    private final StreamWriterFactory streamWriterFactory;

	private static final Logger LOG =
			getLogger(SecretOwnerTaskImpl.class.getName());

	@Inject
	SecretOwnerTaskImpl(AuthenticatedCipher cipher, CryptoComponent crypto,
			@IoExecutor Executor ioExecutor, ClientHelper clientHelper, StreamReaderFactory streamReaderFactory, StreamWriterFactory streamWriterFactory) {
		this.crypto = crypto;
		secureRandom = crypto.getSecureRandom();
		this.cipher = cipher;
		this.ioExecutor = ioExecutor;
		this.clientHelper = clientHelper;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		localKeyPair = crypto.generateAgreementKeyPair();
	}

	@Override
	public void start(Observer observer, InetAddress inetAddress) {
		this.observer = observer;
		if (inetAddress == null) {
			LOG.warning("Cannot retrieve local IP address, failing.");
			observer.onStateChanged(new State.Failure());
		}
		LOG.info("InetAddress is " + inetAddress);
		socketAddress = new InetSocketAddress(inetAddress, PORT);

		// start listening on socketAddress
		try {
			serverSocket = new ServerSocket();
			serverSocket.bind(socketAddress);
		} catch (IOException e) {
			LOG.warning("IO Error when listening on local socket" + e.getMessage());
			observer.onStateChanged(new State.Failure());
			// TODO could try incrementing the port number
			return;
		}

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
			observer.onStateChanged(new State.Failure());
			return;
		}
		receivePayload();
	}

	private void receivePayload() {
		try {
			socket = serverSocket.accept();
			LOG.info("Client connected");
			observer.onStateChanged(new State.ReceivingShard());

			InputStream inputStream = socket.getInputStream();

			AgreementPublicKey remotePublicKey = new AgreementPublicKey(read(inputStream, 32));
			LOG.info("Read remote public key");
			deriveSharedSecret(remotePublicKey);

			byte[] payloadNonce = read(inputStream, NONCE_LENGTH);
			LOG.info("Read payload nonce");

			byte[] payloadLengthRaw = read(inputStream, 4);
			int payloadLength = ByteBuffer.wrap(payloadLengthRaw).getInt();
            LOG.info("Expected payload length " + payloadLength + " bytes");

		    byte[] payloadRaw = read(inputStream, payloadLength);

//		    InputStream clearInputStream = streamReaderFactory.createContactExchangeStreamReader(inputStream, sharedSecret);

//		    byte[] payloadClear = read(clearInputStream, payloadLength);
		    byte[] payloadClear = decrypt(payloadRaw, payloadNonce);

		    LOG.info("Payload decrypted: " + new String(payloadClear));

//			StreamWriter streamWriter = streamWriterFactory.createContactExchangeStreamWriter(socket.getOutputStream(), sharedSecret);
//			OutputStream outputStream = streamWriter.getOutputStream();

			OutputStream outputStream = socket.getOutputStream();
			byte[] ackNonce = new byte[NONCE_LENGTH];
			secureRandom.nextBytes(ackNonce);
            outputStream.write(ackNonce);

			byte[] ackMessage = encrypt("ack".getBytes(), ackNonce);
			outputStream.write(ackMessage);
            LOG.info("Acknowledgement sent");

			serverSocket.close();

			observer.onStateChanged(new State.Success());
		} catch (IOException e) {
			LOG.warning("IO Error receiving payload" + e.getMessage());
			// TODO reasons
			observer.onStateChanged(new State.Failure());
		} catch (GeneralSecurityException e) {
			LOG.warning("Security Error receiving payload" + e.getMessage());
			observer.onStateChanged(new State.Failure());
		}
	}

	private byte[] read (InputStream inputStream, int length) throws IOException {
		byte[] output = new byte[length];
		int read = inputStream.read(output);
		if (read < 0) throw new IOException("Cannot read from socket");
		return output;
	}

	private void deriveSharedSecret(AgreementPublicKey remotePublicKey) throws
			GeneralSecurityException {
		byte[] addressRaw = socketAddress.getAddress().getAddress();
		sharedSecret =
				crypto.deriveSharedSecret("ShardReturn", remotePublicKey,
						localKeyPair, addressRaw);
	}

	@Override
	public void cancel() {
		cancelled = true;
		LOG.info("Cancel called, failing...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			observer.onStateChanged(new State.Failure());
		}
		observer.onStateChanged(new State.Failure());
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
}
