package org.briarproject.bramble.system;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Parcel;
import android.os.StrictMode;
import android.provider.Settings;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static android.os.Build.FINGERPRINT;
import static android.os.Build.SERIAL;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Process.myPid;
import static android.os.Process.myTid;
import static android.os.Process.myUid;
import static android.provider.Settings.Secure.ANDROID_ID;

@Immutable
@NotNullByDefault
class AndroidSecureRandomProvider extends UnixSecureRandomProvider {

	private final Context appContext;

	@Inject
	AndroidSecureRandomProvider(Application app) {
		appContext = app.getApplicationContext();
	}

	@SuppressLint("HardwareIds")
	@Override
	protected void writeToEntropyPool(DataOutputStream out) throws IOException {
		super.writeToEntropyPool(out);
		out.writeInt(myPid());
		out.writeInt(myTid());
		out.writeInt(myUid());
		if (FINGERPRINT != null) out.writeUTF(FINGERPRINT);
		if (SERIAL != null) out.writeUTF(SERIAL);
		ContentResolver contentResolver = appContext.getContentResolver();
		String id = Settings.Secure.getString(contentResolver, ANDROID_ID);
		if (id != null) out.writeUTF(id);
		// On API 31 and higher we need permission to access bonded devices
		if (SDK_INT < 31) {
			Parcel parcel = Parcel.obtain();
			BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
			if (bt != null) {
				@SuppressLint("MissingPermission")
				Set<BluetoothDevice> deviceSet = bt.getBondedDevices();
				for (BluetoothDevice device : deviceSet)
					parcel.writeParcelable(device, 0);
			}
			out.write(parcel.marshall());
			parcel.recycle();
		}
	}

	@Override
	protected void writeSeed() {
		// Silence strict mode
		StrictMode.ThreadPolicy tp = StrictMode.allowThreadDiskWrites();
		super.writeSeed();
		StrictMode.setThreadPolicy(tp);
	}
}
