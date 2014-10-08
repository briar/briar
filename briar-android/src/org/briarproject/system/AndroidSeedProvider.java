package org.briarproject.system;

import java.io.DataOutputStream;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.os.Build;

class AndroidSeedProvider extends LinuxSeedProvider {

	@Override
	@SuppressLint("NewApi")
	void writeToEntropyPool(DataOutputStream out) throws IOException {
		out.writeInt(android.os.Process.myPid());
		out.writeInt(android.os.Process.myTid());
		out.writeInt(android.os.Process.myUid());
		String fingerprint = Build.FINGERPRINT;
		if(fingerprint != null) out.writeUTF(fingerprint);
		if(Build.VERSION.SDK_INT >= 9) {
			String serial = Build.SERIAL;
			if(serial != null) out.writeUTF(serial);
		}
		super.writeToEntropyPool(out);
	}
}
