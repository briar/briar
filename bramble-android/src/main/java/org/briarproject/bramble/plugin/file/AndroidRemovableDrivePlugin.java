package org.briarproject.bramble.plugin.file;

import android.app.Application;
import android.net.Uri;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.plugin.RemovableDriveConstants.PROP_URI;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@Immutable
@NotNullByDefault
class AndroidRemovableDrivePlugin extends RemovableDrivePlugin {

	private final Application app;

	AndroidRemovableDrivePlugin(Application app, int maxLatency) {
		super(maxLatency);
		this.app = app;
	}

	@Override
	InputStream openInputStream(TransportProperties p) throws IOException {
		String uri = p.get(PROP_URI);
		if (isNullOrEmpty(uri)) throw new IllegalArgumentException();
		return app.getContentResolver().openInputStream(Uri.parse(uri));
	}

	@Override
	OutputStream openOutputStream(TransportProperties p) throws IOException {
		String uri = p.get(PROP_URI);
		if (isNullOrEmpty(uri)) throw new IllegalArgumentException();
		return app.getContentResolver().openOutputStream(Uri.parse(uri));
	}
}
