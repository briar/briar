package org.briarproject.bramble.crypto;

import org.spongycastle.asn1.sec.SECNamedCurves;
import org.spongycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.signers.DSADigestSigner;
import org.spongycastle.crypto.signers.DSAKCalculator;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.crypto.signers.HMacDSAKCalculator;
import org.spongycastle.math.ec.ECCurve;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.math.ec.MontgomeryLadderMultiplier;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Not a JUnit test
public class EllipticCurvePerformanceTest {

	private static final SecureRandom random = new SecureRandom();
	private static final int SAMPLES = 50;
	private static final int BYTES_TO_SIGN = 1024;
	private static final List<String> SEC_NAMES = Arrays.asList(
			"secp256k1", "secp256r1", "secp384r1", "secp521r1");
	private static final List<String> BRAINPOOL_NAMES = Arrays.asList(
			"brainpoolp256r1", "brainpoolp384r1", "brainpoolp512r1");

	public static void main(String[] args) {
		for (String name : SEC_NAMES) {
			ECDomainParameters params =
					convertParams(SECNamedCurves.getByName(name));
			runTest(name + " default", params);
			runTest(name + " constant", constantTime(params));
		}
		for (String name : BRAINPOOL_NAMES) {
			ECDomainParameters params =
					convertParams(TeleTrusTNamedCurves.getByName(name));
			runTest(name + " default", params);
			runTest(name + " constant", constantTime(params));
		}
		runTest("ours", EllipticCurveConstants.PARAMETERS);
	}

	private static void runTest(String name, ECDomainParameters params) {
		// Generate two key pairs using the given parameters
		ECKeyGenerationParameters generatorParams =
				new ECKeyGenerationParameters(params, random);
		ECKeyPairGenerator generator = new ECKeyPairGenerator();
		generator.init(generatorParams);
		AsymmetricCipherKeyPair keyPair1 = generator.generateKeyPair();
		ECPublicKeyParameters public1 =
				(ECPublicKeyParameters) keyPair1.getPublic();
		ECPrivateKeyParameters private1 =
				(ECPrivateKeyParameters) keyPair1.getPrivate();
		AsymmetricCipherKeyPair keyPair2 = generator.generateKeyPair();
		ECPublicKeyParameters public2 =
				(ECPublicKeyParameters) keyPair2.getPublic();
		// Time some ECDH key agreements
		List<Long> samples = new ArrayList<Long>();
		for (int i = 0; i < SAMPLES; i++) {
			ECDHCBasicAgreement agreement = new ECDHCBasicAgreement();
			long start = System.nanoTime();
			agreement.init(private1);
			agreement.calculateAgreement(public2);
			samples.add(System.nanoTime() - start);
		}
		long agreementMedian = median(samples);
		// Time some signatures
		List<byte[]> signatures = new ArrayList<byte[]>();
		samples.clear();
		for (int i = 0; i < SAMPLES; i++) {
			Digest digest = new Blake2sDigest();
			DSAKCalculator calculator = new HMacDSAKCalculator(digest);
			DSADigestSigner signer = new DSADigestSigner(new ECDSASigner(
					calculator), digest);
			long start = System.nanoTime();
			signer.init(true, new ParametersWithRandom(private1, random));
			signer.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			signatures.add(signer.generateSignature());
			samples.add(System.nanoTime() - start);
		}
		long signatureMedian = median(samples);
		// Time some signature verifications
		samples.clear();
		for (int i = 0; i < SAMPLES; i++) {
			Digest digest = new Blake2sDigest();
			DSAKCalculator calculator = new HMacDSAKCalculator(digest);
			DSADigestSigner signer = new DSADigestSigner(new ECDSASigner(
					calculator), digest);
			long start = System.nanoTime();
			signer.init(false, public1);
			signer.update(new byte[BYTES_TO_SIGN], 0, BYTES_TO_SIGN);
			if (!signer.verifySignature(signatures.get(i)))
				throw new AssertionError();
			samples.add(System.nanoTime() - start);
		}
		long verificationMedian = median(samples);
		System.out.println(name + ": "
				+ agreementMedian + " "
				+ signatureMedian + " "
				+ verificationMedian);
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

	private static ECDomainParameters constantTime(ECDomainParameters in) {
		ECCurve curve = in.getCurve().configure().setMultiplier(
				new MontgomeryLadderMultiplier()).create();
		BigInteger x = in.getG().getAffineXCoord().toBigInteger();
		BigInteger y = in.getG().getAffineYCoord().toBigInteger();
		ECPoint g = curve.createPoint(x, y);
		return new ECDomainParameters(curve, g, in.getN(), in.getH());
	}
}
