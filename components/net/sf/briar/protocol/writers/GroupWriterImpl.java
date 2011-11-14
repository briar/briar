package net.sf.briar.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.GroupWriter;
import net.sf.briar.api.serial.Writer;

class GroupWriterImpl implements GroupWriter {

	public void writeGroup(Writer w, Group g) throws IOException {
		w.writeUserDefinedId(Types.GROUP);
		w.writeString(g.getName());
		byte[] publicKey = g.getPublicKey();
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
	}
}
