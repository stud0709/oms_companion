package omscompanion.crypto;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.Main;

public final class RSAUtils {
	private static final String PROP_RSA_KEY_LENGTH = "rsa_key_length",
			PROP_RSA_TRANSFORMATION_IDX = "rsa_transformation_idx";
	private static final int DEF_RSA_KEY_LENGTH = 2048, DEF_RSA_TRANSFORMATION = 0;

	private RSAUtils() {

	}

	public static int getRsaTransformationIdx() {
		return Integer.parseInt(Main.properties.getProperty(PROP_RSA_TRANSFORMATION_IDX, "" + DEF_RSA_TRANSFORMATION));
	}

	public static RsaTransformation getRsaTransformation() {
		return RsaTransformation.values()[getRsaTransformationIdx()];
	}

	public static int getKeyLength() {
		return Math.max(Integer.parseInt(Main.properties.getProperty(PROP_RSA_KEY_LENGTH, "" + DEF_RSA_KEY_LENGTH)),
				DEF_RSA_KEY_LENGTH);
	}

	public static byte[] getFingerprint(RSAPublicKey rsaPublicKey) throws NoSuchAlgorithmException {
		var sha256 = MessageDigest.getInstance("SHA-256");

		var modulus = rsaPublicKey.getModulus().toByteArray();
		var publicExp = rsaPublicKey.getPublicExponent().toByteArray();

		sha256.update(modulus);
		return sha256.digest(publicExp);
	}

	public static byte[] process(int cipherMode, PublicKey rsaPublicKey, String transformation, byte[] data)
			throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException {

		var cipher = Cipher.getInstance(transformation);
		cipher.init(cipherMode, rsaPublicKey);

		return cipher.doFinal(data);
	}

	/**
	 * Retrieve public key from X509 format
	 * 
	 * @param encoded
	 * @return
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 */
	public static RSAPublicKey getFromX509(byte[] encoded) throws InvalidKeySpecException, NoSuchAlgorithmException {
		var publicKeySpec = new X509EncodedKeySpec(encoded);
		var keyFactory = KeyFactory.getInstance("RSA");
		return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
	}

}
