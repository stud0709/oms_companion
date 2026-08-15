package omscompanion.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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

import javafx.application.Platform;
import javafx.stage.Stage;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.OmsDataOutputStream;
import omscompanion.nostr.Bip340;
import omscompanion.nostr.NostrConnectionPool;
import omscompanion.nostr.NostrEvent;
import omscompanion.openjfx.QRFrame;

public class PairingInfo {
	private static final Logger logger = Logger.getLogger(PairingInfo.class.getName());

	public static final String PROP_REQUEST_TIMEOUT_S = "request_timeout_s";
	public static final String PROP_NOSTR_RELAYS = "nostr_relays";
	public static final String PROP_PAIRING_DEF_INIT_KEY = "pairing_def_init_key"; // kept for config compatibility

	public static final List<String> DEFAULT_RELAYS = List.of(
			"wss://relay.damus.io",
			"wss://nos.lol",
			"wss://relay.snort.social",
			"wss://nostr.mom"
	);

	private static final int DEFAULT_TTL_SECONDS = 60;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static PairingInfo instance = null;

	private final byte[] nostrPrivateKey;
	private final String topicHex;
	private final int ttlSeconds;
	private final List<String> relayUrls;
	private final NostrConnectionPool connectionPool;

	private final AtomicBoolean paired = new AtomicBoolean(false);
	private final AtomicBoolean active = new AtomicBoolean(true);
	private volatile long lastActivityTimestamp = System.currentTimeMillis();

	private Thread heartbeatThread;
	private Stage qrStage;

	private final Map<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();

	public PairingInfo() {
		this(getRequestTimeoutS(), getPreferredRelays());
	}

	public PairingInfo(int ttlSeconds, List<String> relays) {
		this.ttlSeconds = ttlSeconds;
		this.relayUrls = new ArrayList<>(relays);
		this.nostrPrivateKey = Bip340.generatePrivateKey();

		byte[] topicBytes = new byte[32];
		RANDOM.nextBytes(topicBytes);
		this.topicHex = Bip340.bytesToHex(topicBytes);

		this.connectionPool = new NostrConnectionPool();
		this.connectionPool.addEventListener(this::onNostrEvent);
	}

	public static PairingInfo getInstance() {
		synchronized (PairingInfo.class) {
			return instance;
		}
	}

	public static void disconnect() {
		PairingInfo current;
		synchronized (PairingInfo.class) {
			current = instance;
			instance = null;
		}
		if (current != null) {
			current.terminate();
		}
	}

	public void terminate() {
		active.set(false);
		paired.set(false);

		// Send disconnect event to notify peer
		try {
			List<List<String>> tags = List.of(
					List.of("t", topicHex),
					List.of("type", "disconnect")
			);
			NostrEvent event = NostrEvent.create(nostrPrivateKey, 25000, tags, "{\"type\":\"disconnect\"}");
			connectionPool.broadcast(event);
		} catch (Exception e) {
			logger.log(Level.FINE, "Error sending disconnect event", e);
		}

		if (heartbeatThread != null) {
			heartbeatThread.interrupt();
			heartbeatThread = null;
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

	public static byte[] createPairingPrompt(String topicHex, int ttl, List<String> relays) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			// (1) Application ID 10
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_WIFI_PAIRING);

			// (2) Topic hex (64 hex characters)
			dataOutputStream.writeString(topicHex);

			// (3) TTL in seconds
			dataOutputStream.writeUnsignedShort(ttl);

			// (4) Number of relays
			dataOutputStream.writeUnsignedShort(relays.size());

			// (5) Relay URLs
			for (String relay : relays) {
				dataOutputStream.writeString(relay);
			}

			return baos.toByteArray();
		}
	}

	public void displayPrompt(Runnable andThen) throws IOException {
		byte[] promptBytes = createPairingPrompt(topicHex, ttlSeconds, relayUrls);

		// Connect to Nostr relays and subscribe
		connectionPool.connect(relayUrls);
		connectionPool.subscribe(topicHex);

		lastActivityTimestamp = System.currentTimeMillis();

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

	private void onNostrEvent(NostrEvent event) {
		if (event == null || !active.get()) return;

		String t = event.getTagValue("t");
		if (t == null || !topicHex.equalsIgnoreCase(t)) {
			return; // Not our topic
		}

		String type = event.getTagValue("type");
		if (type == null) {
			// Check if type is inside JSON content
			if (event.content != null && event.content.contains("\"type\"")) {
				if (event.content.contains("\"ping\"")) type = "ping";
				else if (event.content.contains("\"pong\"")) type = "pong";
				else if (event.content.contains("\"disconnect\"")) type = "disconnect";
			}
		}

		if (type == null) return;

		lastActivityTimestamp = System.currentTimeMillis();

		switch (type) {
		case "ping" -> handlePing(event);
		case "pong" -> handlePong(event);
		case "disconnect" -> handleRemoteDisconnect();
		case "response" -> handleResponse(event);
		default -> logger.info("Unhandled Nostr event type: " + type);
		}
	}

	private void handlePing(NostrEvent event) {
		logger.info("Received ping from peer");

		// Send pong response immediately
		List<List<String>> tags = List.of(
				List.of("t", topicHex),
				List.of("type", "pong")
		);
		NostrEvent pongEvent = NostrEvent.create(nostrPrivateKey, 25000, tags,
				"{\"type\":\"pong\",\"ts\":" + System.currentTimeMillis() + "}");
		connectionPool.broadcast(pongEvent);

		// Handshake complete
		if (!paired.get()) {
			paired.set(true);
			synchronized (PairingInfo.class) {
				instance = this;
			}
			logger.info("Nostr Pairing established successfully!");

			// Close QR dialog if open
			Platform.runLater(() -> {
				QRFrame.showForMessage(new byte[0], false, false, null, null);
				// The showForMessage above closes any previous active stage in QRFrame
			});

			startHeartbeat();
		}
	}

	private void handlePong(NostrEvent event) {
		logger.fine("Received pong from peer");
		lastActivityTimestamp = System.currentTimeMillis();
	}

	private void handleRemoteDisconnect() {
		logger.info("Peer sent disconnect signal");
		disconnect();
	}

	private void handleResponse(NostrEvent event) {
		String reqId = event.getTagValue("reply_to");
		if (reqId == null) {
			reqId = event.getTagValue("req_id");
		}

		byte[] payload = null;
		try {
			if (event.content != null && !event.content.isEmpty()) {
				payload = Base64.getDecoder().decode(event.content.trim());
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to decode response base64 payload", e);
		}

		if (reqId != null && pendingRequests.containsKey(reqId)) {
			CompletableFuture<byte[]> future = pendingRequests.remove(reqId);
			if (future != null) {
				future.complete(payload);
			}
		} else {
			// If only one request is pending, complete it
			for (Map.Entry<String, CompletableFuture<byte[]>> entry : pendingRequests.entrySet()) {
				entry.getValue().complete(payload);
				pendingRequests.remove(entry.getKey());
				break;
			}
		}
	}

	private void startHeartbeat() {
		if (heartbeatThread != null) {
			heartbeatThread.interrupt();
		}

		heartbeatThread = new Thread(() -> {
			long timeoutMillis = (long) ttlSeconds * 1000L;
			while (active.get() && paired.get() && !Thread.interrupted()) {
				try {
					Thread.sleep(10000); // 10s ping interval

					if (System.currentTimeMillis() - lastActivityTimestamp > timeoutMillis) {
						logger.warning("Pairing session timed out after " + ttlSeconds + "s inactivity");
						disconnect();
						break;
					}

					// Send heartbeat ping
					List<List<String>> tags = List.of(
							List.of("t", topicHex),
							List.of("type", "ping")
					);
					NostrEvent pingEvent = NostrEvent.create(nostrPrivateKey, 25000, tags,
							"{\"type\":\"ping\",\"ts\":" + System.currentTimeMillis() + "}");
					connectionPool.broadcast(pingEvent);

				} catch (InterruptedException e) {
					break;
				}
			}
		}, "Nostr-Heartbeat");
		heartbeatThread.setDaemon(true);
		heartbeatThread.start();
	}

	public void sendMessage(byte[] message, Consumer<byte[]> onReply) {
		if (!paired.get() || !active.get()) {
			if (onReply != null) onReply.accept(null);
			return;
		}

		String reqId = UUID.randomUUID().toString();
		CompletableFuture<byte[]> future = new CompletableFuture<>();
		pendingRequests.put(reqId, future);

		try {
			String base64Payload = Base64.getEncoder().encodeToString(message);
			List<List<String>> tags = List.of(
					List.of("t", topicHex),
					List.of("type", "request"),
					List.of("req_id", reqId)
			);
			NostrEvent requestEvent = NostrEvent.create(nostrPrivateKey, 25000, tags, base64Payload);
			connectionPool.broadcast(requestEvent);
			logger.info("Sent request to Nostr relays");

			// Set timeout for request response
			future.orTimeout(ttlSeconds, TimeUnit.SECONDS).whenComplete((reply, err) -> {
				pendingRequests.remove(reqId);
				if (onReply != null) {
					onReply.accept(reply);
				}
			});

		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to send message over Nostr", e);
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

	public static String getDefaultInitialKeyAlias() {
		return Main.properties.getProperty(PROP_PAIRING_DEF_INIT_KEY, Main.getDefaultKeyAlias());
	}

	public String getTopicHex() {
		return topicHex;
	}

	public boolean isPaired() {
		return paired.get();
	}
}
