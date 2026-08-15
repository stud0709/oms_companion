package omscompanion.nostr;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;

/**
 * Implementation of BIP-340 Schnorr Signatures for secp256k1 used in Nostr (NIP-01).
 */
public class Bip340 {
	private static final X9ECParameters CURVE_PARAMS = SECNamedCurves.getByName("secp256k1");
	private static final ECDomainParameters DOMAIN_PARAMS = new ECDomainParameters(
			CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
	private static final BigInteger N = DOMAIN_PARAMS.getN();
	private static final BigInteger P = CURVE_PARAMS.getCurve().getField().getCharacteristic();
	private static final SecureRandom RANDOM = new SecureRandom();

	public static byte[] generatePrivateKey() {
		byte[] priv = new byte[32];
		while (true) {
			RANDOM.nextBytes(priv);
			BigInteger k = new BigInteger(1, priv);
			if (k.compareTo(BigInteger.ONE) >= 0 && k.compareTo(N.subtract(BigInteger.ONE)) <= 0) {
				return priv;
			}
		}
	}

	public static byte[] getPublicKey(byte[] privateKey) {
		BigInteger d = new BigInteger(1, privateKey);
		ECPoint pt = DOMAIN_PARAMS.getG().multiply(d).normalize();
		return to32Bytes(pt.getAffineXCoord().toBigInteger());
	}

	public static byte[] sign(byte[] privateKey, byte[] msg) {
		BigInteger dPrime = new BigInteger(1, privateKey);
		if (dPrime.compareTo(BigInteger.ONE) < 0 || dPrime.compareTo(N.subtract(BigInteger.ONE)) > 0) {
			throw new IllegalArgumentException("Private key out of range");
		}

		ECPoint ptP = DOMAIN_PARAMS.getG().multiply(dPrime).normalize();
		BigInteger d = dPrime;
		if (!hasEvenY(ptP)) {
			d = N.subtract(d);
		}

		byte[] pk = to32Bytes(ptP.getAffineXCoord().toBigInteger());

		byte[] aux = new byte[32];
		RANDOM.nextBytes(aux);
		byte[] t = xor(to32Bytes(d), taggedHash("BIP0340/aux", aux));

		byte[] nonceInput = concat(t, pk, msg);
		byte[] rand = taggedHash("BIP0340/nonce", nonceInput);
		BigInteger kPrime = new BigInteger(1, rand).mod(N);
		if (kPrime.equals(BigInteger.ZERO)) {
			throw new IllegalStateException("kPrime is zero");
		}

		ECPoint ptR = DOMAIN_PARAMS.getG().multiply(kPrime).normalize();
		BigInteger k = kPrime;
		if (!hasEvenY(ptR)) {
			k = N.subtract(k);
		}

		byte[] rx = to32Bytes(ptR.getAffineXCoord().toBigInteger());
		byte[] challengeInput = concat(rx, pk, msg);
		byte[] challenge = taggedHash("BIP0340/challenge", challengeInput);
		BigInteger e = new BigInteger(1, challenge).mod(N);

		BigInteger s = k.add(e.multiply(d)).mod(N);

		return concat(rx, to32Bytes(s));
	}

	public static boolean verify(byte[] pubKey, byte[] msg, byte[] sig) {
		if (pubKey.length != 32 || sig.length != 64) {
			return false;
		}

		BigInteger rx = new BigInteger(1, Arrays.copyOfRange(sig, 0, 32));
		BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 32, 64));

		if (rx.compareTo(P) >= 0 || s.compareTo(N) >= 0) {
			return false;
		}

		BigInteger px = new BigInteger(1, pubKey);
		if (px.compareTo(P) >= 0) {
			return false;
		}

		ECPoint ptP = liftX(px);
		if (ptP == null) {
			return false;
		}

		byte[] challengeInput = concat(to32Bytes(rx), pubKey, msg);
		byte[] challenge = taggedHash("BIP0340/challenge", challengeInput);
		BigInteger e = new BigInteger(1, challenge).mod(N);

		// Compute R = s*G - e*P
		ECPoint sG = DOMAIN_PARAMS.getG().multiply(s);
		ECPoint eP = ptP.multiply(e);
		ECPoint ptR = sG.subtract(eP).normalize();

		if (ptR.isInfinity() || !hasEvenY(ptR)) {
			return false;
		}

		return ptR.getAffineXCoord().toBigInteger().equals(rx);
	}

	public static byte[] taggedHash(String tag, byte[] msg) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] tagHash = sha256.digest(tag.getBytes(StandardCharsets.UTF_8));
			sha256.update(tagHash);
			sha256.update(tagHash);
			sha256.update(msg);
			return sha256.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private static ECPoint liftX(BigInteger x) {
		try {
			// y^2 = x^3 + 7 mod p
			BigInteger ySq = x.modPow(BigInteger.valueOf(3), P).add(BigInteger.valueOf(7)).mod(P);
			BigInteger y = ySq.modPow(P.add(BigInteger.ONE).divide(BigInteger.valueOf(4)), P);
			if (!y.modPow(BigInteger.valueOf(2), P).equals(ySq)) {
				return null;
			}
			if (y.testBit(0)) {
				y = P.subtract(y);
			}
			return CURVE_PARAMS.getCurve().createPoint(x, y);
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean hasEvenY(ECPoint pt) {
		return !pt.getAffineYCoord().toBigInteger().testBit(0);
	}

	public static byte[] to32Bytes(BigInteger val) {
		byte[] raw = val.toByteArray();
		byte[] out = new byte[32];
		if (raw.length == 32) {
			return raw;
		} else if (raw.length > 32) {
			System.arraycopy(raw, raw.length - 32, out, 0, 32);
		} else {
			System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
		}
		return out;
	}

	private static byte[] xor(byte[] a, byte[] b) {
		byte[] res = new byte[Math.min(a.length, b.length)];
		for (int i = 0; i < res.length; i++) {
			res[i] = (byte) (a[i] ^ b[i]);
		}
		return res;
	}

	private static byte[] concat(byte[]... arrays) {
		int total = 0;
		for (byte[] a : arrays) {
			total += a.length;
		}
		byte[] out = new byte[total];
		int offset = 0;
		for (byte[] a : arrays) {
			System.arraycopy(a, 0, out, offset, a.length);
			offset += a.length;
		}
		return out;
	}

	public static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public static byte[] hexToBytes(String hex) {
		int len = hex.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
					+ Character.digit(hex.charAt(i + 1), 16));
		}
		return data;
	}
}
