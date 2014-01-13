package org.briarproject.crypto;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import org.briarproject.api.crypto.SeedProvider;

class LinuxSeedProvider implements SeedProvider {

	public byte[] getSeed() {
		byte[] seed = new byte[SEED_BYTES];
		try {
			DataInputStream in =  new DataInputStream(
					new FileInputStream("/dev/urandom"));
			in.readFully(seed);
			in.close();
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
		return seed;
	}
}
