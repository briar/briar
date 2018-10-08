package org.briarproject.briar.api.logging;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Handler;

@NotNullByDefault
public interface PersistentLogManager {

	Handler createLogHandler(File dir) throws IOException;

	Collection<String> getPersistedLog(File dir) throws IOException;
}
