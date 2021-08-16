package org.briarproject.bramble.crypto;

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.generators.Poly1305KeyGenerator;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;

/**
 * An authenticated cipher that uses XSalsa20 for encryption and Poly1305 for
 * authentication. It is equivalent to the C++ implementation of
 * crypto_secretbox in NaCl, and to the C implementations of crypto_secretbox
 * in NaCl and libsodium once the zero-padding has been removed.
 * <p/>
 * References:
 * <ul>
 * <li>http://nacl.cr.yp.to/secretbox.html</li>
 * <li>http://cr.yp.to/highspeed/naclcrypto-20090310.pdf</li>
 * </ul>
 */
@NotThreadSafe
@NotNullByDefault
class XSalsa20Poly1305AuthenticatedCipher implements AuthenticatedCipher {

	/**
	 * Length of the padding to be used to generate the Poly1305 key
	 */
	private static final int SUBKEY_LENGTH = 32;

	private final XSalsa20Engine xSalsa20Engine;
	private final Poly1305 poly1305;

	private boolean encrypting;

	XSalsa20Poly1305AuthenticatedCipher() {
		xSalsa20Engine = new XSalsa20Engine();
		poly1305 = new Poly1305();
	}

	@Override
	public void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException {
		encrypting = encrypt;
		KeyParameter k = new KeyParameter(key.getBytes());
		ParametersWithIV params = new ParametersWithIV(k, iv);
		try {
			xSalsa20Engine.init(encrypt, params);
		} catch (IllegalArgumentException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	@Override
	public int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException {
		if (!encrypting && len < MAC_LENGTH)
			throw new GeneralSecurityException("Invalid MAC");
		try {
			// Generate the Poly1305 subkey from an empty array
			byte[] zero = new byte[SUBKEY_LENGTH];
			byte[] subKey = new byte[SUBKEY_LENGTH];
			xSalsa20Engine.processBytes(zero, 0, SUBKEY_LENGTH, subKey, 0);

			// Clamp the subkey
			Poly1305KeyGenerator.clamp(subKey);

			// Initialize Poly1305 with the subkey
			KeyParameter k = new KeyParameter(subKey);
			poly1305.init(k);

			// If we are decrypting, verify the MAC
			if (!encrypting) {
				byte[] mac = new byte[MAC_LENGTH];
				poly1305.update(input, inputOff + MAC_LENGTH, len - MAC_LENGTH);
				poly1305.doFinal(mac, 0);
				// Constant-time comparison
				int cmp = 0;
				for (int i = 0; i < MAC_LENGTH; i++)
					cmp |= mac[i] ^ input[inputOff + i];
				if (cmp != 0)
					throw new GeneralSecurityException("Invalid MAC");
			}

			// Apply or invert the stream encryption
			int processed = xSalsa20Engine.processBytes(
					input, encrypting ? inputOff : inputOff + MAC_LENGTH,
					encrypting ? len : len - MAC_LENGTH,
					output, encrypting ? outputOff + MAC_LENGTH : outputOff);

			// If we are encrypting, generate the MAC
			if (encrypting) {
				poly1305.update(output, outputOff + MAC_LENGTH, len);
				poly1305.doFinal(output, outputOff);
			}

			return encrypting ? processed + MAC_LENGTH : processed;
		} catch (DataLengthException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	@Override
	public int getMacBytes() {
		return MAC_LENGTH;
	}
}
