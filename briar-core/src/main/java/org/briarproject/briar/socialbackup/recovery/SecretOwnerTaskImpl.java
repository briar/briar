package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

import javax.inject.Inject;

public class SecretOwnerTaskImpl implements SecretOwnerTask {

	private final CryptoComponent crypto;
	private final Executor ioExecutor;
	private final KeyPair localKeyPair;
	private boolean cancelled = false;

	@Inject
	SecretOwnerTaskImpl(CryptoComponent crypto,
		@IoExecutor Executor ioExecutor) {
		this.crypto = crypto;
		this.ioExecutor = ioExecutor;
		localKeyPair = crypto.generateAgreementKeyPair();
	}

	@Override
	public void start(Observer observer) {
		// TODO use the actual ip address on local network
		InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("192.168.1.1", 1234);
		observer.onStateChanged(new State.Listening(localKeyPair.getPublic(), socketAddress));
	}

	@Override
	public void cancel() {
        cancelled = true;
	}
}
