package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.Set;

import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class IncomingHeaderBuilder extends HeaderBuilderImpl {

	private byte[] sig = null;

	IncomingHeaderBuilder(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		super(keyPair, signature, messageDigest, writerFactory);
	}

	public void setSignature(byte[] sig) {
		this.sig = sig;
	}

	public Header build() throws IOException, GeneralSecurityException {
		if(sig == null) throw new IllegalStateException();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeList(acks);
		w.writeList(subs);
		w.writeMap(transports);
		byte[] signable = out.toByteArray();
		signature.initVerify(keyPair.getPublic());
		signature.update(signable);
		if(!signature.verify(sig)) throw new SignatureException();
		w.writeRaw(sig);
		w.close();
		byte[] raw = out.toByteArray();
		messageDigest.reset();
		messageDigest.update(raw);
		BundleId id = new BundleId(messageDigest.digest());
		Set<BatchId> ackSet = new HashSet<BatchId>(acks);
		Set<GroupId> subSet = new HashSet<GroupId>(subs);
		return new HeaderImpl(id, raw.length, ackSet, subSet, transports, sig);
	}
}
