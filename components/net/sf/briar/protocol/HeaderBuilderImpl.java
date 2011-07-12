package net.sf.briar.protocol;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.serial.WriterFactory;

abstract class HeaderBuilderImpl implements HeaderBuilder {

	protected final List<BatchId> acks = new ArrayList<BatchId>();
	protected final List<GroupId> subs = new ArrayList<GroupId>();
	protected final Map<String, String> transports =
		new LinkedHashMap<String, String>();

	protected final KeyPair keyPair;
	protected final Signature signature;
	protected final MessageDigest messageDigest;
	protected final WriterFactory writerFactory;

	protected HeaderBuilderImpl(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		this.keyPair = keyPair;
		this.signature = signature;
		this.messageDigest = messageDigest;
		this.writerFactory = writerFactory;
	}

	public void addAcks(Iterable<BatchId> acks) {
		for(BatchId ack : acks) this.acks.add(ack);
	}

	public void addSubscriptions(Iterable<GroupId> subs) {
		for(GroupId sub : subs) this.subs.add(sub);
	}

	public void addTransports(Map<String, String> transports) {
		for(String key : transports.keySet()) {
			this.transports.put(key, transports.get(key));
		}
	}
}
