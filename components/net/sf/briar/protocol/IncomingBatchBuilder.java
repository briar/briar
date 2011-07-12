package net.sf.briar.protocol;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.serial.WriterFactory;

public class IncomingBatchBuilder extends BatchBuilderImpl {

	IncomingBatchBuilder(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		super(keyPair, signature, messageDigest, writerFactory);
	}

	private byte[] sig = null;

	public void setSignature(byte[] sig) {
		this.sig = sig;
	}

	public Batch build() throws IOException, SignatureException,
	InvalidKeyException {
		if(sig == null) throw new IllegalStateException();
		byte[] raw = getSignableRepresentation();
		signature.initVerify(keyPair.getPublic());
		signature.update(raw);
		signature.verify(sig);
		messageDigest.reset();
		messageDigest.update(raw);
		messageDigest.update(sig);
		byte[] hash = messageDigest.digest();
		return new BatchImpl(new BatchId(hash), raw.length, messages, sig);
	}
}
