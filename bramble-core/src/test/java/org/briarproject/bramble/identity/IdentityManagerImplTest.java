package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Account;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getAccount;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);
	private final Clock clock = context.mock(Clock.class);
	private final PublicKey handshakePublicKey = context.mock(PublicKey.class);
	private final PrivateKey handshakePrivateKey =
			context.mock(PrivateKey.class);

	private final Transaction txn = new Transaction(null, false);
	private final Account accountWithKeys = getAccount();
	private final LocalAuthor localAuthor = accountWithKeys.getLocalAuthor();
	private final Account accountWithoutKeys = new Account(localAuthor,
			null, null, accountWithKeys.getTimeCreated());
	private final KeyPair handshakeKeyPair =
			new KeyPair(handshakePublicKey, handshakePrivateKey);
	private final byte[] handshakePublicKeyBytes =
			accountWithKeys.getHandshakePublicKey();
	private final byte[] handshakePrivateKeyBytes =
			accountWithKeys.getHandshakePrivateKey();

	private IdentityManagerImpl identityManager;

	@Before
	public void setUp() {
		identityManager =
				new IdentityManagerImpl(db, crypto, authorFactory, clock);
	}

	@Test
	public void testOpenDatabaseHookAccountRegistered() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).addAccount(txn, accountWithKeys);
		}});

		identityManager.registerAccount(accountWithKeys);
		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookNoAccountRegisteredHandshakeKeys()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getAccounts(txn);
			will(returnValue(singletonList(accountWithKeys)));
		}});

		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookNoAccountRegisteredNoHandshakeKeys()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getAccounts(txn);
			will(returnValue(singletonList(accountWithoutKeys)));
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
	}

	@Test
	public void testGetLocalAuthor() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getAccounts(txn);
			will(returnValue(singletonList(accountWithKeys)));
		}});
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetCachedLocalAuthor() throws DbException {
		identityManager.registerAccount(accountWithKeys);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

}
