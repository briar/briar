package net.sf.briar.serial;

import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.SerialComponent;

class SerialComponentImpl implements SerialComponent {

	public int getSerialisedListEndLength() {
		return 1;
	}

	public int getSerialisedListStartLength() {
		return 1;
	}

	public int getSerialisedUniqueIdLength(int id) {
		// User-defined ID, BYTES tag, length spec, bytes
		return getSerialisedUserDefinedIdLength(id) + 1
		+ getSerialisedLengthSpecLength(UniqueId.LENGTH) + UniqueId.LENGTH;
	}

	private int getSerialisedLengthSpecLength(int length) {
		assert length >= 0;
		if(length < 128) return 1; // Uint7
		if(length < Short.MAX_VALUE) return 3; // Int16
		return 5; // Int32
	}

	public int getSerialisedUserDefinedIdLength(int id) {
		assert id >= 0 && id <= 255;
		return id < 32 ? 1 : 2;
	}
}
