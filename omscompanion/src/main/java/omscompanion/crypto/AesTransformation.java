package omscompanion.crypto;

/**
 * See https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html for
 * the list of supported transformations
 * 
 * See https://developer.android.com/reference/javax/crypto/Cipher for Android
 * 
 */
public enum AesTransformation {
	AES_CBC_PKCS5Padding("AES/CBC/PKCS5Padding", 16),
	AES_CBC_PKCS7Padding("AES/CBC/PKCS7Padding", 16),
    AES_GCM_NO_PADDING("AES/GCM/NoPadding", 12);

	public final String transformation;
	public final int ivLength;

	AesTransformation(String transformation, int ivLength) {
		this.transformation = transformation;
		this.ivLength = ivLength;
	}
}
