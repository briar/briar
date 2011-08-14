package net.sf.briar.transport;

import java.util.Collection;
import java.util.Collections;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import junit.framework.TestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
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
	private final byte[] secret;
	private final int transportId;
	private final ConnectionWindow connectionWindow;

	public ConnectionRecogniserImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		contactId = new ContactId(1);
		secret = new byte[18];
		transportId = 123;
		connectionWindow = new ConnectionWindowImpl(0L, 0);
	}

	@Test
	public void testUnexpectedTag() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getContacts();
			will(returnValue(Collections.singleton(contactId)));
			oneOf(db).getSharedSecret(contactId);
			will(returnValue(secret));
			oneOf(db).getConnectionWindow(contactId, transportId);
			will(returnValue(connectionWindow));
		}});
		final ConnectionRecogniserImpl c =
			new ConnectionRecogniserImpl(transportId, crypto, db);
		assertNull(c.acceptConnection(new byte[Constants.TAG_BYTES]));
		context.assertIsSatisfied();
	}

	@Test
	public void testExpectedTag() throws Exception {
		// Calculate the expected tag for connection number 3
		SecretKey tagKey = crypto.deriveIncomingTagKey(secret);
		Cipher tagCipher = crypto.getTagCipher();
		tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
		byte[] tag = TagEncoder.encodeTag(transportId, 3L, 0);
		byte[] encryptedTag = tagCipher.doFinal(tag);

		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		context.checking(new Expectations() {{
			oneOf(db).addListener(with(any(ConnectionRecogniserImpl.class)));
			oneOf(db).getContacts();
			will(returnValue(Collections.singleton(contactId)));
			oneOf(db).getSharedSecret(contactId);
			will(returnValue(secret));
			oneOf(db).getConnectionWindow(contactId, transportId);
			will(returnValue(connectionWindow));
			oneOf(db).setConnectionWindow(contactId, transportId,
					connectionWindow);
		}});
		final ConnectionRecogniserImpl c =
			new ConnectionRecogniserImpl(transportId, crypto, db);
		// First time - the tag should be expected
		assertEquals(contactId, c.acceptConnection(encryptedTag));
		// Second time - the tag should no longer be expected
		assertNull(c.acceptConnection(encryptedTag));
		// The window should have advanced
		assertEquals(4L, connectionWindow.getCentre());
		Collection<Long> unseen = connectionWindow.getUnseenConnectionNumbers();
		assertEquals(19, unseen.size());
		for(int i = 0; i < 19; i++) {
			if(i == 3) continue;
			assertTrue(unseen.contains(Long.valueOf(i)));
		}
		context.assertIsSatisfied();
	}
}
