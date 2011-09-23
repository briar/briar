package net.sf.briar.db;

interface DatabaseConstants {

	// FIXME: These should be configurable
	static final long MIN_FREE_SPACE = 300 * 1024 * 1024; // 300 MiB
	static final long CRITICAL_FREE_SPACE = 100 * 1024 * 1024; // 100 MiB

	static final int MAX_BYTES_BETWEEN_SPACE_CHECKS = 5 * 1024 * 1024; // 5 MiB
	static final long MAX_MS_BETWEEN_SPACE_CHECKS = 60L * 1000L; // 1 min

	static final long MS_PER_SWEEP = 10L * 1000L; // 10 sec
	static final int BYTES_PER_SWEEP = 5 * 1024 * 1024; // 5 MiB

	static final long EXPIRY_MODULUS = 60L * 60L * 1000L; // 1 hour
}
