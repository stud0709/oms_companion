package omscompanion.crypto;

public final class RSAUtils {

	private RSAUtils() {

	}

	public static int getRsaTransformationIdx() {
		// todo: make configurable
		return 0;
	}

	public static RsaTransformation getRsaTransformation() {
		return RsaTransformation.values()[getRsaTransformationIdx()];
	}

	public static int getKeyLength() {
		// todo: make configurable
		return 2048;
	}

}
