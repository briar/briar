package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PublicKey;

import javax.inject.Inject;

public class CustodianTaskImpl implements CustodianTask {

	private boolean cancelled = false;
	private Observer observer;
	private ClientHelper clientHelper;
	private InetSocketAddress remoteSocketAddress;

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
	}

	@Override
	public void qrCodeDecoded(byte[] qrCodePayloadRaw) {
	   try {
		   BdfList qrCodePayload = clientHelper.toList(qrCodePayloadRaw);
		   AgreementPublicKey publicKey = new AgreementPublicKey(qrCodePayload.getRaw(0));
		   byte[] addressRaw = qrCodePayload.getRaw(1);
		   int port = qrCodePayload.getLong(2).intValue();
		   remoteSocketAddress = new InetSocketAddress(InetAddress.getByAddress(addressRaw), port);
		   System.out.println(" Qr code decoded " + publicKey.getEncoded().length + " " + remoteSocketAddress);
		   observer.onStateChanged(new CustodianTask.State.SendingShard());
	   } catch (Exception e) {
	   	   observer.onStateChanged(new CustodianTask.State.Failure(State.Failure.Reason.QR_CODE_INVALID));
	   	   return;
	   }

	   Socket s = new Socket();
	   try {
		   s.connect(remoteSocketAddress, 120 * 1000);
	   } catch (IOException e) {
		   observer.onStateChanged(new CustodianTask.State.Failure(State.Failure.Reason.QR_CODE_INVALID));
	   }
	   System.out.println("Connected *****");
	}
}
