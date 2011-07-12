package net.sf.briar.db;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.HeaderBuilder;

import com.google.inject.Provider;

public class SynchronizedDatabaseComponentTest
extends DatabaseComponentImplTest {

	@Override
	protected <T> DatabaseComponent createDatabaseComponent(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) {
		return createDatabaseComponentImpl(database, cleaner,
				headerBuilderProvider, batchBuilderProvider);
	}

	@Override
	protected <T> DatabaseComponentImpl<T> createDatabaseComponentImpl(
			Database<T> database, DatabaseCleaner cleaner,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) {
		return new SynchronizedDatabaseComponent<T>(database, cleaner,
				headerBuilderProvider, batchBuilderProvider);
	}
}
