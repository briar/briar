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
		// BYTES tag, 32-bit length, bytes
		return 5 + UniqueId.LENGTH;
	}
}
