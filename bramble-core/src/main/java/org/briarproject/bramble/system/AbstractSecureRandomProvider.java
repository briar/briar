package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.SecureRandomProvider;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
abstract class AbstractSecureRandomProvider implements SecureRandomProvider {

	// Contribute whatever slightly unpredictable info we have to the pool
	protected void writeToEntropyPool(DataOutputStream out) throws IOException {
		out.writeLong(System.currentTimeMillis());
		out.writeLong(System.nanoTime());
		out.writeLong(Runtime.getRuntime().freeMemory());
		List<NetworkInterface> ifaces =
				Collections.list(NetworkInterface.getNetworkInterfaces());
		for (NetworkInterface i : ifaces) {
			List<InetAddress> addrs = Collections.list(i.getInetAddresses());
			for (InetAddress a : addrs) out.write(a.getAddress());
			byte[] hardware = i.getHardwareAddress();
			if (hardware != null) out.write(hardware);
		}
		for (Entry<String, String> e : System.getenv().entrySet()) {
			out.writeUTF(e.getKey());
			out.writeUTF(e.getValue());
		}
		Properties properties = System.getProperties();
		for (String key : properties.stringPropertyNames())
			out.writeUTF(properties.getProperty(key));
	}
}
