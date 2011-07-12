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

public class OutgoingBatchBuilder extends BatchBuilderImpl {

	OutgoingBatchBuilder(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		super(keyPair, signature, messageDigest, writerFactory);
	}

	public void setSignature(byte[] sig) {
		throw new UnsupportedOperationException();
	}

	public Batch build() throws IOException, SignatureException,
	InvalidKeyException {
		byte[] raw = getSignableRepresentation();
		signature.initSign(keyPair.getPrivate());
		signature.update(raw);
		byte[] sig = signature.sign();
		messageDigest.reset();
		messageDigest.update(raw);
		messageDigest.update(sig);
		byte[] hash = messageDigest.digest();
		return new BatchImpl(new BatchId(hash), raw.length, messages, sig);
	}
}
