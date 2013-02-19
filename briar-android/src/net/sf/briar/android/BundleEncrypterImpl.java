package net.sf.briar.android;

import static java.util.logging.Level.INFO;

import java.util.logging.Logger;

import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.util.ByteUtils;
import android.os.Bundle;
import android.os.Parcel;

import com.google.inject.Inject;

class BundleEncrypterImpl implements BundleEncrypter {

	private static final Logger LOG =
			Logger.getLogger(BundleEncrypterImpl.class.getName());

	private final CryptoComponent crypto;

	@Inject
	BundleEncrypterImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public void encrypt(Bundle b) {
		// Marshall the plaintext contents into a byte array
		Parcel p = Parcel.obtain();
		b.writeToParcel(p, 0);
		byte[] plaintext = p.marshall();
		p.recycle();
		if(LOG.isLoggable(INFO)) {
			LOG.info("Marshalled " + b.size() + " mappings, "
					+ plaintext.length + " plaintext bytes");
		}
		// Encrypt the plaintext
		byte[] ciphertext = crypto.encryptTemporaryStorage(plaintext);
		ByteUtils.erase(plaintext);
		// Replace the plaintext contents with the ciphertext
		b.clear();
		b.putByteArray("net.sf.briar.CIPHERTEXT", ciphertext);
	}

	@Override
	public boolean decrypt(Bundle b) {
		// Retrieve the ciphertext
		byte[] ciphertext = b.getByteArray("net.sf.briar.CIPHERTEXT");
		if(ciphertext == null) throw new IllegalArgumentException();
		// Decrypt the ciphertext
		byte[] plaintext = crypto.decryptTemporaryStorage(ciphertext);
		if(plaintext == null) return false;
		// Unmarshall the plaintext
		Parcel p = Parcel.obtain();
		p.unmarshall(plaintext, 0, plaintext.length);
		ByteUtils.erase(plaintext);
		// Restore the plaintext contents
		p.setDataPosition(0);
		b.readFromParcel(p);
		p.recycle();
		if(LOG.isLoggable(INFO)) {
			LOG.info("Unmarshalled " + (b.size() - 1) + " mappings, "
					+ plaintext.length + " plaintext bytes");
		}
		return true;
	}
}
