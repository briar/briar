package org.briarproject.briar.api.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TestDataCreator {

	/* Creates fake test data on the DatabaseExecutor */
	void createTestData();

}
