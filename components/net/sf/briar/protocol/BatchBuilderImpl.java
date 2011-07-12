package net.sf.briar.protocol;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;

import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.serial.WriterFactory;

abstract class BatchBuilderImpl implements BatchBuilder {

	protected final List<Message> messages = new ArrayList<Message>();
	protected final KeyPair keyPair;
	protected final Signature signature;
	protected final MessageDigest messageDigest;
	protected final WriterFactory writerFactory;

	protected BatchBuilderImpl(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		this.keyPair = keyPair;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.writerFactory = writerFactory;
	}

	public void addMessage(Message m) {
		messages.add(m);
	}
}
