package omscompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.junit.jupiter.api.Test;

import omscompanion.crypto.AESUtil;

class AESUtilTest {

	@Test
	void testEncrypt() throws Exception {
		IvParameterSpec iv = AESUtil.generateIv();
		byte[] salt = AESUtil.generateSalt();
		SecretKey secretKey = AESUtil.getSecretKeyFromPassword("secret".toCharArray(), salt);

		String text = "The quick brown fox jumped over the lazy dog";

		byte[] cipherText = AESUtil.encrypt(text.getBytes(), secretKey, iv, AESUtil.AES_TRANSFORMATION);

		String _text = new String(AESUtil.decrypt(cipherText, secretKey, iv, AESUtil.AES_TRANSFORMATION));

		assertEquals(_text, text);
	}

}
