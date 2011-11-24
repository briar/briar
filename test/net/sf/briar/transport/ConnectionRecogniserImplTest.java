package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.plugins.ImmediateExecutor;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionRecogniserImplTest extends TestCase {

	private final CryptoComponent crypto;
	private final ContactId contactId;
	private final byte[] inSecret;
	private final TransportId transportId;
	private final TransportIndex localIndex, remoteIndex;
	private final Collection<Transport> localTransports, remoteTransports;

	public ConnectionRecogniserImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		contactId = new ContactId(1);
		inSecret = new byte[32];
		new Random().nextBytes(inSecret);
		transportId = new TransportId(TestUtils.getRandomId());
		localIndex = new TransportIndex(13);
		remoteIndex = new TransportIndex(7);
		Map<String, String> properties = Collections.singletonMap("foo", "bar");
		Transport localTransport = new Transport(transportId, localIndex,
				properties);
		localTransports = Collections.singletonList(localTransport);
		Transport remoteTransport = new Transport(transportId, remoteIndex,
				properties);
		remoteTransports = Collections.singletonList(remoteTransport);
	}

	@Test
	public void testUnexpectedIv() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		assertNull(c.acceptConnection(transportId, new byte[IV_LENGTH]));
		context.assertIsSatisfied();
	}

	@Test
	public void testExpectedIv() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// The IV should not be expected by the wrong transport
		TransportId wrong = new TransportId(TestUtils.getRandomId());
		assertNull(c.acceptConnection(wrong, encryptedIv));
		// The IV should be expected by the right transport
		ConnectionContext ctx = c.acceptConnection(transportId, encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testContactRemovedAfterInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise before removing contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Ensure the recogniser is initialised
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, new byte[IV_LENGTH]));
		assertTrue(c.isInitialised());
		// Remove the contact
		c.eventOccurred(new ContactRemovedEvent(contactId));
		// The IV should not be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		context.assertIsSatisfied();
	}

	@Test
	public void testContactRemovedBeforeInit() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise after removing contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.emptyList()));
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Remove the contact
		c.eventOccurred(new ContactRemovedEvent(contactId));
		// The IV should not be expected
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, encryptedIv));
		assertTrue(c.isInitialised());
		context.assertIsSatisfied();
	}

	@Test
	public void testLocalTransportAddedAfterInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise before adding transport
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(Collections.emptyList()));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			// Add the transport
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// The IV should not be expected
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, encryptedIv));
		assertTrue(c.isInitialised());
		// Add the transport
		c.eventOccurred(new TransportAddedEvent(transportId));
		// The IV should be expected
		ConnectionContext ctx = c.acceptConnection(transportId, encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testLocalTransportAddedBeforeInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise after adding transport
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Add the transport
		c.eventOccurred(new TransportAddedEvent(transportId));
		// The IV should be expected
		assertFalse(c.isInitialised());
		ConnectionContext ctx = c.acceptConnection(transportId, encryptedIv);
		assertTrue(c.isInitialised());
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportAddedAfterInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise before updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(null));
			// Update the contact
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// The IV should not be expected
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, encryptedIv));
		assertTrue(c.isInitialised());
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				remoteTransports));
		// The IV should be expected
		ConnectionContext ctx = c.acceptConnection(transportId, encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportAddedBeforeInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise after updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				remoteTransports));
		// The IV should be expected
		assertFalse(c.isInitialised());
		ConnectionContext ctx = c.acceptConnection(transportId, encryptedIv);
		assertTrue(c.isInitialised());
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportRemovedAfterInit() throws Exception {
		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise before updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Ensure the recogniser is initialised
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, new byte[IV_LENGTH]));
		assertTrue(c.isInitialised());
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				Collections.<Transport>emptyList()));
		// The IV should not be expected
		assertNull(c.acceptConnection(transportId, encryptedIv));
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportRemovedBeforeInit() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise after updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(null));
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				Collections.<Transport>emptyList()));
		// The IV should not be expected
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, encryptedIv));
		assertTrue(c.isInitialised());
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportIndexChangedAfterInit() throws Exception {
		// The contact changes the transport ID <-> index relationships
		final TransportId transportId1 =
			new TransportId(TestUtils.getRandomId());
		final TransportIndex remoteIndex1 = new TransportIndex(11);
		Map<String, String> properties = Collections.singletonMap("foo", "bar");
		Transport remoteTransport = new Transport(transportId, remoteIndex1,
				properties);
		Transport remoteTransport1 = new Transport(transportId1, remoteIndex,
				properties);
		Collection<Transport> remoteTransports1 = Arrays.asList(
				new Transport[] {remoteTransport, remoteTransport1});
		// Use two local transports for this test
		TransportIndex localIndex1 = new TransportIndex(17);
		Transport localTransport = new Transport(transportId, localIndex,
				properties);
		Transport localTransport1 = new Transport(transportId1, localIndex1,
				properties);
		final Collection<Transport> localTransports1 = Arrays.asList(
				new Transport[] {localTransport, localTransport1});

		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		final ConnectionWindow window1 = createConnectionWindow(remoteIndex1);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise before updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports1));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			// First, transportId <-> remoteIndex, transportId1 <-> remoteIndex
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).getRemoteIndex(contactId, transportId1);
			will(returnValue(remoteIndex1));
			oneOf(db).getConnectionWindow(contactId, remoteIndex1);
			will(returnValue(window1));
			// Later, transportId <-> remoteIndex1, transportId1 <-> remoteIndex
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).getConnectionWindow(contactId, remoteIndex1);
			will(returnValue(window1));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Ensure the recogniser is initialised
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, new byte[IV_LENGTH]));
		assertTrue(c.isInitialised());
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				remoteTransports1));
		// The IV should not be expected by the old transport
		assertNull(c.acceptConnection(transportId, encryptedIv));
		// The IV should be expected by the new transport
		ConnectionContext ctx = c.acceptConnection(transportId1, encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId1, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testRemoteTransportIndexChangedBeforeInit() throws Exception {
		// The contact changes the transport ID <-> index relationships
		final TransportId transportId1 =
			new TransportId(TestUtils.getRandomId());
		final TransportIndex remoteIndex1 = new TransportIndex(11);
		Map<String, String> properties = Collections.singletonMap("foo", "bar");
		Transport remoteTransport = new Transport(transportId, remoteIndex1,
				properties);
		Transport remoteTransport1 = new Transport(transportId1, remoteIndex,
				properties);
		Collection<Transport> remoteTransports1 = Arrays.asList(
				new Transport[] {remoteTransport, remoteTransport1});
		// Use two local transports for this test
		TransportIndex localIndex1 = new TransportIndex(17);
		Transport localTransport = new Transport(transportId, localIndex,
				properties);
		Transport localTransport1 = new Transport(transportId1, localIndex1,
				properties);
		final Collection<Transport> localTransports1 = Arrays.asList(
				new Transport[] {localTransport, localTransport1});

		final ConnectionWindow window = createConnectionWindow(remoteIndex);
		final ConnectionWindow window1 = createConnectionWindow(remoteIndex1);
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			// Initialise after updating the contact
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getLocalTransports();
			will(returnValue(localTransports1));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			// First, transportId <-> remoteIndex1, transportId1 <-> remoteIndex
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex1));
			oneOf(db).getConnectionWindow(contactId, remoteIndex1);
			will(returnValue(window1));
			oneOf(db).getRemoteIndex(contactId, transportId1);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(window));
			oneOf(db).setConnectionWindow(contactId, remoteIndex, window);
		}});
		Executor executor = new ImmediateExecutor();
		ConnectionRecogniserImpl c = new ConnectionRecogniserImpl(crypto, db,
				executor);
		byte[] encryptedIv = calculateIv();
		// Update the contact
		c.eventOccurred(new RemoteTransportsUpdatedEvent(contactId,
				remoteTransports1));
		// The IV should not be expected by the old transport
		assertFalse(c.isInitialised());
		assertNull(c.acceptConnection(transportId, encryptedIv));
		assertTrue(c.isInitialised());
		// The IV should be expected by the new transport
		ConnectionContext ctx = c.acceptConnection(transportId1, encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3, ctx.getConnectionNumber());
		// The IV should no longer be expected
		assertNull(c.acceptConnection(transportId1, encryptedIv));
		// The window should have advanced
		Map<Long, byte[]> unseen = window.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			assertEquals(i != 3, unseen.containsKey(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}

	private ConnectionWindow createConnectionWindow(TransportIndex index) {
		return new ConnectionWindowImpl(crypto, index, inSecret) {
			@Override
			public void erase() {}
		};
	}

	private byte[] calculateIv() throws Exception {
		// Calculate the shared secret for connection number 3
		byte[] secret = inSecret;
		for(int i = 0; i < 4; i++) {
			secret = crypto.deriveNextSecret(secret, remoteIndex.getInt(), i);
		}
		// Calculate the expected IV for connection number 3
		ErasableKey ivKey = crypto.deriveIvKey(secret, true);
		Cipher ivCipher = crypto.getIvCipher();
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] iv = IvEncoder.encodeIv(true, remoteIndex.getInt(), 3);
		return ivCipher.doFinal(iv);
	}
}
