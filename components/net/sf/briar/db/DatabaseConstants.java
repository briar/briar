package net.sf.briar.db;

interface DatabaseConstants {

	// FIXME: These should be configurable

	/**
	 * The minimum amount of space in bytes that should be kept free on the
	 * device where the database is stored. Whenever less than this much space
	 * is free, old messages will be expired from the database.
	 */
	static final long MIN_FREE_SPACE = 300 * 1024 * 1024; // 300 MiB

	/**
	 * The minimum amount of space in bytes that must be kept free on the device
	 * where the database is stored. If less than this much space is free and
	 * there are no more messages to expire, the program will shut down.
	 */
	static final long CRITICAL_FREE_SPACE = 100 * 1024 * 1024; // 100 MiB

	/**
	 * The amount of free space will be checked whenever this many bytes of
	 * messages have been added to the database since the last check.
	 */
	static final int MAX_BYTES_BETWEEN_SPACE_CHECKS = 5 * 1024 * 1024; // 5 MiB

	/**
	 * The amount of free space will be checked whenever this many milliseconds
	 * have passed since the last check.
	 */
	static final long MAX_MS_BETWEEN_SPACE_CHECKS = 60L * 1000L; // 1 min

	/**
	 * Up to this many bytes of messages will be expired from the database each
	 * time it is necessary to expire messages.
	 */
	static final int BYTES_PER_SWEEP = 5 * 1024 * 1024; // 5 MiB

	/**
	 * The timestamp of the oldest message in the database is rounded using
	 * this modulus to avoid revealing the presence of any particular message.
	 */
	static final long EXPIRY_MODULUS = 60L * 60L * 1000L; // 1 hour

	/**
	 * A batch sent to a contact is considered lost when this many more
	 * recently sent batches have been acknowledged.
	 */
	static final int RETRANSMIT_THRESHOLD = 5;
}
