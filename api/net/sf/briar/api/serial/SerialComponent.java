package net.sf.briar.api.serial;

public interface SerialComponent {

	int getSerialisedListEndLength();

	int getSerialisedListStartLength();

	int getSerialisedStructIdLength(int id);

	int getSerialisedUniqueIdLength();
}
