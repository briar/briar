package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.signers.DSADigestSigner;
import org.spongycastle.crypto.signers.DSAKCalculator;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.crypto.signers.HMacDSAKCalculator;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.logging.Logger;

import javax.annotation.concurrent.NotThreadSafe;

import static java.util.logging.Level.INFO;

@NotThreadSafe
@NotNullByDefault
class SignatureImpl implements Signature {

	private static final Logger LOG =
			Logger.getLogger(SignatureImpl.class.getName());

	private final SecureRandom secureRandom;
	private final DSADigestSigner signer;

	SignatureImpl(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
		Digest digest = new Blake2sDigest();
		DSAKCalculator calculator = new HMacDSAKCalculator(digest);
		signer = new DSADigestSigner(new ECDSASigner(calculator), digest);
	}

	@Override
	public void initSign(PrivateKey k) throws GeneralSecurityException {
		if (!(k instanceof Sec1PrivateKey))
			throw new IllegalArgumentException();
		ECPrivateKeyParameters priv = ((Sec1PrivateKey) k).getKey();
		signer.init(true, new ParametersWithRandom(priv, secureRandom));
	}

	@Override
	public void initVerify(PublicKey k) throws GeneralSecurityException {
		if (!(k instanceof Sec1PublicKey))
			throw new IllegalArgumentException();
		ECPublicKeyParameters pub = ((Sec1PublicKey) k).getKey();
		signer.init(false, pub);
	}

	@Override
	public void update(byte b) {
		signer.update(b);
	}

	@Override
	public void update(byte[] b) {
		update(b, 0, b.length);
	}

	@Override
	public void update(byte[] b, int off, int len) {
		signer.update(b, off, len);
	}

	@Override
	public byte[] sign() {
		long now = System.currentTimeMillis();
		byte[] signature = signer.generateSignature();
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Generating signature took " + duration + " ms");
		return signature;
	}

	@Override
	public boolean verify(byte[] signature) {
		long now = System.currentTimeMillis();
		boolean valid = signer.verifySignature(signature);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Verifying signature took " + duration + " ms");
		return valid;
	}
}
