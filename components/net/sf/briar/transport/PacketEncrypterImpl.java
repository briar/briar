package net.sf.briar.transport;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

class PacketEncrypterImpl extends FilterOutputStream
implements PacketEncrypter {

	private final OutputStream out;
	private final Cipher tagCipher, packetCipher;
	private final SecretKey packetKey;

	PacketEncrypterImpl(OutputStream out, Cipher tagCipher,
			Cipher packetCipher, SecretKey tagKey, SecretKey packetKey) {
		super(out);
		this.out = out;
		this.tagCipher = tagCipher;
		this.packetCipher = packetCipher;
		this.packetKey = packetKey;
		try {
			tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		if(tagCipher.getOutputSize(16) != 16)
			throw new IllegalArgumentException();
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void writeTag(byte[] tag) throws IOException {
		if(tag.length != 16) throw new IllegalArgumentException();
		IvParameterSpec iv = new IvParameterSpec(tag);
		try {
			out.write(tagCipher.doFinal(tag));
			packetCipher.init(Cipher.ENCRYPT_MODE, packetKey, iv);
		} catch(BadPaddingException badCipher) {
			throw new IOException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidAlgorithmParameterException badIv) {
			throw new RuntimeException(badIv);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
	}

	public void finishPacket() throws IOException {
		try {
			out.write(packetCipher.doFinal());
		} catch(BadPaddingException badCipher) {
			throw new IOException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
	}

	@Override
	public void write(int b) throws IOException {
		byte[] ciphertext = packetCipher.update(new byte[] {(byte) b});
		if(ciphertext != null) out.write(ciphertext);
	}

	@Override
	public void write(byte[] b) throws IOException {
		byte[] ciphertext = packetCipher.update(b);
		if(ciphertext != null) out.write(ciphertext);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		byte[] ciphertext = packetCipher.update(b, off, len);
		if(ciphertext != null) out.write(ciphertext);
	}
}
