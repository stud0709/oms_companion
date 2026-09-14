package omscompanion.nostr;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Implementation of BIP-340 Schnorr Signatures for secp256k1 used in Nostr (NIP-01).
 * Pure Java implementation with self-contained secp256k1 elliptic curve arithmetic.
 */
public class Bip340 {
	public static final BigInteger P = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
	public static final BigInteger N = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
	private static final Secp256k1Point G = new Secp256k1Point(
			new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
			new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
			BigInteger.ONE
	);
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
		Secp256k1Point pt = G.multiply(d).normalize();
		return to32Bytes(pt.x);
	}

	public static byte[] sign(byte[] privateKey, byte[] msg) {
		BigInteger dPrime = new BigInteger(1, privateKey);
		if (dPrime.compareTo(BigInteger.ONE) < 0 || dPrime.compareTo(N.subtract(BigInteger.ONE)) > 0) {
			throw new IllegalArgumentException("Private key out of range");
		}

		Secp256k1Point ptP = G.multiply(dPrime).normalize();
		BigInteger d = dPrime;
		if (!hasEvenY(ptP)) {
			d = N.subtract(d);
		}

		byte[] pk = to32Bytes(ptP.x);

		byte[] aux = new byte[32];
		RANDOM.nextBytes(aux);
		byte[] t = xor(to32Bytes(d), taggedHash("BIP0340/aux", aux));

		byte[] nonceInput = concat(t, pk, msg);
		byte[] rand = taggedHash("BIP0340/nonce", nonceInput);
		BigInteger kPrime = new BigInteger(1, rand).mod(N);
		if (kPrime.equals(BigInteger.ZERO)) {
			throw new IllegalStateException("kPrime is zero");
		}

		Secp256k1Point ptR = G.multiply(kPrime).normalize();
		BigInteger k = kPrime;
		if (!hasEvenY(ptR)) {
			k = N.subtract(k);
		}

		byte[] rx = to32Bytes(ptR.x);
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

		Secp256k1Point ptP = liftX(px);
		if (ptP == null) {
			return false;
		}

		byte[] challengeInput = concat(to32Bytes(rx), pubKey, msg);
		byte[] challenge = taggedHash("BIP0340/challenge", challengeInput);
		BigInteger e = new BigInteger(1, challenge).mod(N);

		// Compute R = s*G - e*P
		Secp256k1Point sG = G.multiply(s);
		Secp256k1Point eP = ptP.multiply(e);
		Secp256k1Point ptR = sG.add(eP.negate()).normalize();

		if (ptR.isInfinity() || !hasEvenY(ptR)) {
			return false;
		}

		return ptR.x.equals(rx);
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

	private static Secp256k1Point liftX(BigInteger x) {
		try {
			if (x.compareTo(BigInteger.ZERO) < 0 || x.compareTo(P) >= 0) {
				return null;
			}
			// y^2 = x^3 + 7 mod p
			BigInteger ySq = x.modPow(BigInteger.valueOf(3), P).add(BigInteger.valueOf(7)).mod(P);
			BigInteger y = ySq.modPow(P.add(BigInteger.ONE).divide(BigInteger.valueOf(4)), P);
			if (!y.modPow(BigInteger.valueOf(2), P).equals(ySq)) {
				return null;
			}
			if (y.testBit(0)) {
				y = P.subtract(y);
			}
			return new Secp256k1Point(x, y, BigInteger.ONE);
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean hasEvenY(Secp256k1Point pt) {
		Secp256k1Point n = pt.normalize();
		return !n.y.testBit(0);
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

	private static class Secp256k1Point {
		static final Secp256k1Point INFINITY = new Secp256k1Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO);

		final BigInteger x, y, z;

		Secp256k1Point(BigInteger x, BigInteger y, BigInteger z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		boolean isInfinity() {
			return z.equals(BigInteger.ZERO);
		}

		Secp256k1Point twice() {
			if (isInfinity() || y.equals(BigInteger.ZERO)) {
				return INFINITY;
			}
			// a = 0 for secp256k1
			BigInteger a = x.multiply(x).mod(P);
			BigInteger b = y.multiply(y).mod(P);
			BigInteger c = b.multiply(b).mod(P);
			BigInteger d = BigInteger.valueOf(4).multiply(x).multiply(b).mod(P);
			BigInteger e = BigInteger.valueOf(3).multiply(a).mod(P);
			BigInteger f = e.multiply(e).mod(P);
			BigInteger x3 = f.subtract(BigInteger.valueOf(2).multiply(d)).mod(P);
			BigInteger y3 = e.multiply(d.subtract(x3)).subtract(BigInteger.valueOf(8).multiply(c)).mod(P);
			BigInteger z3 = BigInteger.valueOf(2).multiply(y).multiply(z).mod(P);
			return new Secp256k1Point(x3, y3, z3);
		}

		Secp256k1Point add(Secp256k1Point other) {
			if (this.isInfinity()) return other;
			if (other.isInfinity()) return this;

			BigInteger z1z1 = this.z.multiply(this.z).mod(P);
			BigInteger z2z2 = other.z.multiply(other.z).mod(P);

			BigInteger u1 = this.x.multiply(z2z2).mod(P);
			BigInteger u2 = other.x.multiply(z1z1).mod(P);

			BigInteger s1 = this.y.multiply(other.z).multiply(z2z2).mod(P);
			BigInteger s2 = other.y.multiply(this.z).multiply(z1z1).mod(P);

			if (u1.equals(u2)) {
				if (!s1.equals(s2)) {
					return INFINITY;
				}
				return twice();
			}

			BigInteger h = u2.subtract(u1).mod(P);
			BigInteger i = BigInteger.valueOf(2).multiply(h).pow(2).mod(P);
			BigInteger j = h.multiply(i).mod(P);
			BigInteger r = BigInteger.valueOf(2).multiply(s2.subtract(s1)).mod(P);
			BigInteger v = u1.multiply(i).mod(P);

			BigInteger x3 = r.multiply(r).subtract(j).subtract(BigInteger.valueOf(2).multiply(v)).mod(P);
			BigInteger y3 = r.multiply(v.subtract(x3)).subtract(BigInteger.valueOf(2).multiply(s1).multiply(j)).mod(P);
			BigInteger z3 = BigInteger.valueOf(2).multiply(this.z).multiply(other.z).multiply(h).mod(P);

			return new Secp256k1Point(x3, y3, z3);
		}

		Secp256k1Point negate() {
			if (isInfinity()) return this;
			return new Secp256k1Point(x, P.subtract(y).mod(P), z);
		}

		Secp256k1Point multiply(BigInteger k) {
			k = k.mod(N);
			if (k.equals(BigInteger.ZERO) || isInfinity()) {
				return INFINITY;
			}
			Secp256k1Point res = INFINITY;
			for (int i = k.bitLength() - 1; i >= 0; i--) {
				res = res.twice();
				if (k.testBit(i)) {
					res = res.add(this);
				}
			}
			return res;
		}

		Secp256k1Point normalize() {
			if (isInfinity() || z.equals(BigInteger.ONE)) {
				return this;
			}
			BigInteger zInv = z.modInverse(P);
			BigInteger zInv2 = zInv.multiply(zInv).mod(P);
			BigInteger zInv3 = zInv2.multiply(zInv).mod(P);
			BigInteger nx = x.multiply(zInv2).mod(P);
			BigInteger ny = y.multiply(zInv3).mod(P);
			return new Secp256k1Point(nx, ny, BigInteger.ONE);
		}
	}
}
