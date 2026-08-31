package omscompanion.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.OmsDataOutputStream;
import omscompanion.nostr.Bip340;
import omscompanion.nostr.NostrConnectionPool;
import omscompanion.nostr.NostrEvent;
import omscompanion.openjfx.EncryptionToolBar;
import omscompanion.openjfx.QRFrame;
import omscompanion.openjfx.RSAPublicKeyItem;

public class PairingInfo {
	private static final Logger logger = Logger.getLogger(PairingInfo.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int GCM_IV_LENGTH_BYTES = 12;

	public static final String PROP_REQUEST_TIMEOUT_S = "request_timeout_s";
	public static final String PROP_NOSTR_RELAYS = "nostr_relays";

	public static final SimpleBooleanProperty pairedProperty = new SimpleBooleanProperty(false);

	public static final List<String> DEFAULT_RELAYS = List.of(
			"wss://relay.damus.io",
			"wss://nos.lol",
			"wss://relay.snort.social",
			"wss://nostr.mom"
	);

	private static final int DEFAULT_TTL_SECONDS = 120;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static PairingInfo instance = null;

	private final byte[] nostrPrivateKey;
	private final String myPubKeyHex;
	private final String topicHex;
	private final byte[] psk;
	private final int ttlSeconds;
	private final List<String> relayUrls;
	private final NostrConnectionPool connectionPool;
	private final RSAPublicKey rsaPublicKey;

	private final AtomicBoolean paired = new AtomicBoolean(false);
	private final AtomicBoolean active = new AtomicBoolean(true);

	private final Map<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();

	public PairingInfo() {
		this(getRequestTimeoutS(), getPreferredRelays());
	}

	public PairingInfo(int ttlSeconds, List<String> relays) {
		this(ttlSeconds, relays, null, null, null, null);
	}

	public PairingInfo(int ttlSeconds, List<String> relays, byte[] psk, String topicHex, byte[] nostrPrivateKey) {
		this(ttlSeconds, relays, psk, topicHex, nostrPrivateKey, null);
	}

	public PairingInfo(int ttlSeconds, List<String> relays, byte[] psk, String topicHex, byte[] nostrPrivateKey,
			RSAPublicKey rsaPublicKey) {
		this.ttlSeconds = ttlSeconds;
		this.relayUrls = new ArrayList<>(relays);
		this.nostrPrivateKey = nostrPrivateKey != null ? nostrPrivateKey : Bip340.generatePrivateKey();
		this.myPubKeyHex = Bip340.bytesToHex(Bip340.getPublicKey(this.nostrPrivateKey));
		this.rsaPublicKey = rsaPublicKey;

		if (topicHex != null) {
			this.topicHex = topicHex;
		} else {
			byte[] topicBytes = new byte[32];
			RANDOM.nextBytes(topicBytes);
			this.topicHex = Bip340.bytesToHex(topicBytes);
		}

		if (psk != null) {
			this.psk = psk;
		} else {
			this.psk = new byte[32];
			RANDOM.nextBytes(this.psk);
		}

		this.expiresAt = System.currentTimeMillis() + (ttlSeconds >= 86400 ? (long) ttlSeconds * 1000L : 24L * 60 * 60 * 1000L);
		this.connectionPool = new NostrConnectionPool();
		this.connectionPool.addEventListener(this::onNostrEvent);
	}

	private final long expiresAt;

	public boolean isExpired() {
		return System.currentTimeMillis() > expiresAt;
	}

	public static PairingInfo getInstance() {
		synchronized (PairingInfo.class) {
			if (instance != null && instance.isExpired()) {
				logger.info("Pairing session expired after 24h. Disconnecting.");
				disconnect();
				return null;
			}
			return instance;
		}
	}

	public static void disconnect() {
		PairingInfo current;
		synchronized (PairingInfo.class) {
			current = instance;
			instance = null;
		}
		Platform.runLater(() -> pairedProperty.set(false));
		if (current != null) {
			current.terminate();
		}
	}

	public void terminate() {
		active.set(false);
		paired.set(false);
		Platform.runLater(() -> pairedProperty.set(false));

		// Send encrypted disconnect event to notify peer
		try {
			Map<String, Object> discObj = Map.of("type", "disconnect");
			String jsonStr = MAPPER.writeValueAsString(discObj);
			String encryptedContent = encryptPayload(jsonStr, psk);
			List<List<String>> tags = List.of(
					List.of("t", topicHex)
			);
			NostrEvent event = NostrEvent.create(nostrPrivateKey, 25000, tags, encryptedContent);
			var futures = connectionPool.broadcast(event);
			if (!futures.isEmpty()) {
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
						.orTimeout(1000, TimeUnit.MILLISECONDS)
						.exceptionally(ex -> null)
						.join();
			}
		} catch (Exception e) {
			logger.log(Level.FINE, "Error sending disconnect event", e);
		}

		connectionPool.close();

		for (CompletableFuture<byte[]> future : pendingRequests.values()) {
			future.complete(null);
		}
		pendingRequests.clear();

		synchronized (PairingInfo.class) {
			if (instance == this) {
				instance = null;
			}
		}

		logger.info("PairingInfo terminated and disconnected");
	}

	public static byte[] createPairingPrompt(String topicHex, byte[] psk, int ttl, List<String> relays,
			RSAPublicKey rsaPublicKey, RsaTransformation rsaTransformation, int aesKeyLength,
			AesTransformation aesTransformation) throws Exception {

		byte[] rawPairingData;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			// (1) Topic hex (64 hex characters)
			dataOutputStream.writeString(topicHex);

			// (2) Pre-Shared Key (32 bytes)
			dataOutputStream.writeByteArray(psk);

			// (3) TTL in seconds
			dataOutputStream.writeUnsignedShort(ttl);

			// (4) Number of relays
			dataOutputStream.writeUnsignedShort(relays.size());

			// (5) Relay URLs
			for (String relay : relays) {
				dataOutputStream.writeString(relay);
			}

			rawPairingData = baos.toByteArray();
		}

		byte[] innerPayload = EncryptedMessage.createPayload(MessageComposer.APPLICATION_NOSTR_PAIRING, rawPairingData);

		return MessageComposer.createRsaAesEnvelope(rsaPublicKey, rsaTransformation, aesKeyLength, aesTransformation,
				innerPayload);
	}

	public RSAPublicKey getRsaPublicKey() throws Exception {
		if (rsaPublicKey != null) {
			return rsaPublicKey;
		}
		return getDefaultPublicKey();
	}

	public static RSAPublicKey getDefaultPublicKey() throws Exception {
		if (!Files.exists(Main.PUBLIC_KEY_STORAGE)) {
			throw new IllegalStateException("Public key directory does not exist: " + Main.PUBLIC_KEY_STORAGE);
		}
		var publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE)
				.filter(fn -> fn.getFileName().toString().toLowerCase()
						.endsWith(EncryptionToolBar.FILE_TYPE_PUCLIC_KEY))
				.map(p -> new RSAPublicKeyItem(p))
				.filter(p -> p.publicKey != null)
				.collect(Collectors.toList());

		if (publicKeys.isEmpty()) {
			throw new IllegalStateException(
					String.format("Cannot pair: no RSA public keys found in %s", Main.PUBLIC_KEY_STORAGE.toAbsolutePath()));
		}

		String defaultKeyAlias = Main.getDefaultKeyAlias();
		if (defaultKeyAlias != null) {
			for (RSAPublicKeyItem item : publicKeys) {
				if (item.toString().equals(defaultKeyAlias)) {
					return item.publicKey;
				}
			}
		}
		return publicKeys.get(0).publicKey;
	}

	public void displayPrompt(Runnable andThen) throws Exception {
		RSAPublicKey key = getRsaPublicKey();
		byte[] promptBytes = createPairingPrompt(topicHex, psk, ttlSeconds, relayUrls, key,
				RSAUtils.getTransformation(), AESUtil.getKeyLength(), AESUtil.getTransformation());

		// Connect to Nostr relays and subscribe
		connectionPool.connect(relayUrls);
		connectionPool.subscribe(topicHex);

		String prefix = topicHex.length() >= 8 ? topicHex.substring(0, 8) + "..." : topicHex;
		String infoText = "Topic Prefix: " + prefix;

		// Show QR code dialog
		QRFrame.showForMessage(promptBytes, false, false, "Nostr Pairing", infoText, s -> {
			// If QR dialog was dismissed without successful pairing
			if (!paired.get()) {
				terminate();
				if (andThen != null) {
					andThen.run();
				}
			}
		});
	}

	public static String encryptPayload(String plaintext, byte[] key) throws Exception {
		byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
		RANDOM.nextBytes(iv);
		Cipher cipher = Cipher.getInstance(AES_GCM);
		GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
		byte[] cipherWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
		byte[] combined = new byte[iv.length + cipherWithTag.length];
		System.arraycopy(iv, 0, combined, 0, iv.length);
		System.arraycopy(cipherWithTag, 0, combined, iv.length, cipherWithTag.length);
		return Base64.getEncoder().encodeToString(combined);
	}

	public static String decryptPayload(String base64Combined, byte[] key) throws Exception {
		byte[] combined = Base64.getDecoder().decode(base64Combined.trim());
		if (combined.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
			throw new IllegalArgumentException("Payload too short for AES-GCM");
		}
		byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
		byte[] cipherWithTag = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);
		Cipher cipher = Cipher.getInstance(AES_GCM);
		GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
		byte[] decrypted = cipher.doFinal(cipherWithTag);
		return new String(decrypted, StandardCharsets.UTF_8);
	}

	private void onNostrEvent(NostrEvent event) {
		if (event == null || !active.get()) return;

		// Ignore events sent by ourselves (echoed back by relays)
		if (event.pubkey != null && event.pubkey.equalsIgnoreCase(myPubKeyHex)) {
			return;
		}

		String t = event.getTagValue("t");
		if (t == null || !topicHex.equalsIgnoreCase(t)) {
			return; // Not our topic
		}

		if (event.content == null || event.content.trim().isEmpty()) {
			return;
		}

		String decryptedJson;
		try {
			decryptedJson = decryptPayload(event.content, psk);
		} catch (Exception e) {
			logger.log(Level.FINE, "Failed to decrypt event content with PSK", e);
			return;
		}

		String type = null;
		JsonNode rootNode = null;
		try {
			rootNode = MAPPER.readTree(decryptedJson);
			type = rootNode.path("type").asText(null);
		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to parse decrypted event JSON", e);
			return;
		}

		if (type == null) return;

		switch (type) {
		case "paired" -> handlePaired(rootNode);
		case "response" -> handleResponse(rootNode);
		case "disconnect" -> handleRemoteDisconnect();
		default -> logger.info("Unhandled decrypted Nostr event type: " + type);
		}
	}

	private void handlePaired(JsonNode node) {
		logger.info("Received paired confirmation from peer");
		if (!paired.get()) {
			paired.set(true);
			synchronized (PairingInfo.class) {
				instance = this;
			}
			Platform.runLater(() -> pairedProperty.set(true));
			logger.info("Nostr Pairing established successfully!");

			// Close QR dialog if open
			QRFrame.close();
		}
	}

	private void handleRemoteDisconnect() {
		logger.info("Peer sent disconnect signal");
		disconnect();
	}

	private void handleResponse(JsonNode node) {
		String reqId = node.hasNonNull("reply_to") ? node.path("reply_to").asText() : null;
		if (reqId == null && node.hasNonNull("req_id")) {
			reqId = node.path("req_id").asText();
		}

		logger.info("Received response from peer" + (reqId != null ? " (reqId: " + reqId + ")" : ""));

		byte[] payload = null;
		try {
			if (node.hasNonNull("payload")) {
				String b64 = node.path("payload").asText("");
				if (!b64.isEmpty()) {
					payload = Base64.getDecoder().decode(b64.trim());
				}
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to decode response base64 payload", e);
		}

		if (reqId != null) {
			CompletableFuture<byte[]> future = pendingRequests.remove(reqId);
			if (future != null) {
				future.complete(payload);
			}
		} else {
			// If only one request is pending, complete it
			for (Map.Entry<String, CompletableFuture<byte[]>> entry : pendingRequests.entrySet()) {
				CompletableFuture<byte[]> future = pendingRequests.remove(entry.getKey());
				if (future != null) {
					future.complete(payload);
				}
				break;
			}
		}
	}

	public void sendMessage(byte[] message, Consumer<byte[]> onReply) {
		if (isExpired()) {
			logger.info("Pairing session expired after 24h. Disconnecting.");
			disconnect();
			if (onReply != null) onReply.accept(null);
			return;
		}

		if (!paired.get() || !active.get()) {
			if (onReply != null) onReply.accept(null);
			return;
		}

		String reqId = UUID.randomUUID().toString();
		CompletableFuture<byte[]> future = new CompletableFuture<>();
		pendingRequests.put(reqId, future);

		try {
			String base64Payload = Base64.getEncoder().encodeToString(message);
			Map<String, Object> reqObj = Map.of(
					"type", "request",
					"req_id", reqId,
					"payload", base64Payload
			);
			String jsonStr = MAPPER.writeValueAsString(reqObj);
			String encryptedContent = encryptPayload(jsonStr, psk);
			List<List<String>> tags = List.of(
					List.of("t", topicHex)
			);
			NostrEvent requestEvent = NostrEvent.create(nostrPrivateKey, 25000, tags, encryptedContent);
			connectionPool.broadcast(requestEvent);
			logger.info("Sent encrypted request to Nostr relays");

			// Set timeout for request response
			future.orTimeout(ttlSeconds, TimeUnit.SECONDS).whenComplete((reply, err) -> {
				pendingRequests.remove(reqId);
				if (onReply != null) {
					onReply.accept(reply);
				}
			});

		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to send encrypted message over Nostr", e);
			pendingRequests.remove(reqId);
			if (onReply != null) {
				onReply.accept(null);
			}
		}
	}

	public static int getRequestTimeoutS() {
		return Integer.parseInt(Main.properties.getProperty(PROP_REQUEST_TIMEOUT_S, "" + DEFAULT_TTL_SECONDS));
	}

	public static List<String> getPreferredRelays() {
		String prop = Main.properties.getProperty(PROP_NOSTR_RELAYS);
		if (prop == null || prop.trim().isEmpty()) {
			return DEFAULT_RELAYS;
		}
		String[] split = prop.split("[,;\\s]+");
		List<String> list = new ArrayList<>();
		for (String s : split) {
			if (!s.trim().isEmpty()) {
				list.add(s.trim());
			}
		}
		return list.isEmpty() ? DEFAULT_RELAYS : list;
	}

	public static void setPreferredRelays(List<String> relays) {
		if (relays == null || relays.isEmpty()) {
			Main.properties.remove(PROP_NOSTR_RELAYS);
		} else {
			Main.properties.setProperty(PROP_NOSTR_RELAYS, String.join(",", relays));
		}
	}

	public String getTopicHex() {
		return topicHex;
	}

	public byte[] getPsk() {
		return psk;
	}

	public boolean isPaired() {
		return paired.get();
	}
}
