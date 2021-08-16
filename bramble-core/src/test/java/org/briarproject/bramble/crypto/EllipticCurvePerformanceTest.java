package org.briarproject.bramble.crypto;

import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.KeyPairGenerator;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.agreement.ECDHCBasicAgreement;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.DSAKCalculator;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.i2p.crypto.eddsa.EdDSAEngine.SIGNATURE_ALGORITHM;

// Not a JUnit test
public class EllipticCurvePerformanceTest {

	private static final SecureRandom random = new SecureRandom();
	private static final int SAMPLES = 50;
	private static final int BYTES_TO_SIGN = 1024;
	private static final List<String> SEC_NAMES = Arrays.asList(
			"secp256k1", "secp256r1", "secp384r1", "secp521r1");
	private static final List<String> BRAINPOOL_NAMES = Arrays.asList(
			"brainpoolp256r1", "brainpoolp384r1", "brainpoolp512r1");
	private static final Provider ED_PROVIDER = new EdDSASecurityProvider();

	public static void main(String[] args) throws GeneralSecurityException {
		for (String name : SEC_NAMES) {
			ECDomainParameters params =
					convertParams(SECNamedCurves.getByName(name));
			runTest(name, params);
		}
		for (String name : BRAINPOOL_NAMES) {
			ECDomainParameters params =
					convertParams(TeleTrusTNamedCurves.getByName(name));
			runTest(name, params);
		}
		runCurve25519Test();
		runEd25519Test();
	}

	private static void runTest(String name, ECDomainParameters params) {
		// Generate two key pairs using the given parameters
		ECKeyPairGenerator generator = new ECKeyPairGenerator();
		generator.init(new ECKeyGenerationParameters(params, random));
		AsymmetricCipherKeyPair keyPair1 = generator.generateKeyPair();
		AsymmetricCipherKeyPair keyPair2 = generator.generateKeyPair();
		// Time some ECDH and ECDHC key agreements
		long agreementMedian = runAgreementTest(keyPair1, keyPair2, false);
		long agreementWithCofactorMedian =
				runAgreementTest(keyPair1, keyPair2, true);
		// Time some signatures
		List<Long> samples = new ArrayList<>();
		List<byte[]> signatures = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			Digest digest = new Blake2bDigest(256);
			DSAKCalculator calculator = new HMacDSAKCalculator(digest);
			DSADigestSigner signer = new DSADigestSigner(new ECDSASigner(
					calculator), digest);
			long start = System.nanoTime();
			signer.init(true,
					new ParametersWithRandom(keyPair1.getPrivate(), random));
			signer.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			signatures.add(signer.generateSignature());
			samples.add(System.nanoTime() - start);
		}
		long signatureMedian = median(samples);
		// Time some signature verifications
		samples.clear();
		for (int i = 0; i < SAMPLES; i++) {
			Digest digest = new Blake2bDigest(256);
			DSAKCalculator calculator = new HMacDSAKCalculator(digest);
			DSADigestSigner signer = new DSADigestSigner(new ECDSASigner(
					calculator), digest);
			long start = System.nanoTime();
			signer.init(false, keyPair1.getPublic());
			signer.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			if (!signer.verifySignature(signatures.get(i)))
				throw new AssertionError();
			samples.add(System.nanoTime() - start);
		}
		long verificationMedian = median(samples);
		System.out.println(String.format("%s: %,d %,d %,d %,d", name,
				agreementMedian, agreementWithCofactorMedian,
				signatureMedian, verificationMedian));
	}

	private static long runAgreementTest(AsymmetricCipherKeyPair keyPair1,
			AsymmetricCipherKeyPair keyPair2, boolean withCofactor) {
		List<Long> samples = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			BasicAgreement agreement = createAgreement(withCofactor);
			long start = System.nanoTime();
			agreement.init(keyPair1.getPrivate());
			agreement.calculateAgreement(keyPair2.getPublic());
			samples.add(System.nanoTime() - start);
		}
		return median(samples);
	}

	private static BasicAgreement createAgreement(boolean withCofactor) {
		if (withCofactor) return new ECDHCBasicAgreement();
		else return new ECDHBasicAgreement();
	}

	private static void runCurve25519Test() {
		Curve25519 curve25519 = Curve25519.getInstance("java");
		Curve25519KeyPair keyPair1 = curve25519.generateKeyPair();
		Curve25519KeyPair keyPair2 = curve25519.generateKeyPair();
		// Time some key agreements
		List<Long> samples = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			long start = System.nanoTime();
			curve25519.calculateAgreement(keyPair1.getPublicKey(),
					keyPair2.getPrivateKey());
			samples.add(System.nanoTime() - start);
		}
		long agreementMedian = median(samples);
		System.out.println(String.format("Curve25519: %,d - - -",
				agreementMedian));
	}

	private static void runEd25519Test() throws GeneralSecurityException {
		KeyPair keyPair = new KeyPairGenerator().generateKeyPair();
		// Time some signatures
		List<Long> samples = new ArrayList<>();
		List<byte[]> signatures = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			Signature signature =
					Signature.getInstance(SIGNATURE_ALGORITHM, ED_PROVIDER);
			long start = System.nanoTime();
			signature.initSign(keyPair.getPrivate(), random);
			signature.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			signatures.add(signature.sign());
			samples.add(System.nanoTime() - start);
		}
		long signatureMedian = median(samples);
		// Time some signature verifications
		samples.clear();
		for (int i = 0; i < SAMPLES; i++) {
			Signature signature =
					Signature.getInstance(SIGNATURE_ALGORITHM, ED_PROVIDER);
			long start = System.nanoTime();
			signature.initVerify(keyPair.getPublic());
			signature.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			if (!signature.verify(signatures.get(i)))
				throw new AssertionError();
			samples.add(System.nanoTime() - start);
		}
		long verificationMedian = median(samples);
		System.out.println(String.format("Ed25519: - - %,d %,d",
				signatureMedian, verificationMedian));
	}

	private static long median(List<Long> list) {
		int size = list.size();
		if (size == 0) throw new IllegalArgumentException();
		Collections.sort(list);
		if (size % 2 == 1) return list.get(size / 2);
		return list.get(size / 2 - 1) + list.get(size / 2) / 2;
	}

	private static ECDomainParameters convertParams(X9ECParameters in) {
		return new ECDomainParameters(in.getCurve(), in.getG(), in.getN(),
				in.getH());
	}
}
