package net.sf.briar.api.protocol;

import net.sf.briar.api.serial.Writable;

public interface Author extends Writable {

	AuthorId getId();

	String getName();

	byte[] getPublicKey();
}
