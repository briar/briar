package org.briarproject.api.serial;

import org.briarproject.api.UniqueId;

public interface SerialConstants {

	int LIST_START_LENGTH = 1;

	int LIST_END_LENGTH = 1;

	int UNIQUE_ID_LENGTH = 2 + UniqueId.LENGTH;
}
