package org.briarproject.crypto;

import static java.util.logging.Level.INFO;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.logging.Logger;

import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.signers.DSADigestSigner;
import org.spongycastle.crypto.signers.DSAKCalculator;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.crypto.signers.HMacDSAKCalculator;

class SignatureImpl implements Signature {

	private static final Logger LOG =
			Logger.getLogger(SignatureImpl.class.getName());

	private final SecureRandom secureRandom;
	private final DSADigestSigner signer;

	SignatureImpl(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
		Digest digest = new SHA256Digest();
		DSAKCalculator calculator = new HMacDSAKCalculator(digest);
		signer = new DSADigestSigner(new ECDSASigner(calculator), digest);
	}

	public void initSign(PrivateKey k) throws GeneralSecurityException {
		if(!(k instanceof Sec1PrivateKey)) throw new GeneralSecurityException();
		ECPrivateKeyParameters priv = ((Sec1PrivateKey) k).getKey();
		signer.init(true, new ParametersWithRandom(priv, secureRandom));
	}

	public void initVerify(PublicKey k) throws GeneralSecurityException {
		if(!(k instanceof Sec1PublicKey)) throw new GeneralSecurityException();
		ECPublicKeyParameters pub = ((Sec1PublicKey) k).getKey();
		signer.init(false, pub);
	}

	public void update(byte b) {
		signer.update(b);
	}

	public void update(byte[] b) {
		update(b, 0, b.length);
	}

	public void update(byte[] b, int off, int len) {
		signer.update(b, off, len);
	}

	public byte[] sign() {
		long now = System.currentTimeMillis();
		byte[] signature = signer.generateSignature();
		long duration = System.currentTimeMillis() - now;
		if(LOG.isLoggable(INFO))
			LOG.info("Generating signature took " + duration + " ms");
		return signature;
	}

	public boolean verify(byte[] signature) {
		long now = System.currentTimeMillis();
		boolean valid = signer.verifySignature(signature);
		long duration = System.currentTimeMillis() - now;
		if(LOG.isLoggable(INFO))
			LOG.info("Verifying signature took " + duration + " ms");
		return valid;
	}
}
