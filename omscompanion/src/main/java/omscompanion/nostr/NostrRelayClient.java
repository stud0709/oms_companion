package omscompanion.nostr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NostrRelayClient implements WebSocket.Listener {
	private static final Logger logger = Logger.getLogger(NostrRelayClient.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String relayUrl;
	private final HttpClient httpClient;
	private final BiConsumer<NostrEvent, String> eventConsumer;
	private final Runnable onConnectCallback;
	private final Runnable onDisconnectCallback;

	private WebSocket webSocket;
	private final StringBuilder messageBuffer = new StringBuilder();
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private final AtomicBoolean closing = new AtomicBoolean(false);
	private final Map<String, String> activeSubscriptions = new ConcurrentHashMap<>();

	public NostrRelayClient(String relayUrl, HttpClient httpClient, BiConsumer<NostrEvent, String> eventConsumer,
			Runnable onConnectCallback, Runnable onDisconnectCallback) {
		this.relayUrl = relayUrl;
		this.httpClient = httpClient;
		this.eventConsumer = eventConsumer;
		this.onConnectCallback = onConnectCallback;
		this.onDisconnectCallback = onDisconnectCallback;
	}

	public CompletableFuture<WebSocket> connect() {
		closing.set(false);
		try {
			URI uri = URI.create(relayUrl);
			return httpClient.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.buildAsync(uri, this)
					.whenComplete((ws, err) -> {
						if (err != null) {
							logger.log(Level.WARNING, "Failed to connect to " + relayUrl + ": " + err.getMessage());
							connected.set(false);
							scheduleReconnect();
						} else {
							this.webSocket = ws;
						}
					});
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error initiating connection to " + relayUrl, e);
			scheduleReconnect();
			CompletableFuture<WebSocket> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
	}

	private void scheduleReconnect() {
		if (closing.get()) return;
		CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
			if (!closing.get() && !isConnected()) {
				logger.fine("Attempting automatic reconnect to " + relayUrl + "...");
				connect();
			}
		});
	}

	public boolean isConnected() {
		return connected.get() && webSocket != null;
	}

	public String getRelayUrl() {
		return relayUrl;
	}

	public void subscribe(String subId, String topic) {
		activeSubscriptions.put(subId, topic);
		if (isConnected()) {
			sendSubscription(subId, topic);
		}
	}

	private void sendSubscription(String subId, String topic) {
		try {
			// ["REQ", "<sub_id>", {"kinds": [25000], "#t": ["<topic>"]}]
			List<Object> req = List.of(
					"REQ",
					subId,
					Map.of(
							"kinds", List.of(25000),
							"#t", List.of(topic)
					)
			);
			String reqJson = MAPPER.writeValueAsString(req);
			sendText(reqJson);
			logger.fine("Subscribed on " + relayUrl);
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error sending subscription to " + relayUrl, e);
		}
	}

	public void unsubscribe(String subId) {
		activeSubscriptions.remove(subId);
		if (isConnected()) {
			try {
				List<Object> close = List.of("CLOSE", subId);
				sendText(MAPPER.writeValueAsString(close));
			} catch (Exception e) {
				logger.log(Level.WARNING, "Error sending CLOSE to " + relayUrl, e);
			}
		}
	}

	public CompletableFuture<WebSocket> publish(NostrEvent event) {
		if (isConnected()) {
			try {
				List<Object> msg = List.of("EVENT", event);
				String json = MAPPER.writeValueAsString(msg);
				return sendText(json);
			} catch (Exception e) {
				logger.log(Level.WARNING, "Error publishing event to " + relayUrl, e);
			}
		}
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<WebSocket> sendText(String text) {
		if (webSocket != null && !webSocket.isOutputClosed()) {
			return webSocket.sendText(text, true);
		}
		return CompletableFuture.completedFuture(null);
	}

	public void close() {
		closing.set(true);
		connected.set(false);
		if (webSocket != null) {
			try {
				webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye");
			} catch (Exception e) {
				// ignore
			}
		}
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		logger.info("Connected to " + relayUrl);
		this.webSocket = webSocket;
		this.connected.set(true);
		if (onConnectCallback != null) {
			onConnectCallback.run();
		}
		// Re-send any pending subscriptions
		for (Map.Entry<String, String> entry : activeSubscriptions.entrySet()) {
			sendSubscription(entry.getKey(), entry.getValue());
		}
		webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		messageBuffer.append(data);
		if (last) {
			String fullMessage = messageBuffer.toString();
			messageBuffer.setLength(0);
			processIncomingMessage(fullMessage);
		}
		webSocket.request(1);
		return null;
	}

	private void processIncomingMessage(String message) {
		try {
			List<Object> list = MAPPER.readValue(message, new TypeReference<List<Object>>() {});
			if (list == null || list.isEmpty()) {
				return;
			}
			String type = String.valueOf(list.get(0));
			if ("EVENT".equals(type) && list.size() >= 3) {
				// ["EVENT", "<sub_id>", {event_object}]
				String eventJson = MAPPER.writeValueAsString(list.get(2));
				NostrEvent event = MAPPER.readValue(eventJson, NostrEvent.class);
				if (event != null && eventConsumer != null) {
					eventConsumer.accept(event, relayUrl);
				}
			} else if ("OK".equals(type)) {
				logger.fine("Relay " + relayUrl + " OK response: " + message);
			} else if ("NOTICE".equals(type) && list.size() >= 2) {
				logger.info("Relay " + relayUrl + " NOTICE: " + list.get(1));
			} else if ("CLOSED".equals(type) && list.size() >= 3) {
				logger.warning("Relay " + relayUrl + " CLOSED subscription " + list.get(1) + ": " + list.get(2));
			}
		} catch (Exception e) {
			logger.log(Level.FINE, "Error parsing message from " + relayUrl + ": " + message, e);
		}
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		logger.info("Relay closed " + relayUrl + " (status " + statusCode + "): " + reason);
		connected.set(false);
		if (!closing.get()) {
			if (onDisconnectCallback != null) {
				onDisconnectCallback.run();
			}
			scheduleReconnect();
		}
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		logger.log(Level.WARNING, "WebSocket error on " + relayUrl + ": " + error.getMessage());
		connected.set(false);
		if (!closing.get()) {
			if (onDisconnectCallback != null) {
				onDisconnectCallback.run();
			}
			scheduleReconnect();
		}
	}
}
