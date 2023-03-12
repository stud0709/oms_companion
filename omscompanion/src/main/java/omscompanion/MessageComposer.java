package omscompanion;

import java.util.Base64;
import java.util.regex.Pattern;

public abstract class MessageComposer {
	public static final int APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER = 0,
			APPLICATION_ENCRYPTED_MESSAGE_TRANSFER = 1;

	/**
	 * Prefix of a text encoded message.
	 */
	public static final String OMS_PREFIX = "oms00_";
	/**
	 * Text encoded OMS messages begin with omsXX_ with XX being the protocol
	 * version.
	 */
	public static final Pattern OMS_PATTERN = Pattern.compile("oms([0-9a-f]{2})_");

	public abstract String getMessage();

	public static String decode(String omsText) {
		String result = null;

		var m = OMS_PATTERN.matcher(omsText);

		if (!m.find()) // not a valid OMS message
			return result;

		var version = Integer.parseInt(m.group(1));

		// (1) remove prefix and line breaks
		omsText = omsText.substring(m.group().length());
		omsText = omsText.replaceAll("\\s+", "");

		switch (version) {
		case 0:
			// (2) convert to byte array
			var bArr = Base64.getDecoder().decode(omsText);

			// (3) convert to string
			result = new String(bArr);
			break;
		default:
			throw new UnsupportedOperationException("Unsupported version: " + version);
		}

		return result;
	}

	/**
	 * A OneMoreSecret text format begins with {@link MessageComposer#OMS_PREFIX}.
	 * Version 00 of OMS protocol:
	 * <ol>
	 * <li>BASE64 encode {@code message}</li>
	 * <li>prepend (2) with {@link MessageComposer#OMS_PREFIX}</li>
	 * </ol>
	 * 
	 * @param message
	 * @return
	 */
	public static String encodeAsOmsText(byte[] message) {
		return OMS_PREFIX + Base64.getEncoder().encodeToString(message);
	}
}
