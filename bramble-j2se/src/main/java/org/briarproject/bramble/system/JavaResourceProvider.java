package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.InputStream;

import javax.inject.Inject;

@NotNullByDefault
class JavaResourceProvider implements ResourceProvider {

	@Inject
	JavaResourceProvider() {
	}

	@Override
	public InputStream getResourceInputStream(String name, String extension) {
		return getClass().getClassLoader()
				.getResourceAsStream(name + extension);
	}
}
