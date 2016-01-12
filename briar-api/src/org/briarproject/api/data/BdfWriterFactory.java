package org.briarproject.api.data;

import java.io.OutputStream;

public interface BdfWriterFactory {

	BdfWriter createWriter(OutputStream out);
}
