package omscompanion;

import java.util.Base64;
import java.util.regex.Pattern;

public abstract class MessageComposer {
	public static final int APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER = 0, APPLICATION_ENCRYPTED_MESSAGE_TRANSFER = 1;

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

	/**
	 * You can pass messages through the clipboard. A message begins with
	 * {@link MessageComposer#OMS_PREFIX}. Version 00 of OMS protocol:
	 * <ol>
	 * <li>converts the message to byte array</li>
	 * <li>BASE64 encode (1)</li></li>prepend (2) with
	 * {@link MessageComposer#OMS_PREFIX}
	 * </ol>
	 * 
	 * @return
	 */
	public static String asText(String message) {
		return OMS_PREFIX + Base64.getEncoder().encodeToString(message.getBytes());
	}
}
