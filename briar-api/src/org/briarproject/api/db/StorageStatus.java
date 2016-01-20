package org.briarproject.api.db;

public enum StorageStatus {

	ADDING(0), ACTIVE(1), REMOVING(2);

	private final int value;

	StorageStatus(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static StorageStatus fromValue(int value) {
		for (StorageStatus s : values()) if (s.value == value) return s;
		throw new IllegalArgumentException();
	}
}
