package org.briarproject.android.controller;

public class EncryptedKeyNullException extends NullPointerException {

	@Override
	public String toString() {
		return "Encrypted key can't be null";
	}
}
