package org.briarproject.api.data;

import java.io.InputStream;

public interface BdfReaderFactory {

	BdfReader createReader(InputStream in);
}
