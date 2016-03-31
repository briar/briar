package org.briarproject.crypto;

import org.briarproject.util.StringUtils;
import org.spongycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.BasicAgreement;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.DerivationFunction;
import org.spongycastle.crypto.KeyEncoder;
import org.spongycastle.crypto.KeyParser;
import org.spongycastle.crypto.Mac;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.engines.IESEngine;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.spongycastle.crypto.generators.KDF2BytesGenerator;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.IESWithCipherParameters;
import org.spongycastle.crypto.parsers.ECIESPublicKeyParser;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Scanner;

public class MessageEncrypter {

	private static final ECDomainParameters PARAMETERS;
	private static final int MAC_KEY_BITS = 256;
	private static final int CIPHER_KEY_BITS = 256;
	private static final int LINE_LENGTH = 70;

	static {
		X9ECParameters x9 = TeleTrusTNamedCurves.getByName("brainpoolp512r1");
		PARAMETERS = new ECDomainParameters(x9.getCurve(), x9.getG(),
				x9.getN(), x9.getH());
	}

	private final ECKeyPairGenerator generator;
	private final EphemeralKeyPairGenerator ephemeralGenerator;
	private final KeyParser parser;

	MessageEncrypter(SecureRandom random) {
		generator = new ECKeyPairGenerator();
		generator.init(new ECKeyGenerationParameters(PARAMETERS, random));
		KeyEncoder encoder = new PublicKeyEncoder();
		ephemeralGenerator = new EphemeralKeyPairGenerator(generator, encoder);
		parser = new PublicKeyParser(PARAMETERS);
	}

	AsymmetricCipherKeyPair generateKeyPair() {
		return generator.generateKeyPair();
	}

	byte[] encrypt(byte[] keyBytes, byte[] plaintext)
			throws IOException, CryptoException {
		InputStream in = new ByteArrayInputStream(keyBytes);
		ECPublicKeyParameters publicKey =
				(ECPublicKeyParameters) parser.readKey(in);
		return encrypt(publicKey, plaintext);
	}

	byte[] encrypt(ECPublicKeyParameters pubKey, byte[] plaintext)
			throws CryptoException {
		IESEngine engine = getEngine();
		engine.init(pubKey, getCipherParameters(), ephemeralGenerator);
		return engine.processBlock(plaintext, 0, plaintext.length);
	}

	byte[] decrypt(ECPrivateKeyParameters privKey, byte[] ciphertext)
			throws CryptoException {
		IESEngine engine = getEngine();
		engine.init(privKey, getCipherParameters(), parser);
		return engine.processBlock(ciphertext, 0, ciphertext.length);
	}

	private IESEngine getEngine() {
		BasicAgreement agreement = new ECDHCBasicAgreement();
		DerivationFunction kdf = new KDF2BytesGenerator(new SHA256Digest());
		Mac mac = new HMac(new SHA256Digest());
		BlockCipher cipher = new CBCBlockCipher(new AESLightEngine());
		PaddedBufferedBlockCipher pad = new PaddedBufferedBlockCipher(cipher);
		return new IESEngine(agreement, kdf, mac, pad);
	}

	private CipherParameters getCipherParameters() {
		return new IESWithCipherParameters(null, null, MAC_KEY_BITS,
				CIPHER_KEY_BITS);
	}

	private static class PublicKeyEncoder implements KeyEncoder {

		@Override
		public byte[] getEncoded(AsymmetricKeyParameter key) {
			if (!(key instanceof ECPublicKeyParameters))
				throw new IllegalArgumentException();
			return ((ECPublicKeyParameters) key).getQ().getEncoded(false);
		}
	}

	private static class PublicKeyParser extends ECIESPublicKeyParser {

		private PublicKeyParser(ECDomainParameters ecParams) {
			super(ecParams);
		}

		@Override
		public AsymmetricKeyParameter readKey(InputStream in)
				throws IOException {
			try {
				return super.readKey(in);
			} catch (IllegalArgumentException e) {
				throw new IOException(e);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			printUsage();
			return;
		}
		SecureRandom random = new SecureRandom();
		MessageEncrypter encrypter = new MessageEncrypter(random);
		if (args[0].equals("generate")) {
			if (args.length != 3) {
				printUsage();
				return;
			}
			// Generate a key pair
			AsymmetricCipherKeyPair keyPair = encrypter.generateKeyPair();
			ECPublicKeyParameters publicKey =
					(ECPublicKeyParameters) keyPair.getPublic();
			byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
			PrintStream out = new PrintStream(new FileOutputStream(args[1]));
			out.print(StringUtils.toHexString(publicKeyBytes));
			out.flush();
			out.close();
			ECPrivateKeyParameters privateKey =
					(ECPrivateKeyParameters) keyPair.getPrivate();
			out = new PrintStream(new FileOutputStream(args[2]));
			out.print(privateKey.getD().toString(16).toUpperCase());
			out.flush();
			out.close();
		} else if (args[0].equals("encrypt")) {
			if (args.length != 2) {
				printUsage();
				return;
			}
			// Encrypt a decrypted message
			InputStream in = new FileInputStream(args[1]);
			byte[] publicKey = StringUtils.fromHexString(readFully(in).trim());
			String message = readFully(System.in);
			byte[] plaintext = message.getBytes(Charset.forName("UTF-8"));
			byte[] ciphertext = encrypter.encrypt(publicKey, plaintext);
			System.out.println(AsciiArmour.wrap(ciphertext, LINE_LENGTH));
		} else if (args[0].equals("decrypt")) {
			if (args.length != 2) {
				printUsage();
				return;
			}
			// Decrypt an encrypted message
			InputStream in = new FileInputStream(args[1]);
			byte[] b = StringUtils.fromHexString(readFully(in).trim());
			BigInteger d = new BigInteger(1, b);
			ECPrivateKeyParameters privateKey = new ECPrivateKeyParameters(d,
					PARAMETERS);
			byte[] ciphertext = AsciiArmour.unwrap(readFully(System.in));
			byte[] plaintext = encrypter.decrypt(privateKey, ciphertext);
			System.out.println(new String(plaintext, Charset.forName("UTF-8")));
		} else {
			printUsage();
		}
	}

	private static void printUsage() {
		System.err.println("Usage:");
		System.err.println("MessageEncrypter generate <public_key_file> <private_key_file>");
		System.err.println("MessageEncrypter encrypt <public_key_file>");
		System.err.println("MessageEncrypter decrypt <private_key_file>");
	}

	private static String readFully(InputStream in) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		Scanner scanner = new Scanner(in);
		while (scanner.hasNextLine()) {
			stringBuilder.append(scanner.nextLine());
			stringBuilder.append(System.lineSeparator());
		}
		scanner.close();
		in.close();
		return stringBuilder.toString();
	}
}
