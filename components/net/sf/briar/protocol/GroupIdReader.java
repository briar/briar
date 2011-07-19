package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

public class GroupIdReader implements ObjectReader<GroupId> {

	public GroupId readObject(Reader r) throws IOException {
		byte[] b = r.readRaw();
		if(b.length != UniqueId.LENGTH) throw new FormatException();
		return new GroupId(b);
	}
}
