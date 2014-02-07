package org.briarproject.serial;

import org.briarproject.api.UniqueId;
import org.briarproject.api.serial.SerialComponent;

class SerialComponentImpl implements SerialComponent {

	public int getSerialisedListStartLength() {
		// LIST tag
		return 1;
	}

	public int getSerialisedListEndLength() {
		// END tag
		return 1;
	}

	public int getSerialisedStructStartLength(int id) {
		// STRUCT tag, 8-bit ID
		return 2;
	}

	public int getSerialisedStructEndLength() {
		// END tag
		return 1;
	}

	public int getSerialisedUniqueIdLength() {
		// BYTES_8, BYTES_16 or BYTES_32 tag, length, bytes
		return 1 + getLengthBytes(UniqueId.LENGTH) + UniqueId.LENGTH;
	}

	private int getLengthBytes(int length) {
		if(length <= Byte.MAX_VALUE) return 1;
		if(length <= Short.MAX_VALUE) return 2;
		return 4;
	}
}
