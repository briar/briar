package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;

import javax.inject.Inject;

public class CustodianTaskImpl implements CustodianTask {

	private boolean cancelled = false;
	private Observer observer;
	private ClientHelper clientHelper;

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
		   byte[] publicKeyRaw = qrCodePayload.getRaw(0);
		   byte[] addressRaw = qrCodePayload.getRaw(1);
		   Long port = qrCodePayload.getLong(2);
		   System.out.println(" Qr code decoded " + publicKeyRaw.length + " " + addressRaw.length + " "+ port);
		   observer.onStateChanged(new CustodianTask.State.SendingShard());
	   } catch (FormatException e) {
	   	   observer.onStateChanged(new CustodianTask.State.Failure(State.Failure.Reason.QR_CODE_INVALID));
	   }
	}
}
