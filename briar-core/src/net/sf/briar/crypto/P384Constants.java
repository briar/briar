package net.sf.briar.crypto;

import java.math.BigInteger;

import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.math.ec.ECCurve;
import org.spongycastle.math.ec.ECFieldElement;
import org.spongycastle.math.ec.ECPoint;

interface P384Constants {

	// Parameters for NIST elliptic curve P-384 - see "Suite B Implementer's
	// Guide to NIST SP 800-56A", section A.2
	BigInteger P_384_Q = new BigInteger("FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" +
			"FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" + "FFFFFFFE" +
			"FFFFFFFF" + "00000000" + "00000000" + "FFFFFFFF", 16);
	BigInteger P_384_A = new BigInteger("FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" +
			"FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" + "FFFFFFFE" +
			"FFFFFFFF" + "00000000" + "00000000" + "FFFFFFFC", 16);
	BigInteger P_384_B = new BigInteger("B3312FA7" + "E23EE7E4" + "988E056B" +
			"E3F82D19" + "181D9C6E" + "FE814112" + "0314088F" + "5013875A" +
			"C656398D" + "8A2ED19D" + "2A85C8ED" + "D3EC2AEF", 16);
	BigInteger P_384_G_X = new BigInteger("AA87CA22" + "BE8B0537" + "8EB1C71E" +
			"F320AD74" + "6E1D3B62" + "8BA79B98" + "59F741E0" + "82542A38" +
			"5502F25D" + "BF55296C" + "3A545E38" + "72760AB7", 16);
	BigInteger P_384_G_Y = new BigInteger("3617DE4A" + "96262C6F" + "5D9E98BF" +
			"9292DC29" + "F8F41DBD" + "289A147C" + "E9DA3113" + "B5F0B8C0" +
			"0A60B1CE" + "1D7E819D" + "7A431D7C" + "90EA0E5F", 16);
	BigInteger P_384_N = new BigInteger("FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" +
			"FFFFFFFF" + "FFFFFFFF" + "FFFFFFFF" + "C7634D81" + "F4372DDF" +
			"581A0DB2" + "48B0A77A" + "ECEC196A" + "CCC52973", 16);
	BigInteger P_384_H = BigInteger.ONE;

	// Static parameter objects derived from the above parameters
	ECCurve P_384_CURVE = new ECCurve.Fp(P_384_Q, P_384_A, P_384_B);
	ECPoint P_384_G = new ECPoint.Fp(P_384_CURVE,
			new ECFieldElement.Fp(P_384_Q, P_384_G_X),
			new ECFieldElement.Fp(P_384_Q, P_384_G_Y));
	ECDomainParameters P_384_PARAMS = new ECDomainParameters(P_384_CURVE,
			P_384_G, P_384_N, P_384_H);
}
