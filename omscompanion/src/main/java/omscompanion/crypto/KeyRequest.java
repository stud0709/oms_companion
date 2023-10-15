package omscompanion.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import omscompanion.MessageComposer;
import omscompanion.OmsDataInputStream;
import omscompanion.OmsDataOutputStream;

/**
 * Every file is encrypted with a unique AES key, which is encrypted by RSA key
 * and stored at the beginning of the file encryption envelope. The KeyRequest
 * will request decryption of the AES key in order to decrypt the file locally.
 * To protect the AES key during the transport, a temporary RSA key will be
 * provided.
 * 
 * @see EncryptedFile
 */
public class KeyRequest {
	private final KeyPair rsaKeyPair;
	private final byte[] message;

	private record Header(int rsaTransformationIdx, byte[] rsaFingerprint, String aesTransformation, byte[] iv,
			byte[] rsaEncryptedAesSecretKey) {
	}

	public KeyRequest(File f, byte[] fingerprint) throws NoSuchAlgorithmException, IOException {
		// create temporary RSA key pair
		var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		var rsaKeyLength = RSAUtils.getKeyLength();
		keyPairGenerator.initialize(rsaKeyLength);
		rsaKeyPair = keyPairGenerator.generateKeyPair();
		var rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

		try (FileInputStream fis = new FileInputStream(f);
				OmsDataInputStream dataInputStream = new OmsDataInputStream(fis);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			var header = getHeader(dataInputStream);

			// (1) application-ID
			dataOutputStream.writeUnsignedShort(MessageComposer.KEY_REQUEST);

			// (2) RSA public key
			dataOutputStream.writeByteArray(rsaPublicKey.getEncoded());

			// (3) fingerprint from the file header
			dataOutputStream.writeByteArray(fingerprint);

			// (4) RSA transformation index
			dataOutputStream.writeUnsignedShort(header.rsaTransformationIdx);

			// (5) encrypted AES key from the file header
			dataOutputStream.writeByteArray(header.rsaEncryptedAesSecretKey);

			this.message = baos.toByteArray();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public byte[] getMessage() {
		return message;
	}

	/**
	 * OneMoreSecret will decrypt the key with the requested RSA key and protect it
	 * with the temporary RSA key (see constructor for details). This method will
	 * decrypth AES key and use it to decrypt the data.
	 * 
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws InvalidAlgorithmParameterException
	 * 
	 */
	public void onReply(File f, OutputStream os, byte[] omsReply)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, FileNotFoundException, IOException, InvalidAlgorithmParameterException {

		try (
				// prepare file for reading
				FileInputStream fis = new FileInputStream(f);
				OmsDataInputStream dataInputStream = new OmsDataInputStream(fis);
				// prepare omsReply for reading
				ByteArrayInputStream bais = new ByteArrayInputStream(omsReply);
				OmsDataInputStream replyInputStream = new OmsDataInputStream(bais)) {

			// ***** Read reply data *****

			// (1) RSA transformation index
			var rsaTransformation = RsaTransformation.values()[replyInputStream.readUnsignedShort()].transformation;

			// (2) AES key protected by the temporary RSA key
			var encryptedAESKey = replyInputStream.readByteArray();

			// ***** Decrypt file *****
			// read file header once again, position at start of (encrypted) payload
			var header = getHeader(dataInputStream);

			var cipher = Cipher.getInstance(rsaTransformation);
			cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());

			var aesSecretKeyData = cipher.doFinal(encryptedAESKey);
			var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

			AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream, os, aesSecretKey, new IvParameterSpec(header.iv),
					header.aesTransformation);
		}
	}

	private Header getHeader(OmsDataInputStream dataInputStream) throws IOException {
		// (1) Application ID
		var applicationId = dataInputStream.readUnsignedShort();
		assert applicationId == MessageComposer.APPLICATION_ENCRYPTED_FILE;

		// (2) RSA transformation index
		var rsaTransformationIdx = dataInputStream.readUnsignedShort();

		// (3) RSA fingerprint
		var fingerprint = dataInputStream.readByteArray();

		// (4) AES transformation index
		var aesTransformation = AesTransformation.values()[dataInputStream.readUnsignedShort()].transformation;

		// (5) IV
		var iv = dataInputStream.readByteArray();

		// (6) RSA-encrypted AES secret key
		var encryptedAesSecretKey = dataInputStream.readByteArray();

		// the remaining data is the payload
		return new Header(rsaTransformationIdx, fingerprint, aesTransformation, iv, encryptedAesSecretKey);
	}
}
