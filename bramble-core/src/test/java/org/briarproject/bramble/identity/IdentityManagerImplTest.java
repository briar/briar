package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);
	private final PublicKey handshakePublicKey = context.mock(PublicKey.class);
	private final PrivateKey handshakePrivateKey =
			context.mock(PrivateKey.class);

	private final Transaction txn = new Transaction(null, false);
	private final LocalAuthor localAuthor = getLocalAuthor(true);
	private final LocalAuthor localAuthorWithoutHandshakeKeys =
			new LocalAuthor(localAuthor.getId(), localAuthor.getFormatVersion(),
					localAuthor.getName(), localAuthor.getPublicKey(),
					localAuthor.getPrivateKey(), localAuthor.getTimeCreated());
	private final KeyPair handshakeKeyPair =
			new KeyPair(handshakePublicKey, handshakePrivateKey);
	private final byte[] handshakePublicKeyBytes =
			localAuthor.getHandshakePublicKey();
	private final byte[] handshakePrivateKeyBytes =
			localAuthor.getHandshakePrivateKey();

	private IdentityManagerImpl identityManager;

	@Before
	public void setUp() {
		identityManager = new IdentityManagerImpl(db, crypto, authorFactory);
	}

	@Test
	public void testOpenDatabaseHookLocalAuthorRegistered() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).addLocalAuthor(txn, localAuthor);
		}});

		identityManager.registerLocalAuthor(localAuthor);
		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookNoLocalAuthorRegisteredHandshakeKeys()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(singletonList(localAuthor)));
		}});

		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookNoLocalAuthorRegisteredNoHandshakeKeys()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(singletonList(localAuthorWithoutHandshakeKeys)));
			oneOf(crypto).generateAgreementKeyPair();
			will(returnValue(handshakeKeyPair));
			oneOf(handshakePublicKey).getEncoded();
			will(returnValue(handshakePublicKeyBytes));
			oneOf(handshakePrivateKey).getEncoded();
			will(returnValue(handshakePrivateKeyBytes));
			oneOf(db).setHandshakeKeyPair(txn, localAuthor.getId(),
					handshakePublicKeyBytes, handshakePrivateKeyBytes);
		}});

		identityManager.onDatabaseOpened(txn);

		LocalAuthor cached = identityManager.getLocalAuthor();
		assertArrayEquals(handshakePublicKeyBytes,
				cached.getHandshakePublicKey());
		assertArrayEquals(handshakePrivateKeyBytes,
				cached.getHandshakePrivateKey());
	}

	@Test
	public void testGetLocalAuthor() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(singletonList(localAuthor)));
		}});
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetCachedLocalAuthor() throws DbException {
		identityManager.registerLocalAuthor(localAuthor);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

}
