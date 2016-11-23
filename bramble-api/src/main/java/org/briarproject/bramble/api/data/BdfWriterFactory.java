package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.OutputStream;

@NotNullByDefault
public interface BdfWriterFactory {

	BdfWriter createWriter(OutputStream out);
}
