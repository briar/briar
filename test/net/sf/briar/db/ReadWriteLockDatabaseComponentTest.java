package net.sf.briar.db;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.Batch;

import com.google.inject.Provider;

public class ReadWriteLockDatabaseComponentTest extends DatabaseComponentTest {

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<Batch> batchProvider) {
		return new ReadWriteLockDatabaseComponent<T>(database, cleaner,
				batchProvider);
	}
}
