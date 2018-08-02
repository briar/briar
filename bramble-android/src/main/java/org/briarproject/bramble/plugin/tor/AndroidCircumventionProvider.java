package org.briarproject.bramble.plugin.tor;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;

import java.io.InputStream;

import javax.inject.Inject;

class AndroidCircumventionProvider extends CircumventionProviderImpl {

	private final Context appContext;

	@Inject
	AndroidCircumventionProvider(Application app) {
		this.appContext = app.getApplicationContext();
	}

	@Override
	protected InputStream getResourceInputStream(String name) {
		Resources res = appContext.getResources();
		int resId = res.getIdentifier(name, "raw", appContext.getPackageName());
		return res.openRawResource(resId);
	}
}
