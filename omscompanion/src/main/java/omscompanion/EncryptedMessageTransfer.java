package omscompanion;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class EncryptedMessageTransfer extends MessageComposer {
	private final String message;
	public static final String[] RSA_TRANSFORMATION = { "RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
			"RSA/ECB/OAEPWithSHA-256AndMGF1Padding" };

	public EncryptedMessageTransfer(byte[] message, String type, RSAPublicKey rsaPublicKey, int transformationIdx)
			throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, IllegalBlockSizeException,
			BadPaddingException, InvalidKeyException {

		List<String> list = new ArrayList<>();

		// (1) application-ID
		list.add(Integer.toString(APPLICATION_ENCRYPTED_MESSAGE_TRANSFER));

		// (2) transformation No.
		list.add(Integer.toString(transformationIdx));

		// (3) fingerprint
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		RSAPublicKey rsapk = rsaPublicKey;

		byte[] modulus = rsapk.getModulus().toByteArray();
		byte[] publicExp = rsapk.getPublicExponent().toByteArray();

		sha256.update(modulus);

		list.add(Base64.getEncoder().encodeToString(sha256.digest(publicExp)));

		// (4) cipher text
		byte[] cipherText = encrypt(message, type, rsaPublicKey, transformationIdx);
		list.add(Base64.getEncoder().encodeToString(cipherText));

		this.message = list.stream().collect(Collectors.joining("\t"));
	}

	@Override
	public String getMessage() {
		return message;
	}

	protected byte[] encrypt(byte[] message, String type, RSAPublicKey rsaPublicKey, int transformationIdx)
			throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
			IOException, InvalidKeyException {

		List<String> list = new ArrayList<>();

		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] hash = sha256.digest(message);

		// (1) type
		list.add(type);

		// (2) data
		list.add(Base64.getEncoder().encodeToString(message));

		// (3) hash
		list.add(Base64.getEncoder().encodeToString(hash));

		String s = list.stream().collect(Collectors.joining("\t"));

		byte[] mArr = s.getBytes();

		Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION[transformationIdx]);
		cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
		byte[] cipherText = cipher.doFinal(mArr);

		return cipherText;
	}

}
