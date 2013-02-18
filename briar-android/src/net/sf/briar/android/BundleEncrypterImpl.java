package net.sf.briar.android;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;
import android.os.Bundle;
import android.os.Parcel;

import com.google.inject.Inject;

// This class is not thread-safe
class BundleEncrypterImpl implements BundleEncrypter {

	private final AuthenticatedCipher cipher;
	private final SecureRandom random;
	private final ErasableKey key;
	private final int blockSize, macLength;

	@Inject
	BundleEncrypterImpl(CryptoComponent crypto) {
		cipher = crypto.getBundleCipher();
		random = crypto.getSecureRandom();
		key = crypto.generateSecretKey();
		blockSize = cipher.getBlockSize();
		macLength = cipher.getMacLength();
	}

	@Override
	public void encrypt(Bundle b) {
		// Marshall the plaintext contents into a byte array
		Parcel p = Parcel.obtain();
		b.writeToParcel(p, 0);
		byte[] plaintext = p.marshall();
		p.recycle();
		// Encrypt the byte array using the storage key and a random IV
		byte[] iv = new byte[blockSize];
		random.nextBytes(iv);
		byte[] ciphertext = new byte[plaintext.length + macLength];
		try {
			cipher.init(ENCRYPT_MODE, key, iv, null);
			cipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		ByteUtils.erase(plaintext);
		// Replace the plaintext contents with the IV and the ciphertext
		b.clear();
		b.putByteArray("net.sf.briar.IV", iv);
		b.putByteArray("net.sf.briar.CIPHERTEXT", ciphertext);
	}

	@Override
	public boolean decrypt(Bundle b) {
		// Retrieve the IV and the ciphertext
		byte[] iv = b.getByteArray("net.sf.briar.IV");
		if(iv == null) throw new IllegalArgumentException();
		if(iv.length != blockSize) throw new IllegalArgumentException();
		byte[] ciphertext = b.getByteArray("net.sf.briar.CIPHERTEXT");
		if(ciphertext == null) throw new IllegalArgumentException();
		if(ciphertext.length < macLength) throw new IllegalArgumentException();
		// Decrypt the ciphertext using the storage key and the IV
		byte[] plaintext = new byte[ciphertext.length - macLength];
		try {
			cipher.init(DECRYPT_MODE, key, iv, null);
			cipher.doFinal(ciphertext, 0, ciphertext.length, plaintext, 0);
		} catch(GeneralSecurityException e) {
			return false; // Invalid ciphertext
		}
		// Unmarshall the byte array
		Parcel p = Parcel.obtain();
		p.unmarshall(plaintext, 0, plaintext.length);
		ByteUtils.erase(plaintext);
		// Replace the IV and the ciphertext with the plaintext contents
		b.remove("net.sf.briar.IV");
		b.remove("net.sf.briar.CIPHERTEXT");
		p.setDataPosition(0);
		b.readFromParcel(p);
		p.recycle();
		return true;
	}
}
