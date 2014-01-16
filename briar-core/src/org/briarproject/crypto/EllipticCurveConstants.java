package org.briarproject.crypto;

import java.math.BigInteger;

import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.math.ec.ECCurve;
import org.spongycastle.math.ec.ECPoint;

/** Parameters for curve brainpoolP384r1 - see RFC 5639. */
interface EllipticCurveConstants {

	/**
	 * The prime specifying the finite field. (This is called p in RFC 5639 and
	 * q in SEC 2.)
	 */
	BigInteger P = new BigInteger("8CB91E82" + "A3386D28" + "0F5D6F7E" +
			"50E641DF" + "152F7109" + "ED5456B4" + "12B1DA19" + "7FB71123" +
			"ACD3A729" + "901D1A71" + "87470013" + "3107EC53", 16);

	/**
	 * A coefficient of the equation y^2 = x^3 + A*x + B defining the elliptic
	 * curve. (This is called A in RFC 5639 and a in SEC 2.)
	 */
	BigInteger A = new BigInteger("7BC382C6" + "3D8C150C" + "3C72080A" +
			"CE05AFA0" + "C2BEA28E" + "4FB22787" + "139165EF" + "BA91F90F" +
			"8AA5814A" + "503AD4EB" + "04A8C7DD" + "22CE2826", 16);

	/**
	 * A coefficient of the equation y^2 = x^3 + A*x + B defining the elliptic
	 * curve. (This is called B in RFC 5639 b in SEC 2.)
	 */
	BigInteger B = new BigInteger("04A8C7DD" + "22CE2826" + "8B39B554" +
			"16F0447C" + "2FB77DE1" + "07DCD2A6" + "2E880EA5" + "3EEB62D5" +
			"7CB43902" + "95DBC994" + "3AB78696" + "FA504C11", 16);

	/**
	 * The x co-ordinate of the base point G. (This is called x in RFC 5639 and
	 * SEC 2.)
	 */
	BigInteger X = new BigInteger("1D1C64F0" + "68CF45FF" + "A2A63A81" +
			"B7C13F6B" + "8847A3E7" + "7EF14FE3" + "DB7FCAFE" + "0CBD10E8" +
			"E826E034" + "36D646AA" + "EF87B2E2" + "47D4AF1E", 16);

	/**
	 * The y co-ordinate of the base point G. (This is called y in RFC 5639 and
	 * SEC 2.)
	 */
	BigInteger Y = new BigInteger("8ABE1D75" + "20F9C2A4" + "5CB1EB8E" +
			"95CFD552" + "62B70B29" + "FEEC5864" + "E19C054F" + "F9912928" +
			"0E464621" + "77918111" + "42820341" + "263C5315", 16);

	/**
	 * The order of the base point G. (This is called q in RFC 5639 and n in
	 * SEC 2.)
	 */
	BigInteger Q = new BigInteger("8CB91E82" + "A3386D28" + "0F5D6F7E" +
			"50E641DF" + "152F7109" + "ED5456B3" + "1F166E6C" + "AC0425A7" +
			"CF3AB6AF" + "6B7FC310" + "3B883202" + "E9046565", 16);

	/** The cofactor of G. (This is called h in RFC 5639 and SEC 2.) */
	BigInteger H = BigInteger.ONE;

	// Static parameter objects derived from the above parameters
	ECCurve CURVE = new ECCurve.Fp(P, A, B);
	ECPoint G = CURVE.createPoint(X, Y);
	ECDomainParameters PARAMETERS = new ECDomainParameters(CURVE, G, Q, H);
}
