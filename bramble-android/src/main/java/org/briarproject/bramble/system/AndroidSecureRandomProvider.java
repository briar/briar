package org.briarproject.bramble.system;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.StrictMode;
import android.provider.Settings;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.Secure.ANDROID_ID;

@Immutable
@NotNullByDefault
class AndroidSecureRandomProvider extends UnixSecureRandomProvider {

	private static final int SEED_LENGTH = 32;

	private final Context appContext;

	@Inject
	AndroidSecureRandomProvider(Application app) {
		appContext = app.getApplicationContext();
	}

	@SuppressLint("HardwareIds")
	@Override
	protected void writeToEntropyPool(DataOutputStream out) throws IOException {
		super.writeToEntropyPool(out);
		out.writeInt(android.os.Process.myPid());
		out.writeInt(android.os.Process.myTid());
		out.writeInt(android.os.Process.myUid());
		if (Build.FINGERPRINT != null) out.writeUTF(Build.FINGERPRINT);
		if (Build.SERIAL != null) out.writeUTF(Build.SERIAL);
		ContentResolver contentResolver = appContext.getContentResolver();
		String id = Settings.Secure.getString(contentResolver, ANDROID_ID);
		if (id != null) out.writeUTF(id);
		Parcel parcel = Parcel.obtain();
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt != null) {
			for (BluetoothDevice device : bt.getBondedDevices())
				parcel.writeParcelable(device, 0);
		}
		out.write(parcel.marshall());
		parcel.recycle();
	}

	@Override
	protected void writeSeed() {
		// Silence strict mode
		StrictMode.ThreadPolicy tp = StrictMode.allowThreadDiskWrites();
		super.writeSeed();
		if (SDK_INT <= 18) applyOpenSslFix();
		StrictMode.setThreadPolicy(tp);
	}

	// Based on https://android-developers.googleblog.com/2013/08/some-securerandom-thoughts.html
	private void applyOpenSslFix() {
		byte[] seed = new UnixSecureRandomSpi().engineGenerateSeed(
				SEED_LENGTH);
		try {
			// Seed the OpenSSL PRNG
			Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
					.getMethod("RAND_seed", byte[].class)
					.invoke(null, (Object) seed);
			// Mix the output of the Linux PRNG into the OpenSSL PRNG
			int bytesRead = (Integer) Class.forName(
					"org.apache.harmony.xnet.provider.jsse.NativeCrypto")
					.getMethod("RAND_load_file", String.class, long.class)
					.invoke(null, "/dev/urandom", 1024);
			if (bytesRead != 1024) throw new IOException();
		} catch (Exception e) {
			throw new SecurityException(e);
		}
	}
}
