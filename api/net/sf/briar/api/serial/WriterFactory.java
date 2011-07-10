package net.sf.briar.api.serial;

import java.io.OutputStream;

public interface WriterFactory {

	Writer createWriter(OutputStream out);
}
