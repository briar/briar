package org.briarproject.bramble.system;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandomSpi;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

public class UnixSecureRandomSpi extends SecureRandomSpi {

	private static final Logger LOG =
			getLogger(UnixSecureRandomSpi.class.getName());

	private static final File RANDOM_DEVICE = new File("/dev/urandom");

	private final File inputDevice, outputDevice;

	@SuppressWarnings("WeakerAccess")
	public UnixSecureRandomSpi() {
		this(RANDOM_DEVICE, RANDOM_DEVICE);
	}

	UnixSecureRandomSpi(File inputDevice, File outputDevice) {
		this.inputDevice = inputDevice;
		this.outputDevice = outputDevice;
	}

	@Override
	protected void engineSetSeed(byte[] seed) {
		try {
			DataOutputStream out = new DataOutputStream(
					new FileOutputStream(outputDevice));
			out.write(seed);
			out.flush();
			out.close();
		} catch (IOException e) {
			// On some devices /dev/urandom isn't writable - this isn't fatal
			logException(LOG, WARNING, e);
		}
	}

	@Override
	protected void engineNextBytes(byte[] bytes) {
		try {
			DataInputStream in = new DataInputStream(
					new FileInputStream(inputDevice));
			in.readFully(bytes);
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected byte[] engineGenerateSeed(int len) {
		byte[] seed = new byte[len];
		engineNextBytes(seed);
		return seed;
	}
}
