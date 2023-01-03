package omscompanion;

import java.util.Base64;

public abstract class MessageComposer {
	public static final int APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER = 0, APPLICATION_PUBLIC_MESSAGE_TRANSFER = 1,
			APPLICATION_ENCRYPTED_MESSAGE_TRANSFER = 2;

	public static final String OMS_URL = "oms://";

	public abstract String getMessage();

	/**
	 * You can pass messages through the clipboard. A message begins with
	 * {@code oms://} followed by the base64-encoded message text (utf-8)
	 * 
	 * @return
	 */
	public String getURL() {
		return OMS_URL + Base64.getEncoder().encodeToString(getMessage().getBytes());
	}
}
