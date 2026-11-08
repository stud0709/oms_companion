package omscompanion;

import org.onemoresecret.protocol.OmsProtocol;
import java.io.ByteArrayInputStream;
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

import omscompanion.crypto.AESUtil;
import omscompanion.crypto.AesTransformation;
import omscompanion.crypto.RSAUtils;
import omscompanion.crypto.RsaTransformation;

public abstract class MessageComposer {
	public static final int APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER = 0,
			APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED = 1, APPLICATION_TOTP_URI_TRANSFER = 2,
			APPLICATION_ENCRYPTED_FILE = 3, APPLICATION_KEY_REQUEST = 4, APPLICATION_KEY_RESPONSE = 5,
			/*
			 * Until now, it was possible to understand what kind of information is
			 * contained in the message. The generic message will only allow to decrypt it,
			 * all other information will be found inside.
			 */
			APPLICATION_RSA_AES_GENERIC = 6, APPLICATION_BITCOIN_ADDRESS = 7, APPLICATION_ENCRYPTED_MESSAGE = 8,
			APPLICATION_TOTP_URI = 9, APPLICATION_NOSTR_PAIRING = 10, APPLICATION_KEY_REQUEST_PAIRING = 11, APPLICATION_ENCRYPTED_OTP = 12;

	/**
	 * Prefix of a legacy v0 text encoded message. V1 messages are bare Base64.
	 */
	public static final String OMS_PREFIX = "oms00_";

	public static final String OMS_FILE_TYPE = "oms";

	/**
	 * Legacy text prefix pattern. Current message detection uses OmsProtocol.decodeText.
	 */
	public static final Pattern OMS_PATTERN = Pattern.compile("oms([0-9a-f]{2})_");

	public record RsaAesEnvelope(int applicationId, RsaTransformation rsaTransormation,
			byte[] fingerprint, AesTransformation aesTransformation, byte[] iv,
			byte[] encryptedAesSecretKey, OmsProtocol.Header protocolHeader) {}
	
	public static byte[] decode(String omsText) {
        return OmsProtocol.decodeText(omsText);
    }

	/**
	 * Encode v1 messages as bare Base64. Legacy v0 messages retain
	 * {@link MessageComposer#OMS_PREFIX} before their Base64 data.
	 * 
	 * @param message
	 * @return
	 */
	public static String encodeAsOmsText(byte[] message) {
        return OmsProtocol.encodeText(message);
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
	public static byte[] createRsaAesEnvelope(RSAPublicKey rsaPublicKey, RsaTransformation rsaTransformation,
			int aesKeyLength, AesTransformation aesTransformation, byte[] payload)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException {

		return createRsaAesEnvelope(MessageComposer.APPLICATION_RSA_AES_GENERIC, rsaPublicKey, rsaTransformation,
				aesKeyLength, aesTransformation, payload);
	}

	/**
	 * Wrap a byte array into RSA x AES Envelope. This is a foundation format for
	 * OneMoreSecret messages.
	 * 
	 * @see MessageComposer#prepareRsaAesEnvelope(OmsDataOutputStream, int, RSAPublicKey, RsaTransformation, int, AesTransformation)
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
	public static byte[] createRsaAesEnvelope(int applicationId, RSAPublicKey rsaPublicKey,
			RsaTransformation rsaTransformation, int aesKeyLength, AesTransformation aesTransformation, byte[] payload)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			InvalidKeyException, InvalidAlgorithmParameterException {
        if (applicationId == APPLICATION_RSA_AES_GENERIC) {
            try (var input = new OmsDataInputStream(new ByteArrayInputStream(payload))) {
                int innerId = input.readUnsignedShort(); byte[] innerBody = input.readByteArray();
                if (input.read() != -1) throw new IOException("Trailing generic payload");
                return OmsProtocol.seal(rsaPublicKey, innerId, innerBody);
            } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        }
        return OmsProtocol.seal(rsaPublicKey, applicationId, payload);
    }

	public record AesEncryptionParameters(byte[] aesKeyMaterial, byte[] iv) {
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
	 * @throws InvalidAlgorithmParameterException 
	 */
	public static AesEncryptionParameters prepareRsaAesEnvelope(OmsDataOutputStream dataOutputStream, int applicationId,
			RSAPublicKey rsaPublicKey, RsaTransformation rsaTransformation, int aesKeyLength,
			AesTransformation aesTransformation) throws NoSuchAlgorithmException, IOException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		System.out.println("Preparing envelope:");
		
		// encrypt AES secret key with RSA
		var aesKeyMaterial = AESUtil.generateRandomSecretKey(aesKeyLength);
		var encryptedSecretKey = RSAUtils.process(Cipher.ENCRYPT_MODE, rsaPublicKey, rsaTransformation, aesKeyMaterial);

		// (1) application-ID
		dataOutputStream.writeUnsignedShort(applicationId);
		System.out.println("Application-ID: %d".formatted(applicationId));

		// (2) RSA transformation index
		dataOutputStream.writeUnsignedShort(rsaTransformation.ordinal());
		System.out.println("RSA transformation: %d = %s".formatted(rsaTransformation.ordinal(), rsaTransformation.transformation));

		// (3) fingerprint
		var fingerprint = RSAUtils.getFingerprint(rsaPublicKey);
		dataOutputStream.writeByteArray(RSAUtils.getFingerprint(rsaPublicKey));
		System.out.println("RSA fingerprint: %s".formatted(Main.byteArrayToHex(fingerprint)));

		// (4) AES transformation index
		dataOutputStream.writeUnsignedShort(aesTransformation.ordinal());
		System.out.println("AES transformation: %d = %s".formatted(aesTransformation.ordinal(), aesTransformation.transformation));

		// (5) IV
		var iv = AESUtil.generateIv(aesTransformation);		
		dataOutputStream.writeByteArray(iv);
		System.out.println("IV: %s".formatted(Main.byteArrayToHex(iv)));

		// (6) RSA-encrypted AES secret key
		dataOutputStream.writeByteArray(encryptedSecretKey);
		System.out.println("Encrypted AES key: %s".formatted(Main.byteArrayToHex(encryptedSecretKey)));

		return new AesEncryptionParameters(aesKeyMaterial, iv);
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
		System.out.println("Reading envelope:");
		// (1) Application ID
		var applicationId = dataInputStream.readUnsignedShort();
        if (applicationId == 0x254f) {
            var header = OmsProtocol.readHeader(dataInputStream, applicationId);
            if (header.kind != OmsProtocol.RSA) throw new IOException("Not an RSA envelope");
            return new RsaAesEnvelope(APPLICATION_RSA_AES_GENERIC, RsaTransformation.values()[2], header.fingerprint,
                AesTransformation.values()[2], header.noncePrefix, header.wrappedKey, header);
        }
		System.out.println("application-ID: %d".formatted(applicationId));

		// (2) RSA transformation index
		var rsaTransformationIdx = dataInputStream.readUnsignedShort();
		var rsaTransformation = RsaTransformation.values()[rsaTransformationIdx];
		System.out.println("RSA transformation: %d = %s".formatted(rsaTransformationIdx, rsaTransformation.transformation));

		// (3) RSA fingerprint
		var fingerprint = dataInputStream.readByteArray();
		System.out.println("fingerprint: %s".formatted(Main.byteArrayToHex(fingerprint)));

		// (4) AES transformation index
		var aesTransformationIdx = dataInputStream.readUnsignedShort();
		var aesTransformation = AesTransformation.values()[aesTransformationIdx];
		System.out.println("AES transformation: %d = %s".formatted(aesTransformationIdx, aesTransformation.transformation));

		// (5) IV
		var iv = dataInputStream.readByteArray();
		System.out.println("IV: %s".formatted(Main.byteArrayToHex(iv)));

		// (6) RSA-encrypted AES secret key
		var encryptedAesSecretKey = dataInputStream.readByteArray();
		System.out.println("Encrypted AES key: %s".formatted(Main.byteArrayToHex(encryptedAesSecretKey)));

		// (7) AES-encrypted message <= leave here

		return new RsaAesEnvelope(applicationId, rsaTransformation, fingerprint,
				aesTransformation, iv, encryptedAesSecretKey, null);
	}
}
