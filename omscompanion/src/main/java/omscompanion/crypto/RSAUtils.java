package omscompanion.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

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

}
