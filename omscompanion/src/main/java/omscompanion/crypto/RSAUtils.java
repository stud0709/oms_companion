package omscompanion.crypto;

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
		return Integer.parseInt(Main.properties.getProperty(PROP_RSA_KEY_LENGTH, "" + DEF_RSA_KEY_LENGTH));
	}

}
