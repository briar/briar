package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.serial.Writer;
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

	public Batch build() throws IOException, GeneralSecurityException {
		if(sig == null) throw new IllegalStateException();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeList(messages);
		byte[] signable = out.toByteArray();
		signature.initVerify(keyPair.getPublic());
		signature.update(signable);
		if(!signature.verify(sig)) throw new SignatureException();
		w.writeRaw(sig);
		w.close();
		byte[] raw = out.toByteArray();
		messageDigest.reset();
		messageDigest.update(raw);
		BatchId id = new BatchId(messageDigest.digest());
		return new BatchImpl(id, raw.length, messages, sig);
	}
}
