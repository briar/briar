package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.PROP_PATH;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@Immutable
@NotNullByDefault
class RemovableDrivePlugin extends AbstractRemovableDrivePlugin {

	RemovableDrivePlugin(int maxLatency) {
		super(maxLatency);
	}

	@Override
	InputStream openInputStream(TransportProperties p) throws IOException {
		String path = p.get(PROP_PATH);
		if (isNullOrEmpty(path)) throw new IllegalArgumentException();
		return new FileInputStream(path);
	}

	@Override
	OutputStream openOutputStream(TransportProperties p) throws IOException {
		String path = p.get(PROP_PATH);
		if (isNullOrEmpty(path)) throw new IllegalArgumentException();
		return new FileOutputStream(path);
	}
}
