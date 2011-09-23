package net.sf.briar.db;

interface DatabaseConstants {

	static final int MEGABYTE = 1024 * 1024;

	// FIXME: These should be configurable
	static final long MIN_FREE_SPACE = 300L * MEGABYTE;
	static final long CRITICAL_FREE_SPACE = 100L * MEGABYTE;
	static final int MAX_BYTES_BETWEEN_SPACE_CHECKS = 5 * MEGABYTE;
	static final long MAX_MS_BETWEEN_SPACE_CHECKS = 60L * 1000L; // 1 min
	static final int BYTES_PER_SWEEP = 5 * MEGABYTE;
	static final long EXPIRY_MODULUS = 60L * 60L * 1000L; // 1 hour
}
