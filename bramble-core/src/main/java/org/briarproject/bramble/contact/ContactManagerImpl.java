package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedRemotelyEvent;
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Scheduler;
import org.briarproject.bramble.api.transport.KeyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.contact.PendingContactState.ADDING_CONTACT;
import static org.briarproject.bramble.api.contact.PendingContactState.CONNECTED;
import static org.briarproject.bramble.api.contact.PendingContactState.FAILED;
import static org.briarproject.bramble.api.contact.PendingContactState.WAITING_FOR_CONNECTION;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

@ThreadSafe
@NotNullByDefault
class ContactManagerImpl implements ContactManager {

	private static final String REMOTE_CONTACT_LINK =
			"briar://" + getRandomBase32String(LINK_LENGTH);
	// TODO replace with real implementation
	private final List<PendingContact> pendingContacts = new ArrayList<>();
	@DatabaseExecutor
	private final Executor dbExecutor;
	@Scheduler
	private final ScheduledExecutorService scheduler;

	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final IdentityManager identityManager;
	private final List<ContactHook> hooks;

	@Inject
	ContactManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, KeyManager keyManager,
			IdentityManager identityManager, @Scheduler
			ScheduledExecutorService scheduler) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.keyManager = keyManager;
		this.identityManager = identityManager;
		this.scheduler = scheduler;
		hooks = new CopyOnWriteArrayList<>();
	}

	@Override
	public void registerContactHook(ContactHook hook) {
		hooks.add(hook);
	}

	@Override
	public ContactId addContact(Transaction txn, Author remote, AuthorId local,
			SecretKey rootKey, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException {
		ContactId c = db.addContact(txn, remote, local, verified);
		keyManager.addContact(txn, c, rootKey, timestamp, alice, active);
		Contact contact = db.getContact(txn, c);
		for (ContactHook hook : hooks) hook.addingContact(txn, contact);
		return c;
	}

	@Override
	public ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean verified) throws DbException {
		ContactId c = db.addContact(txn, remote, local, verified);
		Contact contact = db.getContact(txn, c);
		for (ContactHook hook : hooks) hook.addingContact(txn, contact);
		return c;
	}

	@Override
	public ContactId addContact(Author remote, AuthorId local,
			SecretKey rootKey, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException {
		return db.transactionWithResult(false, txn ->
				addContact(txn, remote, local, rootKey, timestamp, alice,
						verified, active));
	}

	@Override
	public String getRemoteContactLink() {
		// TODO replace with real implementation
		try {
			Thread.sleep(1500);
		} catch (InterruptedException ignored) {
		}
		return REMOTE_CONTACT_LINK;
	}

	// TODO replace with real implementation
	@SuppressWarnings("SameParameterValue")
	private static String getRandomBase32String(int length) {
		Random random = new Random();
		char[] c = new char[length];
		for (int i = 0; i < length; i++) {
			int character = random.nextInt(32);
			if (character < 26) c[i] = (char) ('a' + character);
			else c[i] = (char) ('2' + (character - 26));
		}
		return new String(c);
	}

	@Override
	public void addRemoteContactRequest(String link, String alias) {
		// TODO replace with real implementation
		PendingContactId id = new PendingContactId(
				link.substring(0, PendingContactId.LENGTH).getBytes());
		PendingContact pendingContact =
				new PendingContact(id, new byte[MAX_PUBLIC_KEY_LENGTH],
						alias, WAITING_FOR_CONNECTION, currentTimeMillis());
		dbExecutor.execute(() -> {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException ignored) {
			}
			try {
				getLogger("TMP").warning("WAITING_FOR_CONNECTION");
				pendingContacts.add(pendingContact);
				Event e = new PendingContactStateChangedEvent(id,
						WAITING_FOR_CONNECTION);
				db.transaction(true, txn -> txn.attach(e));
			} catch (DbException ignored) {
			}
		});

		scheduler.schedule(() -> dbExecutor.execute(() -> {
			getLogger("TMP").warning("CONNECTED");
			pendingContacts.remove(pendingContact);
			PendingContact updated = new PendingContact(id,
					pendingContact.getPublicKey(), alias, CONNECTED,
					pendingContact.getTimestamp());
			pendingContacts.add(updated);
			Event e = new PendingContactStateChangedEvent(id, CONNECTED);
			try {
				db.transaction(true, txn -> txn.attach(e));
			} catch (DbException ignored) {
			}
		}), 20, SECONDS);

		scheduler.schedule(() -> dbExecutor.execute(() -> {
			getLogger("TMP").warning("ADDING_CONTACT");
			pendingContacts.remove(pendingContact);
			PendingContact updated = new PendingContact(id,
					pendingContact.getPublicKey(), alias, ADDING_CONTACT,
					pendingContact.getTimestamp());
			pendingContacts.add(updated);
			Event e =
					new PendingContactStateChangedEvent(id, ADDING_CONTACT);
			try {
				db.transaction(true, txn -> txn.attach(e));
			} catch (DbException ignored) {
			}
		}), 40, SECONDS);

		scheduler.schedule(() -> dbExecutor.execute(() -> {
			pendingContacts.remove(pendingContact);
			Event e;
			try {
				if (new Random().nextBoolean()) {
					getLogger("TMP").warning("FAILED");
					e = new PendingContactStateChangedEvent(id, FAILED);
					PendingContact updated = new PendingContact(id,
							pendingContact.getPublicKey(), alias, FAILED,
							pendingContact.getTimestamp());
					pendingContacts.add(updated);
				} else {
					getLogger("TMP").warning("ADDED");
					ContactId cid = new ContactId(Integer.MAX_VALUE);
					AuthorId aid = identityManager.getLocalAuthor().getId();
					Contact c = new Contact(cid, null, aid, alias,
							new byte[MAX_PUBLIC_KEY_LENGTH], true);
					e = new ContactAddedRemotelyEvent(c);
				}
				db.transaction(true, txn -> txn.attach(e));
			} catch (DbException ignored) {
			}
		}), 60, SECONDS);
	}

	@Override
	public Collection<PendingContact> getPendingContacts() {
		// TODO replace with real implementation
		return pendingContacts;
	}

	@Override
	public void removePendingContact(PendingContact pendingContact,
			Runnable commitAction) throws DbException {
		// TODO replace with real implementation
		pendingContacts.remove(pendingContact);
		try {
			Thread.sleep(250);
		} catch (InterruptedException ignored) {
		}
		db.transaction(true, txn -> txn.attach(commitAction));
	}

	@Override
	public Contact getContact(ContactId c) throws DbException {
		return db.transactionWithResult(true, txn -> db.getContact(txn, c));
	}

	@Override
	public Contact getContact(AuthorId remoteAuthorId, AuthorId localAuthorId)
			throws DbException {
		return db.transactionWithResult(true, txn ->
				getContact(txn, remoteAuthorId, localAuthorId));
	}

	@Override
	public Contact getContact(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException {
		Collection<Contact> contacts =
				db.getContactsByAuthorId(txn, remoteAuthorId);
		for (Contact c : contacts) {
			if (c.getLocalAuthorId().equals(localAuthorId)) {
				return c;
			}
		}
		throw new NoSuchContactException();
	}

	@Override
	public Collection<Contact> getContacts() throws DbException {
		return db.transactionWithResult(true, db::getContacts);
	}

	@Override
	public void removeContact(ContactId c) throws DbException {
		db.transaction(false, txn -> removeContact(txn, c));
	}

	@Override
	public void setContactAlias(Transaction txn, ContactId c,
			@Nullable String alias) throws DbException {
		if (alias != null) {
			int aliasLength = toUtf8(alias).length;
			if (aliasLength == 0 || aliasLength > MAX_AUTHOR_NAME_LENGTH)
				throw new IllegalArgumentException();
		}
		db.setContactAlias(txn, c, alias);
	}

	@Override
	public void setContactAlias(ContactId c, @Nullable String alias)
			throws DbException {
		db.transaction(false, txn -> setContactAlias(txn, c, alias));
	}

	@Override
	public boolean contactExists(Transaction txn, AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException {
		return db.containsContact(txn, remoteAuthorId, localAuthorId);
	}

	@Override
	public boolean contactExists(AuthorId remoteAuthorId,
			AuthorId localAuthorId) throws DbException {
		return db.transactionWithResult(true, txn ->
				contactExists(txn, remoteAuthorId, localAuthorId));
	}

	@Override
	public void removeContact(Transaction txn, ContactId c)
			throws DbException {
		Contact contact = db.getContact(txn, c);
		for (ContactHook hook : hooks) hook.removingContact(txn, contact);
		db.removeContact(txn, c);
	}

	@Override
	public AuthorInfo getAuthorInfo(AuthorId a) throws DbException {
		return db.transactionWithResult(true, txn -> getAuthorInfo(txn, a));
	}

	@Override
	public AuthorInfo getAuthorInfo(Transaction txn, AuthorId authorId)
			throws DbException {
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		if (localAuthor.getId().equals(authorId))
			return new AuthorInfo(OURSELVES);
		Collection<Contact> contacts = db.getContactsByAuthorId(txn, authorId);
		if (contacts.isEmpty()) return new AuthorInfo(UNKNOWN);
		if (contacts.size() > 1) throw new AssertionError();
		Contact c = contacts.iterator().next();
		if (c.isVerified()) return new AuthorInfo(VERIFIED, c.getAlias());
		else return new AuthorInfo(UNVERIFIED, c.getAlias());
	}

}
