package omscompanion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import omscompanion.crypto.AESUtil;
import omscompanion.crypto.AesTransformation;
import omscompanion.crypto.RSAUtils;
import omscompanion.crypto.RsaTransformation;

public abstract class MessageComposer {
	public static final int APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER = 0,
			APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED = 1, APPLICATION_TOTP_URI_TRANSFER = 2,
			APPLICATION_ENCRYPTED_FILE = 3, APPLICATION_KEY_REQUEST = 4, APPLICATION_KEY_RESPONSE = 5,
			/**
			 * Until now, it was possible to understand what kind of information is
			 * contained in the message. The generic message will only allow to decrypt it,
			 * all other information will be found inside.
			 */
			APPLICATION_RSA_AES_GENERIC = 6, APPLICATION_BITCOIN_ADDRESS = 7, APPLICATION_ENCRYPTED_MESSAGE = 8,
			APPLICATION_TOTP_URI = 9, APPLICATION_WIFI_PAIRING = 10, APPLICATION_KEY_REQUEST_PAIRING = 11;

	/**
	 * Prefix of a text encoded message.
	 */
	public static final String OMS_PREFIX = "oms00_";

	public static final String OMS_FILE_TYPE = "oms00";

	/**
	 * Text encoded OMS messages begin with omsXX_ with XX being the protocol
	 * version.
	 */
	public static final Pattern OMS_PATTERN = Pattern.compile("oms([0-9a-f]{2})_");

	public record RsaAesEnvelope(int applicationId, int rsaTransformationIdx, String rsaTransormation,
			byte[] fingerprint, int aesTransformationIdx, String aesTransformation, byte[] iv,
			byte[] encryptedAesSecretKey) {

		public IvParameterSpec ivParameterSpec() {
			return new IvParameterSpec(this.iv());
		}
	}

	public static byte[] decode(String omsText) {
		var m = OMS_PATTERN.matcher(omsText);

		if (!m.find()) // not a valid OMS message
			return null;

		byte[] result;

		var version = Integer.parseInt(Objects.requireNonNull(m.group(1)));

		// (1) remove prefix and line breaks
		omsText = omsText.substring(m.group().length());
		omsText = omsText.replaceAll("\\s+", "");

		if (version == 0) {
			// (2) convert to byte array
			result = Base64.getDecoder().decode(omsText);
		} else {
			throw new UnsupportedOperationException("Unsupported version: " + version);
		}

		return result;
	}

	/**
	 * A OneMoreSecret text format begins with {@link MessageComposer#OMS_PREFIX}.
	 * Version 00 of OMS protocol:
	 * <ol>
	 * <li>BASE64 encode {@code message}</li>
	 * <li>prepend (1) with {@link MessageComposer#OMS_PREFIX}</li>
	 * </ol>
	 * 
	 * @param message
	 * @return
	 */
	public static String encodeAsOmsText(byte[] message) {
		return OMS_PREFIX + Base64.getEncoder().encodeToString(message);
	}

	/**
	 * Wrap a byte array into RSA x AES Envelope.
	 * 
	 * @see MessageComposer#createRsaAesEnvelope(int, RSAPublicKey, int, int, int,
	 *      byte[])
	 * @param rsaPublicKey
	 * @param rsaTransformationIdx
	 * @param aesKeyLength
	 * @param aesTransformationIdx
	 * @param payload
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 */
	public static byte[] createRsaAesEnvelope(RSAPublicKey rsaPublicKey, int rsaTransformationIdx, int aesKeyLength,
			int aesTransformationIdx, byte[] payload) throws NoSuchAlgorithmException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {

		return createRsaAesEnvelope(MessageComposer.APPLICATION_RSA_AES_GENERIC, rsaPublicKey, rsaTransformationIdx,
				aesKeyLength, aesTransformationIdx, payload);
	}

	/**
	 * Wrap a byte array into RSA x AES Envelope. This is a foundation format for
	 * OneMoreSecret messages.
	 * 
	 * @see MessageComposer#prepareRsaAesEnvelope(OmsDataOutputStream, int,
	 *      RSAPublicKey, int, int, int)
	 * @param applicationId
	 * @param rsaPublicKey
	 * @param rsaTransformationIdx
	 * @param aesKeyLength
	 * @param aesTransformationIdx
	 * @param payload
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 */
	public static byte[] createRsaAesEnvelope(int applicationId, RSAPublicKey rsaPublicKey, int rsaTransformationIdx,
			int aesKeyLength, int aesTransformationIdx, byte[] payload)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException {

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			var aesEncryptionParameters = prepareRsaAesEnvelope(dataOutputStream, applicationId, rsaPublicKey,
					rsaTransformationIdx, aesKeyLength, aesTransformationIdx);

// (7) AES-encrypted message
			dataOutputStream.writeByteArray(AESUtil.process(Cipher.ENCRYPT_MODE, payload,
					aesEncryptionParameters.secretKey(), aesEncryptionParameters.iv(),
					AesTransformation.values()[aesTransformationIdx].transformation));

			return baos.toByteArray();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public record AesEncryptionParameters(SecretKey secretKey, IvParameterSpec iv) {
	}

	/**
	 * RSA x AES envelope is a foundation of OneMoreSecret messages. Every message
	 * is encrypted by a unique AES key, the key itself is then encrypted by public
	 * RSA key. You must be the holder of the corresponding RSA private key to
	 * decrypt the message.
	 * 
	 * Encryption parameters as well as the message type (aka application ID) are
	 * embedded into the message header to allow for decryption. See source code for
	 * more details.
	 * 
	 * @param dataOutputStream
	 * @param applicationId
	 * @param rsaPublicKey
	 * @param rsaTransformationIdx
	 * @param aesKeyLength
	 * @param aesTransformationIdx
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 */
	public static AesEncryptionParameters prepareRsaAesEnvelope(OmsDataOutputStream dataOutputStream, int applicationId,
			RSAPublicKey rsaPublicKey, int rsaTransformationIdx, int aesKeyLength, int aesTransformationIdx)
			throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException,
			IllegalBlockSizeException, BadPaddingException {

		// init AES
		var iv = AESUtil.generateIv();
		var secretKey = AESUtil.generateRandomSecretKey(aesKeyLength);

		// encrypt AES secret key with RSA
		var cipher = Cipher.getInstance(RsaTransformation.values()[rsaTransformationIdx].transformation);
		cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

		var encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

		// (1) application-ID
		dataOutputStream.writeUnsignedShort(applicationId);

		// (2) RSA transformation index
		dataOutputStream.writeUnsignedShort(rsaTransformationIdx);

		// (3) fingerprint
		dataOutputStream.writeByteArray(RSAUtils.getFingerprint(rsaPublicKey));

		// (4) AES transformation index
		dataOutputStream.writeUnsignedShort(aesTransformationIdx);

		// (5) IV
		dataOutputStream.writeByteArray(iv.getIV());

		// (6) RSA-encrypted AES secret key
		dataOutputStream.writeByteArray(encryptedSecretKey);

		return new AesEncryptionParameters(secretKey, iv);
	}

	/**
	 * Reads {@link RsaAesEnvelope } from a {@link OmsDataInputStream }. The stream
	 * should be positioned at the beginning. After the parameters have been read,
	 * the stream is left open pointing to the start of the encrypted part of the
	 * message. Now the decryption can take place.
	 * 
	 * @param dataInputStream
	 * @return
	 * @throws IOException
	 */
	public static RsaAesEnvelope readRsaAesEnvelope(OmsDataInputStream dataInputStream) throws IOException {
		// (1) Application ID
		var applicationId = dataInputStream.readUnsignedShort();

		// (2) RSA transformation index
		var rsaTransformationIdx = dataInputStream.readUnsignedShort();
		var rsaTransformation = RsaTransformation.values()[rsaTransformationIdx].transformation;

		// (3) RSA fingerprint
		var fingerprint = dataInputStream.readByteArray();

		// (4) AES transformation index
		var aesTransformationIdx = dataInputStream.readUnsignedShort();
		var aesTransformation = AesTransformation.values()[aesTransformationIdx].transformation;

		// (5) IV
		var iv = dataInputStream.readByteArray();

		// (6) RSA-encrypted AES secret key
		var encryptedAesSecretKey = dataInputStream.readByteArray();

		// (7) AES-encrypted message <= leave here

		return new RsaAesEnvelope(applicationId, rsaTransformationIdx, rsaTransformation, fingerprint,
				aesTransformationIdx, aesTransformation, iv, encryptedAesSecretKey);
	}
}
