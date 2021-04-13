package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.inject.Inject;

public class CustodianTaskImpl implements CustodianTask {

	private boolean cancelled = false;
	private Observer observer;
	private ClientHelper clientHelper;
	private InetSocketAddress remoteSocketAddress;
	private Socket socket = new Socket();
	private final int TIMEOUT = 120 * 1000;

	@Inject
	CustodianTaskImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
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
			AgreementPublicKey publicKey =
					new AgreementPublicKey(qrCodePayload.getRaw(0));
			byte[] addressRaw = qrCodePayload.getRaw(1);
			int port = qrCodePayload.getLong(2).intValue();
			remoteSocketAddress =
					new InetSocketAddress(InetAddress.getByAddress(addressRaw),
							port);
			System.out.println(
					" Qr code decoded " + publicKey.getEncoded().length + " " +
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
			outputStream.write("crunchy".getBytes());
			observer.onStateChanged(new CustodianTask.State.ReceivingAck());
		} catch (IOException e) {
			observer.onStateChanged(new CustodianTask.State.Failure(
					State.Failure.Reason.QR_CODE_INVALID));
			return;
		}
		System.out.println("Connected *****");
		receiveAck();
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
