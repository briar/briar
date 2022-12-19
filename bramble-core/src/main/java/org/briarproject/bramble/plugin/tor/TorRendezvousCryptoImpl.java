package org.briarproject.bramble.plugin.tor;

import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

import org.bouncycastle.util.encoders.Base64;
import org.briarproject.bramble.api.crypto.CryptoComponent;

import static org.briarproject.bramble.util.StringUtils.US_ASCII;

class TorRendezvousCryptoImpl implements TorRendezvousCrypto {

	private static final EdDSANamedCurveSpec CURVE_SPEC =
			EdDSANamedCurveTable.getByName("Ed25519");

	private final CryptoComponent crypto;

	TorRendezvousCryptoImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public String getOnion(byte[] seed) {
		EdDSAPrivateKeySpec spec = new EdDSAPrivateKeySpec(seed, CURVE_SPEC);
		return crypto.encodeOnion(spec.getA().toByteArray());
	}

	@Override
	public String getPrivateKeyBlob(byte[] seed) {
		EdDSAPrivateKeySpec spec = new EdDSAPrivateKeySpec(seed, CURVE_SPEC);
		byte[] hash = spec.getH();
		byte[] base64 = Base64.encode(hash);
		return "ED25519-V3:" + new String(base64, US_ASCII);
	}
}
