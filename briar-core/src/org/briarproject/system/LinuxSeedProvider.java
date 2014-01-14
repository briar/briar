package org.briarproject.system;

import static java.util.logging.Level.WARNING;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.briarproject.api.system.SeedProvider;

class LinuxSeedProvider implements SeedProvider {

	private static final Logger LOG =
			Logger.getLogger(LinuxSeedProvider.class.getName());

	private final String outputFile, inputFile;

	LinuxSeedProvider() {
		this("/dev/urandom", "/dev/urandom");
	}

	LinuxSeedProvider(String outputFile, String inputFile) {
		this.outputFile = outputFile;
		this.inputFile = inputFile;
	}

	public byte[] getSeed() {
		byte[] seed = new byte[SEED_BYTES];
		// Contribute whatever slightly unpredictable info we have to the pool
		try {
			DataOutputStream out = new DataOutputStream(
					new FileOutputStream(outputFile));
			writeToEntropyPool(out);
			out.flush();
			out.close();
		} catch(IOException e) {
			// On some devices /dev/urandom isn't writable - this isn't fatal
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		// Read the seed from the pool
		try {
			DataInputStream in =  new DataInputStream(
					new FileInputStream(inputFile));
			in.readFully(seed);
			in.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		return seed;
	}

	void writeToEntropyPool(DataOutputStream out) throws IOException {
		out.writeLong(System.currentTimeMillis());
		out.writeLong(System.nanoTime());
		List<NetworkInterface> ifaces =
				Collections.list(NetworkInterface.getNetworkInterfaces());
		for(NetworkInterface i : ifaces) {
			List<InetAddress> addrs = Collections.list(i.getInetAddresses());
			for(InetAddress a : addrs) out.write(a.getAddress());
		}
	}
}
