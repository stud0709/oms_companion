package omscompanion;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class PublicTextTransfer extends MessageComposer {
	private final String message;

	public PublicTextTransfer(String text) throws NoSuchAlgorithmException, IOException {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		byte[] hash = sha256.digest(text.getBytes());

		List<String> list = new ArrayList<>();
		// (1) application-ID
		list.add(Integer.toString(APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER));
		// (2) hash
		list.add(Base64.getEncoder().encodeToString(hash));
		// (3) message
		list.add(text);

		this.message = list.stream().collect(Collectors.joining("\t"));
	}

	@Override
	public String getMessage() {
		return message;
	}

}
