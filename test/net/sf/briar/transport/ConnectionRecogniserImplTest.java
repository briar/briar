package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import javax.crypto.Cipher;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.crypto.CryptoModule;

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
	private final Collection<Transport> transports;
	private final ConnectionWindow connectionWindow;

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
		Transport transport = new Transport(transportId, localIndex,
				Collections.singletonMap("foo", "bar"));
		transports = Collections.singletonList(transport);
		connectionWindow = new ConnectionWindowImpl();
	}

	@Test
	public void testUnexpectedIv() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			// Initialise
			oneOf(db).getLocalTransports();
			will(returnValue(transports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getSharedSecret(contactId, true);
			will(returnValue(inSecret));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(connectionWindow));
		}});
		final ConnectionRecogniserImpl c =
			new ConnectionRecogniserImpl(crypto, db);
		assertNull(c.acceptConnection(new byte[IV_LENGTH]));
		context.assertIsSatisfied();
	}

	@Test
	public void testExpectedIv() throws Exception {
		// Calculate the expected IV for connection number 3
		ErasableKey ivKey = crypto.deriveIvKey(inSecret, true);
		Cipher ivCipher = crypto.getIvCipher();
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] iv = IvEncoder.encodeIv(true, remoteIndex, 3L);
		byte[] encryptedIv = ivCipher.doFinal(iv);

		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			// Initialise
			oneOf(db).getLocalTransports();
			will(returnValue(transports));
			oneOf(db).getContacts();
			will(returnValue(Collections.singletonList(contactId)));
			oneOf(db).getSharedSecret(contactId, true);
			will(returnValue(inSecret));
			oneOf(db).getRemoteIndex(contactId, transportId);
			will(returnValue(remoteIndex));
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(connectionWindow));
			// Update the window
			oneOf(db).getConnectionWindow(contactId, remoteIndex);
			will(returnValue(connectionWindow));
			oneOf(db).setConnectionWindow(contactId, remoteIndex,
					connectionWindow);
			oneOf(db).getSharedSecret(contactId, true);
			will(returnValue(inSecret));
		}});
		final ConnectionRecogniserImpl c =
			new ConnectionRecogniserImpl(crypto, db);
		// First time - the IV should be expected
		ConnectionContext ctx = c.acceptConnection(encryptedIv);
		assertNotNull(ctx);
		assertEquals(contactId, ctx.getContactId());
		assertEquals(remoteIndex, ctx.getTransportIndex());
		assertEquals(3L, ctx.getConnectionNumber());
		// Second time - the IV should no longer be expected
		assertNull(c.acceptConnection(encryptedIv));
		// The window should have advanced
		Collection<Long> unseen = connectionWindow.getUnseen();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			if(i == 3) continue;
			assertTrue(unseen.contains(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}
}
