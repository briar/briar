package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.junit.Assert.assertEquals;

public class IdentityManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final AuthorFactory authorFactory =
			context.mock(AuthorFactory.class);

	private final Transaction txn = new Transaction(null, false);
	private final LocalAuthor localAuthor = getLocalAuthor();
	private final Collection<LocalAuthor> localAuthors =
			singletonList(localAuthor);
	private IdentityManagerImpl identityManager;

	@Before
	public void setUp() {
		identityManager = new IdentityManagerImpl(db, authorFactory);
	}

	@Test
	public void testOpenDatabaseHookWithoutLocalAuthorRegistered()
			throws Exception {
		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testOpenDatabaseHookWithLocalAuthorRegistered()
			throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).addLocalAuthor(txn, localAuthor);
		}});

		identityManager.registerLocalAuthor(localAuthor);
		identityManager.onDatabaseOpened(txn);
	}

	@Test
	public void testGetLocalAuthor() throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(db).getLocalAuthors(txn);
			will(returnValue(localAuthors));
		}});
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

	@Test
	public void testGetCachedLocalAuthor() throws DbException {
		identityManager.registerLocalAuthor(localAuthor);
		assertEquals(localAuthor, identityManager.getLocalAuthor());
	}

}
