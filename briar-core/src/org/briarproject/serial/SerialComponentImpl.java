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
		// STRUCT tag, ID
		return 2;
	}

	public int getSerialisedStructEndLength() {
		// END tag
		return 1;
	}

	public int getSerialisedUniqueIdLength() {
		// BYTES tag, length spec, bytes
		return 1 + getSerialisedLengthSpecLength(UniqueId.LENGTH)
				+ UniqueId.LENGTH;
	}

	private int getSerialisedLengthSpecLength(int length) {
		if(length < 0) throw new IllegalArgumentException();
		// Uint7, int16 or int32
		return length <= Byte.MAX_VALUE ? 1 : length <= Short.MAX_VALUE ? 3 : 5;
	}
}
