package omscompanion.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Supplier;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.MessageComposer;
import omscompanion.OmsDataOutputStream;

public class EncryptedFile {
	public static void create(InputStream fis, File oFile, RSAPublicKey rsaPublicKey, RsaTransformation rsaTransformation,
			int aesKeyLength, AesTransformation aesTransformation, Supplier<Boolean> cancelled)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException, IOException {

		try (var fos = new FileOutputStream(oFile); var dataOutputStream = new OmsDataOutputStream(fos)) {

			var aesEncryptionParameters = MessageComposer.prepareRsaAesEnvelope(dataOutputStream,
					MessageComposer.APPLICATION_ENCRYPTED_FILE, rsaPublicKey, rsaTransformation, aesKeyLength,
					aesTransformation);

			AESUtil.process(Cipher.ENCRYPT_MODE, fis, dataOutputStream, aesEncryptionParameters.aesKeyMaterial(),
					aesEncryptionParameters.iv(),aesTransformation,
					cancelled);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}

		if (cancelled != null && cancelled.get())
			oFile.delete();
	}
}
