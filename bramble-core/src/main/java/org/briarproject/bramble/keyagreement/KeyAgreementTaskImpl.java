package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementAbortedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFailedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementListeningEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementStartedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementWaitingEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class KeyAgreementTaskImpl extends Thread implements
		KeyAgreementTask, KeyAgreementConnector.Callbacks,
		KeyAgreementProtocol.Callbacks {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementTaskImpl.class.getName());

	private final CryptoComponent crypto;
	private final EventBus eventBus;
	private final PayloadEncoder payloadEncoder;
	private final KeyPair localKeyPair;
	private final KeyAgreementConnector connector;

	private Payload localPayload;
	private Payload remotePayload;

	KeyAgreementTaskImpl(Clock clock, CryptoComponent crypto,
			EventBus eventBus, PayloadEncoder payloadEncoder,
			PluginManager pluginManager, Executor ioExecutor) {
		this.crypto = crypto;
		this.eventBus = eventBus;
		this.payloadEncoder = payloadEncoder;
		localKeyPair = crypto.generateAgreementKeyPair();
		connector = new KeyAgreementConnector(this, clock, crypto,
				pluginManager, ioExecutor);
	}

	@Override
	public synchronized void listen() {
		if (localPayload == null) {
			localPayload = connector.listen(localKeyPair);
			eventBus.broadcast(new KeyAgreementListeningEvent(localPayload));
		}
	}

	@Override
	public synchronized void stopListening() {
		if (localPayload != null) {
			if (remotePayload == null)
				connector.stopListening();
			else
				interrupt();
		}
	}

	@Override
	public synchronized void connectAndRunProtocol(Payload remotePayload) {
		if (this.localPayload == null)
			throw new IllegalStateException(
					"Must listen before connecting");
		if (this.remotePayload != null)
			throw new IllegalStateException(
					"Already provided remote payload for this task");
		this.remotePayload = remotePayload;
		start();
	}

	@Override
	public void run() {
		boolean alice = localPayload.compareTo(remotePayload) < 0;

		// Open connection to remote device
		KeyAgreementTransport transport =
				connector.connect(remotePayload, alice);
		if (transport == null) {
			// Notify caller that the connection failed
			eventBus.broadcast(new KeyAgreementFailedEvent());
			return;
		}

		// Run BQP protocol over the connection
		LOG.info("Starting BQP protocol");
		KeyAgreementProtocol protocol = new KeyAgreementProtocol(this, crypto,
				payloadEncoder, transport, remotePayload, localPayload,
				localKeyPair, alice);
		try {
			SecretKey master = protocol.perform();
			KeyAgreementResult result =
					new KeyAgreementResult(master, transport.getConnection(),
							transport.getTransportId(), alice);
			LOG.info("Finished BQP protocol");
			// Broadcast result to caller
			eventBus.broadcast(new KeyAgreementFinishedEvent(result));
		} catch (AbortException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			// Notify caller that the protocol was aborted
			eventBus.broadcast(new KeyAgreementAbortedEvent(e.receivedAbort));
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			// Notify caller that the connection failed
			eventBus.broadcast(new KeyAgreementFailedEvent());
		}
	}

	@Override
	public void connectionWaiting() {
		eventBus.broadcast(new KeyAgreementWaitingEvent());
	}

	@Override
	public void initialRecordReceived() {
		// We send this here instead of when we create the protocol, so that
		// if device A makes a connection after getting device B's payload and
		// starts its protocol, device A's UI doesn't change to prevent device B
		// from getting device A's payload.
		eventBus.broadcast(new KeyAgreementStartedEvent());
	}
}
