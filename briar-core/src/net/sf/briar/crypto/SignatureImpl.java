package net.sf.briar.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import net.sf.briar.api.crypto.PrivateKey;
import net.sf.briar.api.crypto.PublicKey;
import net.sf.briar.api.crypto.Signature;

import org.spongycastle.crypto.digests.SHA384Digest;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.signers.DSADigestSigner;
import org.spongycastle.crypto.signers.ECDSASigner;

class SignatureImpl implements Signature {

	private final SecureRandom secureRandom;
	private final DSADigestSigner signer;

	SignatureImpl(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
		signer = new DSADigestSigner(new ECDSASigner(), new SHA384Digest());
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
		return signer.generateSignature();
	}

	public boolean verify(byte[] signature) {
		return signer.verifySignature(signature);
	}
}
