package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

import javax.inject.Inject;

public class SecretOwnerTaskImpl implements SecretOwnerTask {

	private final CryptoComponent crypto;
	private final Executor ioExecutor;
	private final KeyPair localKeyPair;
	private boolean cancelled = false;
	private InetSocketAddress socketAddress;
	private ClientHelper clientHelper;

	@Inject
	SecretOwnerTaskImpl(CryptoComponent crypto,
		@IoExecutor Executor ioExecutor, ClientHelper clientHelper) {
		this.crypto = crypto;
		this.ioExecutor = ioExecutor;
		this.clientHelper = clientHelper;
		localKeyPair = crypto.generateAgreementKeyPair();
	}

	@Override
	public void start(Observer observer, InetAddress inetAddress) {
		if (inetAddress == null) observer.onStateChanged(new State.Failure());
		System.out.println("InetAddress is " + inetAddress);
		socketAddress = new InetSocketAddress(inetAddress, 3002);
		// TODO start listening on socketAddress
		try {
			// TODO add version number
			BdfList payloadList = new BdfList();
			payloadList.add(localKeyPair.getPublic().getEncoded());
			payloadList.add(socketAddress.getAddress().getAddress());
			payloadList.add(socketAddress.getPort());
			observer.onStateChanged(
					new State.Listening(clientHelper.toByteArray(payloadList)));
		} catch (Exception e) {
			observer.onStateChanged(new State.Failure());
		}
	}

	@Override
	public void cancel() {
        cancelled = true;
	}
}
