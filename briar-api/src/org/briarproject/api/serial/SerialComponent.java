package org.briarproject.api.serial;

public interface SerialComponent {

	int getSerialisedListStartLength();

	int getSerialisedListEndLength();

	int getSerialisedStructStartLength(int id);

	int getSerialisedStructEndLength();

	int getSerialisedUniqueIdLength();
}
