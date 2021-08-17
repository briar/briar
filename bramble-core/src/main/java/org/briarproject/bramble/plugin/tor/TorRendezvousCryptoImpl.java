package org.briarproject.bramble.plugin.tor;

import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.util.encoders.Base64;
import org.briarproject.bramble.util.Base32;

import java.nio.charset.Charset;

import static java.lang.System.arraycopy;

public class TorRendezvousCryptoImpl implements TorRendezvousCrypto {

	private static final EdDSANamedCurveSpec CURVE_SPEC =
			EdDSANamedCurveTable.getByName("Ed25519");

	private static final byte HS_PROTOCOL_VERSION = 3;
	private static final int CHECKSUM_BYTES = 2;

	@Override
	public String getOnionAddress(byte[] seed) {
		EdDSAPrivateKeySpec spec = new EdDSAPrivateKeySpec(seed, CURVE_SPEC);
		byte[] publicKey = spec.getA().toByteArray();
		Digest digest = new SHA3Digest(256);
		byte[] label = ".onion checksum".getBytes(Charset.forName("US-ASCII"));
		digest.update(label, 0, label.length);
		digest.update(publicKey, 0, publicKey.length);
		digest.update(HS_PROTOCOL_VERSION);
		byte[] checksum = new byte[digest.getDigestSize()];
		digest.doFinal(checksum, 0);
		byte[] address = new byte[publicKey.length + CHECKSUM_BYTES + 1];
		arraycopy(publicKey, 0, address, 0, publicKey.length);
		arraycopy(checksum, 0, address, publicKey.length, CHECKSUM_BYTES);
		address[address.length - 1] = HS_PROTOCOL_VERSION;
		return Base32.encode(address).toLowerCase();
	}

	@Override
	public String getPrivateKeyBlob(byte[] seed) {
		EdDSAPrivateKeySpec spec = new EdDSAPrivateKeySpec(seed, CURVE_SPEC);
		byte[] hash = spec.getH();
		byte[] base64 = Base64.encode(hash);
		return "ED25519-V3:" + new String(base64, Charset.forName("US-ASCII"));
	}
}
