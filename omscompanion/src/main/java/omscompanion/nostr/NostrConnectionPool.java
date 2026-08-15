package omscompanion.nostr;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class NostrConnectionPool {
	private static final Logger logger = Logger.getLogger(NostrConnectionPool.class.getName());

	private final HttpClient httpClient;
	private final List<NostrRelayClient> relayClients = new CopyOnWriteArrayList<>();
	private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
	private final List<Consumer<NostrEvent>> eventListeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean(false);
	private String activeTopic;
	private String subscriptionId = "oms_sub";

	public NostrConnectionPool() {
		this.httpClient = HttpClient.newBuilder().build();
	}

	public synchronized void connect(List<String> relayUrls) {
		close();
		closed.set(false);
		processedEventIds.clear();

		for (String url : relayUrls) {
			if (url == null || url.trim().isEmpty()) {
				continue;
			}
			String trimmedUrl = url.trim();
			NostrRelayClient client = new NostrRelayClient(
					trimmedUrl,
					httpClient,
					this::handleIncomingEvent,
					() -> onRelayConnected(trimmedUrl),
					() -> onRelayDisconnected(trimmedUrl)
			);
			relayClients.add(client);
			client.connect();
		}
	}

	public void addEventListener(Consumer<NostrEvent> listener) {
		eventListeners.add(listener);
	}

	public void removeEventListener(Consumer<NostrEvent> listener) {
		eventListeners.remove(listener);
	}

	public synchronized void subscribe(String topic) {
		this.activeTopic = topic;
		for (NostrRelayClient client : relayClients) {
			client.subscribe(subscriptionId, topic);
		}
	}

	public synchronized void unsubscribe() {
		for (NostrRelayClient client : relayClients) {
			client.unsubscribe(subscriptionId);
		}
		this.activeTopic = null;
	}

	public void broadcast(NostrEvent event) {
		if (closed.get()) return;
		for (NostrRelayClient client : relayClients) {
			if (client.isConnected()) {
				client.publish(event);
			}
		}
	}

	public int getConnectedCount() {
		int count = 0;
		for (NostrRelayClient client : relayClients) {
			if (client.isConnected()) {
				count++;
			}
		}
		return count;
	}

	public List<String> getRelayUrls() {
		List<String> urls = new ArrayList<>();
		for (NostrRelayClient client : relayClients) {
			urls.add(client.getRelayUrl());
		}
		return urls;
	}

	private void handleIncomingEvent(NostrEvent event, String relayUrl) {
		if (event == null || event.id == null) return;
		// De-duplicate events
		if (!processedEventIds.add(event.id)) {
			return; // already processed
		}

		logger.fine("Received event " + event.id + " from " + relayUrl + ", dispatching to listeners");
		for (Consumer<NostrEvent> listener : eventListeners) {
			try {
				listener.accept(event);
			} catch (Exception e) {
				logger.warning("Error in event listener: " + e.getMessage());
			}
		}
	}

	private void onRelayConnected(String url) {
		logger.info("Relay connected: " + url + " (total connected: " + getConnectedCount() + ")");
	}

	private void onRelayDisconnected(String url) {
		logger.info("Relay disconnected: " + url + " (total connected: " + getConnectedCount() + ")");
	}

	public synchronized void close() {
		closed.set(true);
		for (NostrRelayClient client : relayClients) {
			try {
				client.close();
			} catch (Exception e) {
				// ignore
			}
		}
		relayClients.clear();
	}
}
