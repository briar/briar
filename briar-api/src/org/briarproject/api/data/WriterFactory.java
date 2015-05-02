package org.briarproject.api.data;

import java.io.OutputStream;

public interface WriterFactory {

	Writer createWriter(OutputStream out);
}
