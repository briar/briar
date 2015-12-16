package org.briarproject.identity;

import com.google.inject.Inject;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;

class IdentityManagerImpl implements IdentityManager {

	private final DatabaseComponent db;

	@Inject
	IdentityManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public void addLocalAuthor(LocalAuthor a) throws DbException {
		db.addLocalAuthor(a);
	}

	@Override
	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		return db.getLocalAuthor(a);
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		return db.getLocalAuthors();
	}

	@Override
	public void removeLocalAuthor(AuthorId a) throws DbException {
		db.removeLocalAuthor(a);
	}
}
