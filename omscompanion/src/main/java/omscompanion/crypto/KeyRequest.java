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
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.Main;
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

			System.out.println("Creating key request");

			// (1) application-ID
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_REQUEST);
			System.out.println("Application-ID: %s".formatted(MessageComposer.APPLICATION_KEY_REQUEST));

			// (2) reference (file name)
			dataOutputStream.writeString(encryptedFile.getName());
			System.out.println("Reference: %s".formatted(encryptedFile.getName()));

			// (3) RSA public key
			dataOutputStream.writeByteArray(rsaPublicKey.getEncoded());
			System.out.println("RSA public key: %s".formatted(Main.byteArrayToHex(rsaPublicKey.getEncoded())));

			// (4) fingerprint of the requested RSA key (from the file header)
			dataOutputStream.writeByteArray(envelope.fingerprint());
			System.out.println("fingerprint (file header): %s".formatted(Main.byteArrayToHex(envelope.fingerprint())));

			// (5) RSA transformation index for decryption
			dataOutputStream.writeUnsignedShort(envelope.rsaTransormation().ordinal());
			System.out.println(
					"RSA transformation (file header): %s".formatted(envelope.rsaTransormation().transformation));

			// (5) RSA transformation index for key request (compatible with this device)
			dataOutputStream.writeUnsignedShort(RSAUtils.getTransformation().ordinal());
			System.out.println(
					"RSA transformation (key request): %s".formatted(RSAUtils.getTransformation().transformation));

			// (7) encrypted AES key from the file header
			dataOutputStream.writeByteArray(envelope.encryptedAesSecretKey());
			System.out.println("encrypted AES key (file header): %s"
					.formatted(Main.byteArrayToHex(envelope.encryptedAesSecretKey())));

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
				var keyResponseInputStream = new OmsDataInputStream(bais)) {

			System.out.println("Processing key response");

			// (1) Application Identifier
			var applicationId = keyResponseInputStream.readUnsignedShort();
			assert applicationId == MessageComposer.APPLICATION_KEY_RESPONSE;
			System.out.println("Application-ID: %d".formatted(applicationId));

			// (3) RSA encrypted AES key
			var rsaEncryptedAesKey = keyResponseInputStream.readByteArray();
			System.out.println("encrypted AES key: %s".formatted(Main.byteArrayToHex(rsaEncryptedAesKey)));

			// Decrypt AES key
			var aesKeyMaterial = RSAUtils.process(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate(), RSAUtils.getTransformation(),
					rsaEncryptedAesKey);

			// Read file header once again, position at start of (encrypted) payload
			var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

			AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream, osDecryptedFile, aesKeyMaterial, envelope.iv(),
					envelope.aesTransformation(), null);

			Arrays.fill(aesKeyMaterial, (byte) 0); // wipe AES key data
		}
	}
}
