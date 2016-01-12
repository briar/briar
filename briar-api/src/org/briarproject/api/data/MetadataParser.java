package org.briarproject.api.data;

import org.briarproject.api.FormatException;
import org.briarproject.api.db.Metadata;

public interface MetadataParser {

	BdfDictionary parse(Metadata m) throws FormatException;
}
