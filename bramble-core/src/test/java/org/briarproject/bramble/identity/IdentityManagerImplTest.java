package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getIdentity;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);
	private final Clock clock = context.mock(Clock.class);

	private final Transaction txn = new Transaction(null, false);
	private final Identity identityWithKeys = getIdentity();
	private final LocalAuthor localAuthor = identityWithKeys.getLocalAuthor();
	private final Identity identityWithoutKeys = new Identity(localAuthor,
			null, null, identityWithKeys.getTimeCreated());
	private final PublicKey handshakePublicKey = getAgreementPublicKey();
	private final PrivateKey handshakePrivateKey = getAgreementPrivateKey();
	private final KeyPair handshakeKeyPair =
			new KeyPair(handshakePublicKey, handshakePrivateKey);

	private IdentityManagerImpl identityManager;

	@Before
	public void setUp() {
		identityManager =
				new IdentityManagerImpl(db, crypto, authorFactory, clock);
	}

	@Test
	public void testOpenDatabaseIdentityRegistered() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).addIdentity(txn, identityWithKeys);
		}});

		identityManager.registerIdentity(identityWithKeys);
		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHandshakeKeysGenerated() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getIdentities(txn);
			will(returnValue(singletonList(identityWithoutKeys)));
			oneOf(crypto).generateAgreementKeyPair();
			will(returnValue(handshakeKeyPair));
			oneOf(db).setHandshakeKeyPair(txn, localAuthor.getId(),
					handshakePublicKey, handshakePrivateKey);
		}});

		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseNoHandshakeKeysGenerated() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getIdentities(txn);
			will(returnValue(singletonList(identityWithKeys)));
		}});

		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testGetLocalAuthorIdentityRegistered() throws DbException {
		identityManager.registerIdentity(identityWithKeys);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetLocalAuthorHandshakeKeysGenerated() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getIdentities(txn);
			will(returnValue(singletonList(identityWithoutKeys)));
			oneOf(crypto).generateAgreementKeyPair();
			will(returnValue(handshakeKeyPair));
		}});

		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetLocalAuthorNoHandshakeKeysGenerated() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getIdentities(txn);
			will(returnValue(singletonList(identityWithKeys)));
		}});

		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

}
