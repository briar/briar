package org.briarproject.bramble.system;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.InputStream;

import javax.inject.Inject;

@NotNullByDefault
class AndroidResourceProvider implements ResourceProvider {

	private final Context appContext;

	@Inject
	AndroidResourceProvider(Application app) {
		this.appContext = app.getApplicationContext();
	}

	@Override
	public InputStream getResourceInputStream(String name, String extension) {
		Resources res = appContext.getResources();
		// extension is ignored on Android, resources are retrieved without it
		int resId =
				res.getIdentifier(name, "raw", appContext.getPackageName());
		return res.openRawResource(resId);
	}
}
