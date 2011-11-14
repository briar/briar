package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.serial.Writer;

public interface GroupWriter {

	void writeGroup(Writer w, Group g) throws IOException;
}
