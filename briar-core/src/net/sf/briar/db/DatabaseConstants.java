package net.sf.briar.db;

interface DatabaseConstants {

	// FIXME: These should be configurable

	/**
	 * The minimum amount of space in bytes that should be kept free on the
	 * device where the database is stored. Whenever less than this much space
	 * is free, old messages will be expired from the database.
	 */
	long MIN_FREE_SPACE = 300 * 1024 * 1024; // 300 MiB

	/**
	 * The minimum amount of space in bytes that must be kept free on the device
	 * where the database is stored. If less than this much space is free and
	 * there are no more messages to expire, an Error will be thrown.
	 */
	long CRITICAL_FREE_SPACE = 100 * 1024 * 1024; // 100 MiB

	/**
	 * The amount of free space will be checked whenever this many bytes of
	 * messages have been added to the database since the last check.
	 */
	int MAX_BYTES_BETWEEN_SPACE_CHECKS = 5 * 1024 * 1024; // 5 MiB

	/**
	 * The amount of free space will be checked whenever this many milliseconds
	 * have passed since the last check.
	 */
	long MAX_MS_BETWEEN_SPACE_CHECKS = 60 * 1000; // 1 min

	/**
	 * Up to this many bytes of messages will be expired from the database each
	 * time it is necessary to expire messages.
	 */
	int BYTES_PER_SWEEP = 5 * 1024 * 1024; // 5 MiB

	/**
	 * The timestamp of the oldest message in the database is rounded using
	 * this modulus to avoid revealing the presence of any particular message.
	 */
	long RETENTION_MODULUS = 60 * 60 * 1000; // 1 hour

	/**
	 * The time in milliseconds after which a subscription or transport update
	 * should be sent to a contact even if no changes have occurred.
	 */
	long MAX_UPDATE_INTERVAL = 12 * 60 * 60 * 1000; // 12 hours
}
