package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface ResourceProvider {

	InputStream getResourceInputStream(String name, String extension);
}
