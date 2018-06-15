package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;
import org.spongycastle.crypto.generators.SCrypt;

import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

class ScryptKdf implements PasswordBasedKdf {

	private static final Logger LOG =
			Logger.getLogger(ScryptKdf.class.getName());

	private static final int MIN_COST = 256; // Min parameter N
	private static final int MAX_COST = 1024 * 1024; // Max parameter N
	private static final int BLOCK_SIZE = 8; // Parameter r
	private static final int PARALLELIZATION = 1; // Parameter p
	private static final int TARGET_MS = 1000;

	private final Clock clock;

	@Inject
	ScryptKdf(Clock clock) {
		this.clock = clock;
	}

	@Override
	public int chooseCostParameter() {
		// Increase the cost from min to max while measuring performance
		int cost = MIN_COST;
		while (cost * 2 <= MAX_COST && measureDuration(cost) * 2 <= TARGET_MS)
			cost *= 2;
		if (LOG.isLoggable(INFO))
			LOG.info("KDF cost parameter " + cost);
		return cost;
	}

	private long measureDuration(int cost) {
		byte[] password = new byte[16], salt = new byte[32];
		long start = clock.currentTimeMillis();
		SCrypt.generate(password, salt, cost, BLOCK_SIZE, PARALLELIZATION,
				SecretKey.LENGTH);
		return clock.currentTimeMillis() - start;
	}

	@Override
	public SecretKey deriveKey(String password, byte[] salt, int cost) {
		long start = System.currentTimeMillis();
		byte[] passwordBytes = StringUtils.toUtf8(password);
		SecretKey k = new SecretKey(SCrypt.generate(passwordBytes, salt, cost,
				BLOCK_SIZE, PARALLELIZATION, SecretKey.LENGTH));
		long duration = System.currentTimeMillis() - start;
		if (LOG.isLoggable(FINE))
			LOG.fine("Deriving key from password took " + duration + " ms");
		return k;
	}
}
