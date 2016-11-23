package org.briarproject.bramble.system;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.provider.Settings.Secure.ANDROID_ID;

@Immutable
@NotNullByDefault
class AndroidSeedProvider extends LinuxSeedProvider {

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
		String id = Settings.Secure.getString(contentResolver, ANDROID_ID);
		if (id != null) out.writeUTF(id);
		super.writeToEntropyPool(out);
	}
}
