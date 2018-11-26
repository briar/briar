package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Provider;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@Immutable
@NotNullByDefault
class UnixSecureRandomProvider extends AbstractSecureRandomProvider {

	private static final Logger LOG =
			getLogger(UnixSecureRandomProvider.class.getName());

	private static final File RANDOM_DEVICE = new File("/dev/urandom");

	private final AtomicBoolean seeded = new AtomicBoolean(false);
	private final File outputDevice;

	UnixSecureRandomProvider() {
		this(RANDOM_DEVICE);
	}

	UnixSecureRandomProvider(File outputDevice) {
		this.outputDevice = outputDevice;
	}

	@Override
	public Provider getProvider() {
		if (!seeded.getAndSet(true)) writeSeed();
		return new UnixProvider();
	}

	protected void writeSeed() {
		try {
			DataOutputStream out = new DataOutputStream(
					new FileOutputStream(outputDevice));
			writeToEntropyPool(out);
			out.flush();
			out.close();
		} catch (IOException e) {
			// On some devices /dev/urandom isn't writable - this isn't fatal
			logException(LOG, WARNING, e);
		}
	}

	// Based on https://android-developers.googleblog.com/2013/08/some-securerandom-thoughts.html
	private static class UnixProvider extends Provider {

		private UnixProvider() {
			super("UnixPRNG", 1.0, "A Unix-specific PRNG using /dev/urandom");
			// Although /dev/urandom is not a SHA-1 PRNG, some callers
			// explicitly request a SHA1PRNG SecureRandom and we need to
			// prevent them from getting the default implementation whose
			// output may have low entropy.
			put("SecureRandom.SHA1PRNG", UnixSecureRandomSpi.class.getName());
			put("SecureRandom.SHA1PRNG ImplementedIn", "Software");
		}
	}
}