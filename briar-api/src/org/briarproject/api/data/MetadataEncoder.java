package org.briarproject.api.data;

import org.briarproject.api.FormatException;
import org.briarproject.api.db.Metadata;

public interface MetadataEncoder {

	Metadata encode(BdfDictionary d) throws FormatException;
}
