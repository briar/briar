package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxContact;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@ThreadSafe
@NotNullByDefault
class OwnMailboxContactListWorker
		implements MailboxWorker, ConnectivityObserver, EventListener {

	/**
	 * When the worker is started it waits for a connectivity check, then
	 * fetches the remote contact list and compares it to the local contact
	 * list.
	 * <p>
	 * Any contacts that are missing from the remote list are added to the
	 * mailbox's contact list, while any contacts that are missing from the
	 * local list are removed from the mailbox's contact list.
	 * <p>
	 * Once the remote contact list has been brought up to date, the worker
	 * waits for events indicating that contacts have been added or removed.
	 * Each time an event is received, the worker updates the mailbox's
	 * contact list and then goes back to waiting.
	 */
	private enum State {
		CREATED,
		CONNECTIVITY_CHECK,
		FETCHING_CONTACT_LIST,
		UPDATING_CONTACT_LIST,
		WAITING_FOR_CHANGES,
		DESTROYED
	}

	private static final Logger LOG =
			getLogger(OwnMailboxContactListWorker.class.getName());

	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final ConnectivityChecker connectivityChecker;
	private final MailboxApiCaller mailboxApiCaller;
	private final MailboxApi mailboxApi;
	private final MailboxUpdateManager mailboxUpdateManager;
	private final MailboxProperties mailboxProperties;
	private final Object lock = new Object();

	@GuardedBy("lock")
	private State state = State.CREATED;

	@GuardedBy("lock")
	@Nullable
	private Cancellable apiCall = null;

	/**
	 * A queue of updates waiting to be applied to the remote contact list.
	 */
	@GuardedBy("lock")
	private final Queue<Update> updates = new LinkedList<>();

	OwnMailboxContactListWorker(@IoExecutor Executor ioExecutor,
			DatabaseComponent db,
			EventBus eventBus,
			ConnectivityChecker connectivityChecker,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxUpdateManager mailboxUpdateManager,
			MailboxProperties mailboxProperties) {
		if (!mailboxProperties.isOwner()) throw new IllegalArgumentException();
		this.ioExecutor = ioExecutor;
		this.db = db;
		this.connectivityChecker = connectivityChecker;
		this.mailboxApiCaller = mailboxApiCaller;
		this.mailboxApi = mailboxApi;
		this.mailboxUpdateManager = mailboxUpdateManager;
		this.mailboxProperties = mailboxProperties;
		this.eventBus = eventBus;
	}

	@Override
	public void start() {
		LOG.info("Started");
		synchronized (lock) {
			if (state != State.CREATED) return;
			state = State.CONNECTIVITY_CHECK;
		}
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
	public void destroy() {
		LOG.info("Destroyed");
		Cancellable apiCall;
		synchronized (lock) {
			state = State.DESTROYED;
			apiCall = this.apiCall;
			this.apiCall = null;
		}
		if (apiCall != null) apiCall.cancel();
		connectivityChecker.removeObserver(this);
		eventBus.removeListener(this);
	}

	@Override
	public void onConnectivityCheckSucceeded() {
		LOG.info("Connectivity check succeeded");
		synchronized (lock) {
			if (state != State.CONNECTIVITY_CHECK) return;
			state = State.FETCHING_CONTACT_LIST;
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(this::apiCallFetchContactList));
		}
	}

	@IoExecutor
	private void apiCallFetchContactList() throws IOException, ApiException {
		synchronized (lock) {
			if (state != State.FETCHING_CONTACT_LIST) return;
		}
		LOG.info("Fetching remote contact list");
		Collection<ContactId> remote =
				mailboxApi.getContacts(mailboxProperties);
		ioExecutor.execute(() -> loadLocalContactList(remote));
	}

	@IoExecutor
	private void loadLocalContactList(Collection<ContactId> remote) {
		synchronized (lock) {
			if (state != State.FETCHING_CONTACT_LIST) return;
			apiCall = null;
		}
		LOG.info("Loading local contact list");
		try {
			db.transaction(true, txn -> {
				Collection<Contact> local = db.getContacts(txn);
				// Handle the result on the event executor to avoid races with
				// incoming events
				txn.attach(() -> reconcileContactLists(local, remote));
			});
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	@EventExecutor
	private void reconcileContactLists(Collection<Contact> local,
			Collection<ContactId> remote) {
		Set<ContactId> localIds = new HashSet<>();
		for (Contact c : local) localIds.add(c.getId());
		remote = new HashSet<>(remote);
		synchronized (lock) {
			if (state != State.FETCHING_CONTACT_LIST) return;
			for (ContactId c : localIds) {
				if (!remote.contains(c)) updates.add(new Update(true, c));
			}
			for (ContactId c : remote) {
				if (!localIds.contains(c)) updates.add(new Update(false, c));
			}
			if (updates.isEmpty()) {
				LOG.info("Contact list is up to date");
				state = State.WAITING_FOR_CHANGES;
			} else {
				if (LOG.isLoggable(INFO)) {
					LOG.info(updates.size() + " updates to apply");
				}
				state = State.UPDATING_CONTACT_LIST;
				ioExecutor.execute(this::updateContactList);
			}
		}
	}

	@IoExecutor
	private void updateContactList() {
		Update update;
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
			update = updates.poll();
			if (update == null) {
				LOG.info("No more updates to process");
				state = State.WAITING_FOR_CHANGES;
				apiCall = null;
				return;
			}
		}
		if (update.add) loadMailboxProperties(update.contactId);
		else removeContact(update.contactId);
	}

	@IoExecutor
	private void loadMailboxProperties(ContactId c) {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
		}
		LOG.info("Loading mailbox properties for contact");
		try {
			MailboxUpdate mailboxUpdate = db.transactionWithResult(true, txn ->
					mailboxUpdateManager.getLocalUpdate(txn, c));
			if (mailboxUpdate instanceof MailboxUpdateWithMailbox) {
				addContact(c, (MailboxUpdateWithMailbox) mailboxUpdate);
			} else {
				// Our own mailbox was concurrently unpaired. This worker will
				// be destroyed soon, so we can stop here
				LOG.info("Own mailbox was unpaired");
			}
		} catch (NoSuchContactException e) {
			// Contact was removed concurrently. Move on to the next update.
			// Later we may process a removal update for this contact, which
			// was never added to the mailbox's contact list. The removal API
			// call should fail safely with a TolerableFailureException
			LOG.info("No such contact");
			updateContactList();
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	@IoExecutor
	private void addContact(ContactId c, MailboxUpdateWithMailbox withMailbox) {
		MailboxProperties props = withMailbox.getMailboxProperties();
		MailboxContact contact = new MailboxContact(c, props.getAuthToken(),
				requireNonNull(props.getInboxId()),
				requireNonNull(props.getOutboxId()));
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
			apiCall = mailboxApiCaller.retryWithBackoff(new SimpleApiCall(() ->
					apiCallAddContact(contact)));
		}
	}

	@IoExecutor
	private void apiCallAddContact(MailboxContact contact)
			throws IOException, ApiException, TolerableFailureException {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
		}
		LOG.info("Adding contact to remote contact list");
		mailboxApi.addContact(mailboxProperties, contact);
		updateContactList();
	}

	@IoExecutor
	private void removeContact(ContactId c) {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
			apiCall = mailboxApiCaller.retryWithBackoff(new SimpleApiCall(() ->
					apiCallRemoveContact(c)));
		}
	}

	@IoExecutor
	private void apiCallRemoveContact(ContactId c)
			throws IOException, ApiException {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST) return;
		}
		LOG.info("Removing contact from remote contact list");
		try {
			mailboxApi.deleteContact(mailboxProperties, c);
		} catch (TolerableFailureException e) {
			// Catch this so we can continue to the next update
			LOG.warning("Contact does not exist");
		}
		updateContactList();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			LOG.info("Contact added");
			onContactAdded(((ContactAddedEvent) e).getContactId());
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed");
			onContactRemoved(((ContactRemovedEvent) e).getContactId());
		}
	}

	@EventExecutor
	private void onContactAdded(ContactId c) {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST &&
					state != State.WAITING_FOR_CHANGES) {
				return;
			}
			updates.add(new Update(true, c));
			if (state == State.WAITING_FOR_CHANGES) {
				state = State.UPDATING_CONTACT_LIST;
				ioExecutor.execute(this::updateContactList);
			}
		}
	}

	@EventExecutor
	private void onContactRemoved(ContactId c) {
		synchronized (lock) {
			if (state != State.UPDATING_CONTACT_LIST &&
					state != State.WAITING_FOR_CHANGES) {
				return;
			}
			updates.add(new Update(false, c));
			if (state == State.WAITING_FOR_CHANGES) {
				state = State.UPDATING_CONTACT_LIST;
				ioExecutor.execute(this::updateContactList);
			}
		}
	}

	/**
	 * An update that should be applied to the remote contact list.
	 */
	private static class Update {

		/**
		 * True if the contact should be added, false if the contact should be
		 * removed.
		 */
		private final boolean add;
		private final ContactId contactId;

		private Update(boolean add, ContactId contactId) {
			this.add = add;
			this.contactId = contactId;
		}
	}
}
