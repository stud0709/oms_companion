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
	private final File encryptedFile;

	public KeyRequest(File encryptedFile) throws NoSuchAlgorithmException, IOException {
		this.encryptedFile = encryptedFile;

		// create temporary RSA key pair
		var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		var rsaKeyLength = RSAUtils.getKeyLength();
		keyPairGenerator.initialize(rsaKeyLength);
		rsaKeyPair = keyPairGenerator.generateKeyPair();
		var rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

		try (FileInputStream fis = new FileInputStream(encryptedFile);
				OmsDataInputStream dataInputStream = new OmsDataInputStream(fis);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

			// (1) application-ID
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_REQUEST);

			// (2) reference (file name)
			dataOutputStream.writeString(encryptedFile.getName());

			// (3) RSA public key
			dataOutputStream.writeByteArray(rsaPublicKey.getEncoded());

			// (4) fingerprint of the requested RSA key (from the file header)
			dataOutputStream.writeByteArray(envelope.fingerprint());

			// (5) RSA transformation index for decryption
			dataOutputStream.writeUnsignedShort(envelope.rsaTransformationIdx());

			// (6) encrypted AES key from the file header
			dataOutputStream.writeByteArray(envelope.encryptedAesSecretKey());

			this.message = baos.toByteArray();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public byte[] getMessage() {
		return message;
	}

	public void onReply(OutputStream osDecryptedFile, byte[] keyResponse)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
			BadPaddingException, FileNotFoundException, IOException, InvalidAlgorithmParameterException {

		try (
				// prepare file for reading
				var fis = new FileInputStream(encryptedFile);
				var dataInputStream = new OmsDataInputStream(fis);
				// prepare omsReply for reading
				var bais = new ByteArrayInputStream(keyResponse);
				var replyInputStream = new OmsDataInputStream(bais)) {

			// Read file header once again, position at start of (encrypted) payload
			var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

			// ***** Read reply data *****

			// (1) Application Identifier
			var applicationId = replyInputStream.readUnsignedShort();
			assert applicationId == MessageComposer.APPLICATION_KEY_RESPONSE;

			// (2) RSA transformation
			var rsaTransformation = RsaTransformation.values()[replyInputStream.readUnsignedShort()].transformation;

			// (3) RSA encrypted AES key
			var rsaEncryptedAesKey = replyInputStream.readByteArray();

			// ***** Decrypt file *****
			var cipher = Cipher.getInstance(rsaTransformation);
			cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());

			var aesSecretKeyData = cipher.doFinal(rsaEncryptedAesKey);
			var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

			AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream, osDecryptedFile, aesSecretKey,
					envelope.ivParameterSpec(), envelope.aesTransformation(), null);
		}
	}
}
