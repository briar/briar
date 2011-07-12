package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

public class OutgoingBatchBuilder extends BatchBuilderImpl {

	OutgoingBatchBuilder(KeyPair keyPair, Signature signature,
			MessageDigest messageDigest, WriterFactory writerFactory) {
		super(keyPair, signature, messageDigest, writerFactory);
	}

	public void setSignature(byte[] sig) {
		throw new UnsupportedOperationException();
	}

	public Batch build() throws IOException, GeneralSecurityException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeList(messages);
		byte[] signable = out.toByteArray();
		signature.initSign(keyPair.getPrivate());
		signature.update(signable);
		byte[] sig = signature.sign();
		w.writeRaw(sig);
		w.close();
		byte[] raw = out.toByteArray();
		messageDigest.reset();
		messageDigest.update(raw);
		BatchId id = new BatchId(messageDigest.digest());
		return new BatchImpl(id, raw.length, messages, sig);
	}
}
