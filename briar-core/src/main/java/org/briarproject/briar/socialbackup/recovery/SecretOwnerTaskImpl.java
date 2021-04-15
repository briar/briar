package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
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
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

public class SecretOwnerTaskImpl extends ReturnShardTaskImpl
		implements SecretOwnerTask {

	//	private final Executor ioExecutor;
	private boolean cancelled = false;
	private InetSocketAddress socketAddress;
	private ClientHelper clientHelper;
	private Observer observer;
	private ServerSocket serverSocket;
	private Socket socket;
//	private final StreamReaderFactory streamReaderFactory;
//  private final StreamWriterFactory streamWriterFactory;

	private static final Logger LOG =
			getLogger(SecretOwnerTaskImpl.class.getName());

	@Inject
	SecretOwnerTaskImpl(AuthenticatedCipher cipher, CryptoComponent crypto,
			@IoExecutor Executor ioExecutor, ClientHelper clientHelper) {
		super(cipher, crypto);
//		this.ioExecutor = ioExecutor;
		this.clientHelper = clientHelper;
//		this.streamReaderFactory = streamReaderFactory;
//		this.streamWriterFactory = streamWriterFactory;
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

		// Start listening on socketAddress
		try {
			serverSocket = new ServerSocket();
			serverSocket.bind(socketAddress);
		} catch (IOException e) {
			LOG.warning(
					"IO Error when listening on local socket" + e.getMessage());
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

//		    InputStream clearInputStream = streamReaderFactory.createContactExchangeStreamReader(inputStream, sharedSecret);

//		    byte[] payloadClear = read(clearInputStream, payloadLength);
			byte[] payloadClear = decrypt(payloadRaw, payloadNonce);

			LOG.info("Payload decrypted: " + new String(payloadClear));

//			StreamWriter streamWriter = streamWriterFactory.createContactExchangeStreamWriter(socket.getOutputStream(), sharedSecret);
//			OutputStream outputStream = streamWriter.getOutputStream();

			OutputStream outputStream = socket.getOutputStream();
			byte[] ackNonce = generateNonce();
			outputStream.write(ackNonce);

			byte[] ackMessage = encrypt("ack".getBytes(), ackNonce);
			outputStream.write(ackMessage);
			LOG.info("Acknowledgement sent");

			serverSocket.close();

			observer.onStateChanged(new State.Success());
		} catch (IOException e) {
			LOG.warning("IO Error receiving payload " + e.getMessage());
			// TODO reasons
			observer.onStateChanged(new State.Failure());
		} catch (GeneralSecurityException e) {
			LOG.warning("Security Error receiving payload " + e.getMessage());
			observer.onStateChanged(new State.Failure());
		}
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
}
