package org.briarproject.system;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.provider.Settings.Secure.ANDROID_ID;

class AndroidSeedProvider extends LinuxSeedProvider {

	private static final Logger LOG =
			Logger.getLogger(LinuxSeedProvider.class.getName());

	private final Context appContext;

	@Inject
	AndroidSeedProvider(Application app) {
		appContext = app.getApplicationContext();
	}

	@Override
	void writeToEntropyPool(DataOutputStream out) throws IOException {
		out.writeInt(android.os.Process.myPid());
		out.writeInt(android.os.Process.myTid());
		out.writeInt(android.os.Process.myUid());
		if (Build.FINGERPRINT != null) out.writeUTF(Build.FINGERPRINT);
		if (Build.SERIAL != null) out.writeUTF(Build.SERIAL);
		ContentResolver contentResolver = appContext.getContentResolver();
		out.writeUTF(Settings.Secure.getString(contentResolver, ANDROID_ID));
		super.writeToEntropyPool(out);
	}
}
