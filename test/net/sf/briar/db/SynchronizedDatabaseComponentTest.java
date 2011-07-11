package net.sf.briar.db;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.BatchBuilder;

import com.google.inject.Provider;

public class SynchronizedDatabaseComponentTest
extends DatabaseComponentImplTest {

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<BatchBuilder> batchBuilderProvider) {
		return createDatabaseComponentImpl(database, cleaner,
				batchBuilderProvider);
	}

	@Override
	protected <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<BatchBuilder> batchBuilderProvider) {
		return new SynchronizedDatabaseComponent<T>(database, cleaner,
				batchBuilderProvider);
	}
}
