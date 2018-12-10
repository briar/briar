package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.KeyManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.contact.PendingContact.PendingContactState.WAITING_FOR_CONNECTION;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

@ThreadSafe
@NotNullByDefault
class ContactManagerImpl implements ContactManager {

	private static final int LINK_LENGTH = 64;
	private static final String REMOTE_CONTACT_LINK =
			"briar://" + getRandomBase32String(LINK_LENGTH);
	private static final Pattern LINK_REGEX =
			Pattern.compile("(briar://)?([a-z2-7]{" + LINK_LENGTH + "})");

	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final IdentityManager identityManager;
	private final List<ContactHook> hooks;

	@Inject
	ContactManagerImpl(DatabaseComponent db, KeyManager keyManager,
			IdentityManager identityManager) {
		this.db = db;
		this.keyManager = keyManager;
		this.identityManager = identityManager;
		hooks = new CopyOnWriteArrayList<>();
	}

	@Override
	public void registerContactHook(ContactHook hook) {
		hooks.add(hook);
	}

	@Override
	public ContactId addContact(Transaction txn, Author remote, AuthorId local,
			SecretKey master, long timestamp, boolean alice, boolean verified,
			boolean active) throws DbException {
		ContactId c = db.addContact(txn, remote, local, verified, active);
		keyManager.addContact(txn, c, master, timestamp, alice, active);
		Contact contact = db.getContact(txn, c);
		for (ContactHook hook : hooks) hook.addingContact(txn, contact);
		return c;
	}

	@Override
	public ContactId addContact(Transaction txn, Author remote, AuthorId local,
			boolean verified, boolean active) throws DbException {
		ContactId c = db.addContact(txn, remote, local, verified, active);
		Contact contact = db.getContact(txn, c);
		for (ContactHook hook : hooks) hook.addingContact(txn, contact);
		return c;
	}

	@Override
	public ContactId addContact(Author remote, AuthorId local, SecretKey master,
			long timestamp, boolean alice, boolean verified, boolean active)
			throws DbException {
		return db.transactionWithResult(false, txn ->
				addContact(txn, remote, local, master, timestamp, alice,
						verified, active));
	}

	@Override
	public String getRemoteContactLink() {
		// TODO replace with real implementation
		return REMOTE_CONTACT_LINK;
	}

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
	public boolean isValidRemoteContactLink(String link) {
		return LINK_REGEX.matcher(link).matches();
	}

	@Override
	public PendingContact addRemoteContactRequest(String link, String alias) {
		// TODO replace with real implementation
		PendingContactId id = new PendingContactId(link.getBytes());
		return new PendingContact(id, alias, WAITING_FOR_CONNECTION,
				System.currentTimeMillis());
	}

	@Override
	public Collection<PendingContact> getPendingContacts() {
		// TODO replace with real implementation
		return emptyList();
	}

	@Override
	public void removePendingContact(PendingContact pendingContact) {
		// TODO replace with real implementation
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
	public Collection<Contact> getActiveContacts() throws DbException {
		Collection<Contact> contacts =
				db.transactionWithResult(true, db::getContacts);
		List<Contact> active = new ArrayList<>(contacts.size());
		for (Contact c : contacts) if (c.isActive()) active.add(c);
		return active;
	}

	@Override
	public void removeContact(ContactId c) throws DbException {
		db.transaction(false, txn -> removeContact(txn, c));
	}

	@Override
	public void setContactActive(Transaction txn, ContactId c, boolean active)
			throws DbException {
		db.setContactActive(txn, c, active);
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
