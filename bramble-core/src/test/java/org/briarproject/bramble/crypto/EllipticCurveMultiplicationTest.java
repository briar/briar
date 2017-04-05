package org.briarproject.bramble.crypto;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;
import org.spongycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.math.ec.ECCurve;
import org.spongycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.briarproject.bramble.crypto.EllipticCurveConstants.PARAMETERS;
import static org.junit.Assert.assertEquals;

public class EllipticCurveMultiplicationTest extends BrambleTestCase {

	@Test
	public void testMultiplierProducesSameResultsAsDefault() throws Exception {
		// Instantiate the default implementation of the curve
		X9ECParameters defaultX9Parameters =
				TeleTrusTNamedCurves.getByName("brainpoolp256r1");
		ECCurve defaultCurve = defaultX9Parameters.getCurve();
		ECPoint defaultG = defaultX9Parameters.getG();
		BigInteger defaultN = defaultX9Parameters.getN();
		BigInteger defaultH = defaultX9Parameters.getH();
		// Check that the default parameters are equal to our parameters
		assertEquals(PARAMETERS.getCurve(), defaultCurve);
		assertEquals(PARAMETERS.getG(), defaultG);
		assertEquals(PARAMETERS.getN(), defaultN);
		assertEquals(PARAMETERS.getH(), defaultH);
		// ECDomainParameters doesn't have an equals() method, but it's just a
		// container for the parameters
		ECDomainParameters defaultParameters = new ECDomainParameters(
				defaultCurve, defaultG, defaultN, defaultH);
		// Generate two key pairs with each set of parameters, using the same
		// deterministic PRNG for both sets of parameters
		byte[] seed = new byte[32];
		new SecureRandom().nextBytes(seed);
		// Montgomery ladder multiplier
		SecureRandom random = new PseudoSecureRandom(seed);
		ECKeyGenerationParameters montgomeryGeneratorParams =
				new ECKeyGenerationParameters(PARAMETERS, random);
		ECKeyPairGenerator montgomeryGenerator = new ECKeyPairGenerator();
		montgomeryGenerator.init(montgomeryGeneratorParams);
		AsymmetricCipherKeyPair montgomeryKeyPair1 =
				montgomeryGenerator.generateKeyPair();
		ECPrivateKeyParameters montgomeryPrivate1 =
				(ECPrivateKeyParameters) montgomeryKeyPair1.getPrivate();
		ECPublicKeyParameters montgomeryPublic1 =
				(ECPublicKeyParameters) montgomeryKeyPair1.getPublic();
		AsymmetricCipherKeyPair montgomeryKeyPair2 =
				montgomeryGenerator.generateKeyPair();
		ECPrivateKeyParameters montgomeryPrivate2 =
				(ECPrivateKeyParameters) montgomeryKeyPair2.getPrivate();
		ECPublicKeyParameters montgomeryPublic2 =
				(ECPublicKeyParameters) montgomeryKeyPair2.getPublic();
		// Default multiplier
		random = new PseudoSecureRandom(seed);
		ECKeyGenerationParameters defaultGeneratorParams =
				new ECKeyGenerationParameters(defaultParameters, random);
		ECKeyPairGenerator defaultGenerator = new ECKeyPairGenerator();
		defaultGenerator.init(defaultGeneratorParams);
		AsymmetricCipherKeyPair defaultKeyPair1 =
				defaultGenerator.generateKeyPair();
		ECPrivateKeyParameters defaultPrivate1 =
				(ECPrivateKeyParameters) defaultKeyPair1.getPrivate();
		ECPublicKeyParameters defaultPublic1 =
				(ECPublicKeyParameters) defaultKeyPair1.getPublic();
		AsymmetricCipherKeyPair defaultKeyPair2 =
				defaultGenerator.generateKeyPair();
		ECPrivateKeyParameters defaultPrivate2 =
				(ECPrivateKeyParameters) defaultKeyPair2.getPrivate();
		ECPublicKeyParameters defaultPublic2 =
				(ECPublicKeyParameters) defaultKeyPair2.getPublic();
		// The key pairs generated with both sets of parameters should be equal
		assertEquals(montgomeryPrivate1.getD(), defaultPrivate1.getD());
		assertEquals(montgomeryPublic1.getQ(), defaultPublic1.getQ());
		assertEquals(montgomeryPrivate2.getD(), defaultPrivate2.getD());
		assertEquals(montgomeryPublic2.getQ(), defaultPublic2.getQ());
		// OK, all of the above was just sanity checks - now for the test!
		ECDHCBasicAgreement agreement = new ECDHCBasicAgreement();
		agreement.init(montgomeryPrivate1);
		BigInteger sharedSecretMontgomeryMontgomery =
				agreement.calculateAgreement(montgomeryPublic2);
		agreement.init(montgomeryPrivate1);
		BigInteger sharedSecretMontgomeryDefault =
				agreement.calculateAgreement(defaultPublic2);
		agreement.init(defaultPrivate1);
		BigInteger sharedSecretDefaultMontgomery =
				agreement.calculateAgreement(montgomeryPublic2);
		agreement.init(defaultPrivate1);
		BigInteger sharedSecretDefaultDefault =
				agreement.calculateAgreement(defaultPublic2);
		// Shared secrets calculated with different multipliers should be equal
		assertEquals(sharedSecretMontgomeryMontgomery,
				sharedSecretMontgomeryDefault);
		assertEquals(sharedSecretMontgomeryMontgomery,
				sharedSecretDefaultMontgomery);
		assertEquals(sharedSecretMontgomeryMontgomery,
				sharedSecretDefaultDefault);
	}
}
