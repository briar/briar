package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.OutgoingSessionRecord;
import org.briarproject.bramble.api.sync.event.GroupVisibilityUpdatedEvent;
import org.briarproject.bramble.api.sync.event.MessageSharedEvent;
import org.briarproject.bramble.api.sync.event.MessageToAckEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.MAX_LATENCY;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.util.IoUtils.delete;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class MailboxUploadWorker implements MailboxWorker, ConnectivityObserver,
		EventListener {

	/**
	 * When the worker is started it checks for data to send. If data is ready
	 * to send, the worker waits for a connectivity check, then writes and
	 * uploads a file and checks again for data to send.
	 * <p>
	 * If data is due to be sent at some time in the future, the worker
	 * schedules a wakeup for that time and also listens for events indicating
	 * that new data may be ready to send.
	 * <p>
	 * If there's no data to send, the worker listens for events indicating
	 * that new data may be ready to send.
	 * <p>
	 * Whenever we're directly connected to the contact, the worker doesn't
	 * check for data to send or start connectivity checks until the contact
	 * disconnects. However, if the worker has already started writing and
	 * uploading a file when the contact connects, the worker will finish the
	 * upload.
	 */
	private enum State {
		CREATED,
		CONNECTED_TO_CONTACT,
		CHECKING_FOR_DATA,
		WAITING_FOR_DATA,
		CONNECTIVITY_CHECK,
		WRITING_UPLOADING,
		DESTROYED
	}

	private static final Logger LOG =
			getLogger(MailboxUploadWorker.class.getName());

	/**
	 * When we're waiting for data to send and an event indicates that new data
	 * may have become available, wait this long before checking the DB. This
	 * should help to avoid creating lots of small files when several acks or
	 * messages become available to send in a short period (eg when reading a
	 * file downloaded from a mailbox).
	 * <p>
	 * Package access for testing.
	 */
	static final long CHECK_DELAY_MS = 5_000;

	/**
	 * How long to wait before retrying when an exception occurs while writing
	 * a file.
	 * <p>
	 * Package access for testing.
	 */
	static final long RETRY_DELAY_MS = MINUTES.toMillis(1);

	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final Clock clock;
	private final TaskScheduler taskScheduler;
	private final EventBus eventBus;
	private final ConnectionRegistry connectionRegistry;
	private final ConnectivityChecker connectivityChecker;
	private final MailboxApiCaller mailboxApiCaller;
	private final MailboxApi mailboxApi;
	private final MailboxFileManager mailboxFileManager;
	private final MailboxProperties mailboxProperties;
	private final MailboxFolderId folderId;
	private final ContactId contactId;

	private final Object lock = new Object();

	@GuardedBy("lock")
	private State state = State.CREATED;

	@GuardedBy("lock")
	@Nullable
	private Cancellable wakeupTask = null, checkTask = null, apiCall = null;

	@GuardedBy("lock")
	@Nullable
	private File file = null;

	MailboxUploadWorker(@IoExecutor Executor ioExecutor,
			DatabaseComponent db,
			Clock clock,
			TaskScheduler taskScheduler,
			EventBus eventBus,
			ConnectionRegistry connectionRegistry,
			ConnectivityChecker connectivityChecker,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxFileManager mailboxFileManager,
			MailboxProperties mailboxProperties,
			MailboxFolderId folderId,
			ContactId contactId) {
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.clock = clock;
		this.taskScheduler = taskScheduler;
		this.eventBus = eventBus;
		this.connectionRegistry = connectionRegistry;
		this.connectivityChecker = connectivityChecker;
		this.mailboxApiCaller = mailboxApiCaller;
		this.mailboxApi = mailboxApi;
		this.mailboxFileManager = mailboxFileManager;
		this.mailboxProperties = mailboxProperties;
		this.folderId = folderId;
		this.contactId = contactId;
	}

	@Override
	public void start() {
		LOG.info("Started");
		synchronized (lock) {
			// Don't allow the worker to be reused
			if (state != State.CREATED) return;
			state = State.CHECKING_FOR_DATA;
		}
		ioExecutor.execute(this::checkForDataToSend);
	}

	@Override
	public void destroy() {
		LOG.info("Destroyed");
		Cancellable wakeupTask, checkTask, apiCall;
		File file;
		synchronized (lock) {
			state = State.DESTROYED;
			wakeupTask = this.wakeupTask;
			this.wakeupTask = null;
			checkTask = this.checkTask;
			this.checkTask = null;
			apiCall = this.apiCall;
			this.apiCall = null;
			file = this.file;
			this.file = null;
		}
		if (wakeupTask != null) wakeupTask.cancel();
		if (checkTask != null) checkTask.cancel();
		if (apiCall != null) apiCall.cancel();
		if (file != null) delete(file);
		connectivityChecker.removeObserver(this);
		eventBus.removeListener(this);
	}

	@IoExecutor
	private void checkForDataToSend() {
		synchronized (lock) {
			checkTask = null;
			if (state != State.CHECKING_FOR_DATA) return;
			// Check whether we're directly connected to the contact. Calling
			// this while holding the lock isn't ideal, but it avoids races
			if (connectionRegistry.isConnected(contactId)) {
				state = State.CONNECTED_TO_CONTACT;
				return;
			}
		}
		LOG.info("Checking for data to send");
		try {
			db.transaction(true, txn -> {
				long nextSendTime;
				if (db.containsAcksToSend(txn, contactId)) {
					nextSendTime = 0L;
				} else {
					nextSendTime = db.getNextSendTime(txn, contactId,
							MAX_LATENCY);
				}
				// Handle the result on the event executor to avoid races with
				// incoming events
				txn.attach(() -> handleNextSendTime(nextSendTime));
			});
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	@EventExecutor
	private void handleNextSendTime(long nextSendTime) {
		if (nextSendTime == Long.MAX_VALUE) {
			// Nothing is sendable now or due to be sent in the future. Wait
			// for an event indicating that new data may be ready to send
			waitForDataToSend();
		} else {
			// Work out the delay until data's ready to send (may be negative)
			long delay = nextSendTime - clock.currentTimeMillis();
			if (delay > 0) {
				// Schedule a wakeup when data will be ready to send. If an
				// event is received in the meantime indicating that new data
				// may be ready to send, we'll cancel the wakeup
				scheduleWakeup(delay);
			} else {
				// Data is ready to send now
				checkConnectivity();
			}
		}
	}

	@EventExecutor
	private void waitForDataToSend() {
		synchronized (lock) {
			if (state != State.CHECKING_FOR_DATA) return;
			state = State.WAITING_FOR_DATA;
			LOG.info("Waiting for data to send");
		}
	}

	@EventExecutor
	private void scheduleWakeup(long delay) {
		synchronized (lock) {
			if (state != State.CHECKING_FOR_DATA) return;
			state = State.WAITING_FOR_DATA;
			if (LOG.isLoggable(INFO)) {
				LOG.info("Scheduling wakeup in " + delay + " ms");
			}
			wakeupTask = taskScheduler.schedule(this::wakeUp, ioExecutor,
					delay, MILLISECONDS);
		}
	}

	@IoExecutor
	private void wakeUp() {
		LOG.info("Woke up");
		synchronized (lock) {
			wakeupTask = null;
			if (state != State.WAITING_FOR_DATA) return;
			state = State.CHECKING_FOR_DATA;
		}
		checkForDataToSend();
	}

	@EventExecutor
	private void checkConnectivity() {
		synchronized (lock) {
			if (state != State.CHECKING_FOR_DATA) return;
			state = State.CONNECTIVITY_CHECK;
		}
		LOG.info("Checking connectivity");
		// Avoid leaking observer in case destroy() is called concurrently
		// before observer is added
		connectivityChecker.checkConnectivity(mailboxProperties, this);
		boolean destroyed;
		synchronized (lock) {
			destroyed = state == State.DESTROYED;
		}
		if (destroyed) connectivityChecker.removeObserver(this);
	}

	@Override
	public void onConnectivityCheckSucceeded() {
		LOG.info("Connectivity check succeeded");
		synchronized (lock) {
			if (state != State.CONNECTIVITY_CHECK) return;
			state = State.WRITING_UPLOADING;
		}
		ioExecutor.execute(this::writeAndUploadFile);
	}

	@IoExecutor
	private void writeAndUploadFile() {
		synchronized (lock) {
			if (state != State.WRITING_UPLOADING) return;
		}
		OutgoingSessionRecord sessionRecord = new OutgoingSessionRecord();
		File file;
		try {
			file = mailboxFileManager.createAndWriteTempFileForUpload(
					contactId, sessionRecord);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			// Try again after a delay
			synchronized (lock) {
				if (state != State.WRITING_UPLOADING) return;
				state = State.CHECKING_FOR_DATA;
				checkTask = taskScheduler.schedule(this::checkForDataToSend,
						ioExecutor, RETRY_DELAY_MS, MILLISECONDS);
			}
			return;
		}
		boolean deleteFile = false;
		synchronized (lock) {
			if (state == State.WRITING_UPLOADING) {
				this.file = file;
				apiCall = mailboxApiCaller.retryWithBackoff(
						new SimpleApiCall(() -> apiCallUploadFile(file,
								sessionRecord)));
			} else {
				deleteFile = true;
			}
		}
		if (deleteFile) delete(file);
	}

	@IoExecutor
	private void apiCallUploadFile(File file,
			OutgoingSessionRecord sessionRecord)
			throws IOException, ApiException {
		synchronized (lock) {
			if (state != State.WRITING_UPLOADING) return;
		}
		LOG.info("Uploading file");
		mailboxApi.addFile(mailboxProperties, folderId, file);
		markMessagesSentOrAcked(sessionRecord);
		delete(file);
		synchronized (lock) {
			if (state != State.WRITING_UPLOADING) return;
			state = State.CHECKING_FOR_DATA;
			apiCall = null;
			this.file = null;
		}
		checkForDataToSend();
	}

	private void markMessagesSentOrAcked(OutgoingSessionRecord sessionRecord) {
		Collection<MessageId> acked = sessionRecord.getAckedIds();
		Collection<MessageId> sent = sessionRecord.getSentIds();
		try {
			db.transaction(false, txn -> {
				if (!acked.isEmpty()) {
					db.setAckSent(txn, contactId, acked);
				}
				if (!sent.isEmpty()) {
					db.setMessagesSent(txn, contactId, sent, MAX_LATENCY);
				}
			});
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof MessageToAckEvent) {
			MessageToAckEvent m = (MessageToAckEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Message to ack");
				onDataToSend();
			}
		} else if (e instanceof MessageSharedEvent) {
			MessageSharedEvent m = (MessageSharedEvent) e;
			// If the contact is present in the map (ie the value is not null)
			// and the value is true, the message's group is shared with the
			// contact and therefore the message may now be sendable
			if (m.getGroupVisibility().get(contactId) == TRUE) {
				LOG.info("Message shared");
				onDataToSend();
			}
		} else if (e instanceof GroupVisibilityUpdatedEvent) {
			GroupVisibilityUpdatedEvent g = (GroupVisibilityUpdatedEvent) e;
			if (g.getVisibility() == SHARED &&
					g.getAffectedContacts().contains(contactId)) {
				LOG.info("Group shared");
				onDataToSend();
			}
		} else if (e instanceof ContactConnectedEvent) {
			ContactConnectedEvent c = (ContactConnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact connected");
				onContactConnected();
			}
		} else if (e instanceof ContactDisconnectedEvent) {
			ContactDisconnectedEvent c = (ContactDisconnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact disconnected");
				onContactDisconnected();
			}
		}
	}

	@EventExecutor
	private void onDataToSend() {
		Cancellable wakeupTask;
		synchronized (lock) {
			if (state != State.WAITING_FOR_DATA) return;
			state = State.CHECKING_FOR_DATA;
			wakeupTask = this.wakeupTask;
			this.wakeupTask = null;
			// Delay the check to avoid creating lots of small files
			checkTask = taskScheduler.schedule(this::checkForDataToSend,
					ioExecutor, CHECK_DELAY_MS, MILLISECONDS);
		}
		// If we had scheduled a wakeup when data was due to be sent, cancel it
		if (wakeupTask != null) wakeupTask.cancel();
	}

	@EventExecutor
	private void onContactConnected() {
		Cancellable wakeupTask = null, checkTask = null;
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			// If we're checking for data to send, waiting for data to send,
			// or checking connectivity then wait until we disconnect from
			// the contact before proceeding. If we're writing or uploading
			// a file then continue
			if (state == State.CHECKING_FOR_DATA ||
					state == State.WAITING_FOR_DATA ||
					state == State.CONNECTIVITY_CHECK) {
				state = State.CONNECTED_TO_CONTACT;
				wakeupTask = this.wakeupTask;
				this.wakeupTask = null;
				checkTask = this.checkTask;
				this.checkTask = null;
			}
		}
		if (wakeupTask != null) wakeupTask.cancel();
		if (checkTask != null) checkTask.cancel();
	}

	@EventExecutor
	private void onContactDisconnected() {
		synchronized (lock) {
			if (state != State.CONNECTED_TO_CONTACT) return;
			state = State.CHECKING_FOR_DATA;
		}
		ioExecutor.execute(this::checkForDataToSend);
	}
}
