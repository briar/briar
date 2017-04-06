package org.briarproject.bramble.system;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Parcel;
import android.provider.Settings;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.content.Context.WIFI_SERVICE;
import static android.provider.Settings.Secure.ANDROID_ID;

@Immutable
@NotNullByDefault
class AndroidSecureRandomProvider extends LinuxSecureRandomProvider {

	private static final int SEED_LENGTH = 32;

	private final Context appContext;

	@Inject
	AndroidSecureRandomProvider(Application app) {
		appContext = app.getApplicationContext();
	}

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
		WifiManager wm =
				(WifiManager) appContext.getSystemService(WIFI_SERVICE);
		List<WifiConfiguration> configs = wm.getConfiguredNetworks();
		if (configs != null) {
			for (WifiConfiguration config : configs)
				parcel.writeParcelable(config, 0);
		}
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
		super.writeSeed();
		if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 18)
			applyOpenSslFix();
	}

	// Based on https://android-developers.googleblog.com/2013/08/some-securerandom-thoughts.html
	private void applyOpenSslFix() {
		byte[] seed = new LinuxSecureRandomSpi().engineGenerateSeed(
				SEED_LENGTH);
		try {
			// Seed the OpenSSL PRNG
			Class.forName("org.apache.harmony.xnet.provider.jsse.NativeCrypto")
					.getMethod("RAND_seed", byte[].class)
					.invoke(null, seed);
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
