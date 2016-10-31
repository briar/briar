package org.briarproject.transport;

import org.briarproject.BriarTestCase;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TransportKeyManager;
import org.briarproject.api.transport.TransportKeyManagerFactory;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.DeterministicExecutor;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.TestUtils.getRandomBytes;
import static org.briarproject.TestUtils.getRandomId;
import static org.briarproject.TestUtils.getSecretKey;
import static org.junit.Assert.assertEquals;

public class KeyManagerImplTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final KeyManagerImpl keyManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final PluginConfig pluginConfig = context.mock(PluginConfig.class);
	private final TransportKeyManagerFactory transportKeyManagerFactory =
			context.mock(TransportKeyManagerFactory.class);
	private final TransportKeyManager transportKeyManager =
			context.mock(TransportKeyManager.class);
	private final DeterministicExecutor executor = new DeterministicExecutor();
	private final Transaction txn = new Transaction(null, false);
	private final ContactId contactId = new ContactId(42);
	private final ContactId inactiveContactId = new ContactId(43);
	private final TransportId transportId = new TransportId("tId");
	private final StreamContext streamContext =
			new StreamContext(contactId, transportId, getSecretKey(),
					getSecretKey(), 1);
	private final byte[] tag = getRandomBytes(42);

	public KeyManagerImplTest() {
		keyManager = new KeyManagerImpl(db, executor, pluginConfig,
				transportKeyManagerFactory);
	}

	@Before
	public void testStartService() throws Exception {
		final Transaction txn = new Transaction(null, false);
		AuthorId remoteAuthorId = new AuthorId(getRandomId());
		Author remoteAuthor = new Author(remoteAuthorId, "author",
				getRandomBytes(42));
		AuthorId localAuthorId = new AuthorId(getRandomId());
		final Collection<Contact> contacts = new ArrayList<>();
		contacts.add(new Contact(contactId, remoteAuthor, localAuthorId, true,
				true));
		contacts.add(new Contact(inactiveContactId, remoteAuthor, localAuthorId,
				true, false));
		final SimplexPluginFactory pluginFactory =
				context.mock(SimplexPluginFactory.class);
		final Collection<SimplexPluginFactory> factories = Collections
				.singletonList(pluginFactory);
		final int maxLatency = 1337;

		context.checking(new Expectations() {{
			oneOf(pluginConfig).getSimplexFactories();
			will(returnValue(factories));
			oneOf(pluginFactory).getId();
			will(returnValue(transportId));
			oneOf(pluginFactory).getMaxLatency();
			will(returnValue(maxLatency));
			oneOf(db).addTransport(txn, transportId, maxLatency);
			oneOf(transportKeyManagerFactory)
					.createTransportKeyManager(transportId, maxLatency);
			will(returnValue(transportKeyManager));
			oneOf(pluginConfig).getDuplexFactories();
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(transportKeyManager).start(txn);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		keyManager.startService();
	}

	@Test
	public void testAddContact() throws Exception {
		final SecretKey secretKey = getSecretKey();
		final long timestamp = 42L;
		final boolean alice =  true;

		context.checking(new Expectations() {{
			oneOf(transportKeyManager)
					.addContact(txn, contactId, secretKey, timestamp, alice);
		}});

		keyManager.addContact(txn, contactId, secretKey, timestamp, alice);
		context.assertIsSatisfied();
	}

	@Test
	public void testGetStreamContextForContact() throws Exception {
		assertEquals(null,
				keyManager.getStreamContext(inactiveContactId, transportId));
		assertEquals(null, keyManager
				.getStreamContext(inactiveContactId, new TransportId("id")));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(transportKeyManager).getStreamContext(txn, contactId);
			will(returnValue(streamContext));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(streamContext,
				keyManager.getStreamContext(contactId, transportId));
		context.assertIsSatisfied();
	}

	@Test
	public void testGetStreamContextForTag() throws Exception {
		assertEquals(null, keyManager
				.getStreamContext(new TransportId("id"), tag));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(transportKeyManager).getStreamContext(txn, tag);
			will(returnValue(streamContext));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		assertEquals(streamContext,
				keyManager.getStreamContext(transportId, tag));
		context.assertIsSatisfied();
	}

	@Test
	public void testContactRemovedEvent() throws Exception {
		ContactRemovedEvent event = new ContactRemovedEvent(contactId);

		context.checking(new Expectations() {{
			oneOf(transportKeyManager).removeContact(contactId);
		}});

		keyManager.eventOccurred(event);
		executor.runUntilIdle();
		assertEquals(null, keyManager.getStreamContext(contactId, transportId));

		context.assertIsSatisfied();
	}

	@Test
	public void testContactStatusChangedEvent() throws Exception {
		ContactStatusChangedEvent event =
				new ContactStatusChangedEvent(inactiveContactId, true);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			oneOf(transportKeyManager).getStreamContext(txn, inactiveContactId);
			will(returnValue(streamContext));
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		keyManager.eventOccurred(event);
		assertEquals(streamContext,
				keyManager.getStreamContext(inactiveContactId, transportId));

		context.assertIsSatisfied();
	}

}
