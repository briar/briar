package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.inject.Inject;

public class CustodianTaskImpl implements CustodianTask {

	private boolean cancelled = false;
	private Observer observer;
	private ClientHelper clientHelper;
	private InetSocketAddress remoteSocketAddress;
	private Socket socket = new Socket();
	private final CryptoComponent crypto;
	private final AuthenticatedCipher cipher;
	private final KeyPair localKeyPair;
	private final SecureRandom secureRandom;
	private SecretKey sharedSecret;
	private final int TIMEOUT = 120 * 1000;
	private final int NONCE_LENGTH = 24; // TODO get this constant

	@Inject
	CustodianTaskImpl(CryptoComponent crypto, ClientHelper clientHelper,
			AuthenticatedCipher cipher) {
		this.clientHelper = clientHelper;
		this.crypto = crypto;
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
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.NO_CONNECTION));
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

			System.out.println(
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
			OutputStream outputStream = socket.getOutputStream();
			outputStream.write(createPayload());
			observer.onStateChanged(new CustodianTask.State.ReceivingAck());
		} catch (IOException e) {
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			return;
		}
		System.out.println("Connected *****");
		receiveAck();
	}

	private byte[] createPayload() throws FormatException {
		BdfList payloadList = new BdfList();
		payloadList.add(localKeyPair.getPublic().getEncoded());
		byte[] nonce = new byte[NONCE_LENGTH];
		secureRandom.nextBytes(nonce);
		payloadList.add(nonce);
		try {
			payloadList.add(encrypt("crunchy".getBytes(), nonce));
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
		return clientHelper.toByteArray(payloadList);
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
			byte[] ackMessage = new byte[3];
			int read = inputStream.read(ackMessage);
			if (read < 0) throw new IOException("Ack not read");
			System.out.println("ack message: " + new String(ackMessage));
			observer.onStateChanged(new CustodianTask.State.Success());
			socket.close();
		} catch (IOException e) {
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			return;
		}
	}

}
