package org.briarproject.system;

import android.os.Build;

import java.io.DataOutputStream;
import java.io.IOException;

class AndroidSeedProvider extends LinuxSeedProvider {

	@Override
	void writeToEntropyPool(DataOutputStream out) throws IOException {
		out.writeInt(android.os.Process.myPid());
		out.writeInt(android.os.Process.myTid());
		out.writeInt(android.os.Process.myUid());
		if (Build.FINGERPRINT != null) out.writeUTF(Build.FINGERPRINT);
		if (Build.SERIAL != null) out.writeUTF(Build.SERIAL);
		super.writeToEntropyPool(out);
	}
}
