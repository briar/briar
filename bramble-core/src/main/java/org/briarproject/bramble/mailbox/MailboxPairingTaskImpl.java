package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.ConnectionError;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.InvalidQrCode;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.MailboxAlreadyPaired;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.Paired;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.Pairing;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.QrCodeReceived;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.UnexpectedError;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxAlreadyPairedException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.QR_FORMAT_VERSION;
import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.MAILBOX;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.ISO_8859_1;

@ThreadSafe
@NotNullByDefault
class MailboxPairingTaskImpl implements MailboxPairingTask {

	private final static Logger LOG =
			getLogger(MailboxPairingTaskImpl.class.getName());

	private final String payload;
	private final Executor eventExecutor;
	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final Clock clock;
	private final MailboxApi api;
	private final MailboxSettingsManager mailboxSettingsManager;
	private final MailboxUpdateManager mailboxUpdateManager;
	private final QrCodeClassifier qrCodeClassifier;
	private final long timeStarted;

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final List<Consumer<MailboxPairingState>> observers =
			new ArrayList<>();
	@GuardedBy("lock")
	private MailboxPairingState state;

	MailboxPairingTaskImpl(
			String payload,
			@EventExecutor Executor eventExecutor,
			DatabaseComponent db,
			CryptoComponent crypto,
			Clock clock,
			MailboxApi api,
			MailboxSettingsManager mailboxSettingsManager,
			MailboxUpdateManager mailboxUpdateManager,
			QrCodeClassifier qrCodeClassifier) {
		this.payload = payload;
		this.eventExecutor = eventExecutor;
		this.db = db;
		this.crypto = crypto;
		this.clock = clock;
		this.api = api;
		this.mailboxSettingsManager = mailboxSettingsManager;
		this.mailboxUpdateManager = mailboxUpdateManager;
		this.qrCodeClassifier = qrCodeClassifier;
		timeStarted = clock.currentTimeMillis();
		state = new QrCodeReceived(timeStarted);
	}

	@Override
	public void addObserver(Consumer<MailboxPairingState> o) {
		MailboxPairingState state;
		synchronized (lock) {
			observers.add(o);
			state = this.state;
			eventExecutor.execute(() -> o.accept(state));
		}
	}

	@Override
	public void removeObserver(Consumer<MailboxPairingState> o) {
		synchronized (lock) {
			observers.remove(o);
		}
	}

	@Override
	public void run() {
		Pair<QrCodeType, Integer> typeAndVersion =
				qrCodeClassifier.classifyQrCode(payload);
		QrCodeType qrCodeType = typeAndVersion.getFirst();
		int formatVersion = typeAndVersion.getSecond();
		if (qrCodeType != MAILBOX || formatVersion != QR_FORMAT_VERSION) {
			setState(new InvalidQrCode(qrCodeType, formatVersion));
			return;
		}
		try {
			pairMailbox();
		} catch (FormatException e) {
			onMailboxError(e, new InvalidQrCode(qrCodeType, formatVersion));
		} catch (MailboxAlreadyPairedException e) {
			onMailboxError(e, new MailboxAlreadyPaired());
		} catch (IOException e) {
			onMailboxError(e, new ConnectionError());
		} catch (ApiException | DbException e) {
			onMailboxError(e, new UnexpectedError());
		}
	}

	private void pairMailbox() throws IOException, ApiException, DbException {
		MailboxProperties mailboxProperties = decodeQrCodePayload(payload);
		setState(new Pairing(timeStarted));
		MailboxProperties ownerProperties = api.setup(mailboxProperties);
		long time = clock.currentTimeMillis();
		db.transaction(false, txn -> {
			mailboxSettingsManager
					.setOwnMailboxProperties(txn, ownerProperties);
			mailboxSettingsManager.recordSuccessfulConnection(txn, time,
					ownerProperties.getServerSupports());
			// A (possibly new) mailbox is paired. Reset message retransmission
			// timers for contacts who doesn't have their own mailbox. This way,
			// data stranded on our old mailbox will be re-uploaded to our new.
			for (Contact c : db.getContacts(txn)) {
				MailboxUpdate update = mailboxUpdateManager.getRemoteUpdate(
						txn, c.getId());
				if (update == null || !update.hasMailbox()) {
					db.resetUnackedMessagesToSend(txn, c.getId());
				}
			}
		});
		setState(new Paired());
	}

	private void onMailboxError(Exception e, MailboxPairingState state) {
		logException(LOG, WARNING, e);
		setState(state);
	}

	private void setState(MailboxPairingState state) {
		synchronized (lock) {
			this.state = state;
			notifyObservers();
		}
	}

	@GuardedBy("lock")
	private void notifyObservers() {
		List<Consumer<MailboxPairingState>> observers =
				new ArrayList<>(this.observers);
		MailboxPairingState state = this.state;
		eventExecutor.execute(() -> {
			for (Consumer<MailboxPairingState> o : observers) o.accept(state);
		});
	}

	private MailboxProperties decodeQrCodePayload(String payload)
			throws FormatException {
		byte[] bytes = payload.getBytes(ISO_8859_1);
		if (bytes.length != 65) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning("QR code length is not 65: " + bytes.length);
			}
			throw new FormatException();
		}
		LOG.info("QR code is valid");
		byte[] onionPubKey = Arrays.copyOfRange(bytes, 1, 33);
		String onion = crypto.encodeOnion(onionPubKey);
		byte[] tokenBytes = Arrays.copyOfRange(bytes, 33, 65);
		MailboxAuthToken setupToken = new MailboxAuthToken(tokenBytes);
		return new MailboxProperties(onion, setupToken, new ArrayList<>());
	}

}
