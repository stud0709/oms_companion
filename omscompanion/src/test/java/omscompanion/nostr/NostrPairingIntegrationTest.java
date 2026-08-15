package omscompanion.nostr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import omscompanion.MessageComposer;
import omscompanion.OmsDataInputStream;
import omscompanion.crypto.PairingInfo;

public class NostrPairingIntegrationTest {
	public static void main(String[] args) throws Exception {
		testBinaryQrSerialization();
		testNostrEventSignatures();
		testMessageFlowSimulation();
		System.out.println("\n>>> ALL NOSTR INTEGRATION TESTS PASSED! <<<");
	}

	private static void testBinaryQrSerialization() throws IOException {
		System.out.println("--- Testing Binary QR Serialization (App ID 10) ---");
		String topicHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		int ttl = 60;
		List<String> relays = List.of(
				"wss://relay.damus.io",
				"wss://nos.lol",
				"wss://relay.snort.social"
		);

		byte[] promptBytes = PairingInfo.createPairingPrompt(topicHex, ttl, relays);
		System.out.println("Serialized Prompt Length: " + promptBytes.length + " bytes");

		// Test OMS Text Encoding
		String omsText = MessageComposer.encodeAsOmsText(promptBytes);
		System.out.println("OMS Text Length: " + omsText.length() + " chars");
		System.out.println("OMS Text Sample: " + omsText.substring(0, Math.min(60, omsText.length())) + "...");

		if (!omsText.startsWith("oms00_")) {
			throw new AssertionError("OMS text does not start with oms00_");
		}

		byte[] decodedBytes = MessageComposer.decode(omsText);
		if (decodedBytes == null || decodedBytes.length != promptBytes.length) {
			throw new AssertionError("Decoded bytes length mismatch");
		}

		// Deserialize with OmsDataInputStream (simulating OneMoreSecret)
		try (ByteArrayInputStream bais = new ByteArrayInputStream(decodedBytes);
				OmsDataInputStream in = new OmsDataInputStream(bais)) {

			int appId = in.readUnsignedShort();
			System.out.println("Deserialized App ID: " + appId);
			if (appId != MessageComposer.APPLICATION_WIFI_PAIRING) {
				throw new AssertionError("Expected App ID " + MessageComposer.APPLICATION_WIFI_PAIRING + ", got " + appId);
			}

			String desTopic = in.readString();
			System.out.println("Deserialized Topic: " + desTopic);
			if (!topicHex.equals(desTopic)) {
				throw new AssertionError("Topic mismatch");
			}

			int desTtl = in.readUnsignedShort();
			System.out.println("Deserialized TTL: " + desTtl);
			if (desTtl != ttl) {
				throw new AssertionError("TTL mismatch");
			}

			int relayCount = in.readUnsignedShort();
			System.out.println("Deserialized Relay Count: " + relayCount);
			if (relayCount != relays.size()) {
				throw new AssertionError("Relay count mismatch");
			}

			for (int i = 0; i < relayCount; i++) {
				String relay = in.readString();
				System.out.println("  Relay [" + i + "]: " + relay);
				if (!relays.get(i).equals(relay)) {
					throw new AssertionError("Relay mismatch at index " + i);
				}
			}
		}
		System.out.println("Binary QR Serialization Test Passed!");
	}

	private static void testNostrEventSignatures() {
		System.out.println("\n--- Testing Nostr Event Signatures & Kinds ---");
		byte[] priv = Bip340.generatePrivateKey();
		byte[] pub = Bip340.getPublicKey(priv);
		String topic = "test-topic-abc";

		// Ping event
		NostrEvent ping = NostrEvent.create(priv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "ping")
		), "{\"type\":\"ping\",\"ts\":" + System.currentTimeMillis() + "}");

		if (!ping.verify()) {
			throw new AssertionError("Ping event failed signature verification");
		}
		if (ping.kind != 25000) {
			throw new AssertionError("Ping event kind must be 25000");
		}
		if (!topic.equals(ping.getTagValue("t"))) {
			throw new AssertionError("Topic tag mismatch");
		}
		if (!"ping".equals(ping.getTagValue("type"))) {
			throw new AssertionError("Type tag mismatch");
		}

		// Request event with encrypted payload
		byte[] dummyPayload = "Hello OneMoreSecret Encrypted Payload".getBytes();
		String base64Payload = Base64.getEncoder().encodeToString(dummyPayload);

		NostrEvent request = NostrEvent.create(priv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "request"),
				List.of("req_id", "req-001")
		), base64Payload);

		if (!request.verify()) {
			throw new AssertionError("Request event failed signature verification");
		}
		if (!"request".equals(request.getTagValue("type"))) {
			throw new AssertionError("Request type tag mismatch");
		}
		if (!"req-001".equals(request.getTagValue("req_id"))) {
			throw new AssertionError("Request ID tag mismatch");
		}

		System.out.println("Nostr Event Signatures Test Passed!");
	}

	private static void testMessageFlowSimulation() {
		System.out.println("\n--- Testing Message Flow Simulation ---");
		byte[] omsPriv = Bip340.generatePrivateKey();
		byte[] appPriv = Bip340.generatePrivateKey();
		String topic = "topic-flow-test";

		// 1. Android side sends ping
		NostrEvent ping = NostrEvent.create(appPriv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "ping")
		), "{\"type\":\"ping\"}");

		// 2. omsCompanion verifies ping and creates pong
		if (!ping.verify()) throw new AssertionError("Invalid ping");
		NostrEvent pong = NostrEvent.create(omsPriv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "pong")
		), "{\"type\":\"pong\"}");

		if (!pong.verify()) throw new AssertionError("Invalid pong");

		// 3. omsCompanion sends encrypted request
		byte[] sampleEncryptedData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
		String b64Req = Base64.getEncoder().encodeToString(sampleEncryptedData);
		NostrEvent req = NostrEvent.create(omsPriv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "request"),
				List.of("req_id", "12345")
		), b64Req);

		if (!req.verify()) throw new AssertionError("Invalid req");

		// 4. Android responds with response
		byte[] sampleResponseData = new byte[] { 8, 7, 6, 5, 4, 3, 2, 1 };
		String b64Resp = Base64.getEncoder().encodeToString(sampleResponseData);
		NostrEvent resp = NostrEvent.create(appPriv, 25000, List.of(
				List.of("t", topic),
				List.of("type", "response"),
				List.of("reply_to", "12345")
		), b64Resp);

		if (!resp.verify()) throw new AssertionError("Invalid resp");
		byte[] decodedResp = Base64.getDecoder().decode(resp.content);
		if (!java.util.Arrays.equals(sampleResponseData, decodedResp)) {
			throw new AssertionError("Response payload mismatch");
		}

		System.out.println("Message Flow Simulation Passed!");
	}
}
