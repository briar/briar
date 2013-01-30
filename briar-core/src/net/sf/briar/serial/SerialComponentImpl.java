package net.sf.briar.serial;

import net.sf.briar.api.messaging.UniqueId;
import net.sf.briar.api.serial.SerialComponent;

class SerialComponentImpl implements SerialComponent {

	public int getSerialisedListEndLength() {
		// END tag
		return 1;
	}

	public int getSerialisedListStartLength() {
		// LIST tag
		return 1;
	}

	public int getSerialisedStructIdLength(int id) {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		return id < 32 ? 1 : 2;
	}

	public int getSerialisedUniqueIdLength() {
		// BYTES tag, length spec, bytes
		return 1 + getSerialisedLengthSpecLength(UniqueId.LENGTH)
		+ UniqueId.LENGTH;
	}

	private int getSerialisedLengthSpecLength(int length) {
		if(length < 0) throw new IllegalArgumentException();
		if(length < 128) return 1; // Uint7
		if(length < Short.MAX_VALUE) return 3; // Int16
		return 5; // Int32
	}
}
