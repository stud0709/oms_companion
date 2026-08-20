package omscompanion.nostr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import omscompanion.MessageComposer;
import omscompanion.OmsDataInputStream;
import omscompanion.crypto.PairingInfo;

public class NostrPairingIntegrationTest {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final SecureRandom RANDOM = new SecureRandom();

	public static void main(String[] args) throws Exception {
		testBinaryQrSerialization();
		testAesGcmPayloadEncryption();
		testNostrEventSignatures();
		testMessageFlowSimulation();
		System.out.println("\n>>> ALL NOSTR INTEGRATION TESTS PASSED! <<<");
	}

	private static void testBinaryQrSerialization() throws IOException {
		System.out.println("--- Testing Binary QR Serialization (App ID 10 with 256-bit PSK) ---");
		String topicHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
		byte[] psk = new byte[32];
		RANDOM.nextBytes(psk);
		int ttl = 120;
		List<String> relays = List.of(
				"wss://relay.damus.io",
				"wss://nos.lol",
				"wss://relay.snort.social"
		);

		byte[] promptBytes = PairingInfo.createPairingPrompt(topicHex, psk, ttl, relays);
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
			if (appId != MessageComposer.APPLICATION_NOSTR_PAIRING) {
				throw new AssertionError("Expected App ID " + MessageComposer.APPLICATION_NOSTR_PAIRING + ", got " + appId);
			}

			String desTopic = in.readString();
			System.out.println("Deserialized Topic: " + desTopic);
			if (!topicHex.equals(desTopic)) {
				throw new AssertionError("Topic mismatch");
			}

			byte[] desPsk = in.readByteArray();
			System.out.println("Deserialized PSK Length: " + desPsk.length + " bytes");
			if (!Arrays.equals(psk, desPsk)) {
				throw new AssertionError("PSK mismatch");
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

	private static void testAesGcmPayloadEncryption() throws Exception {
		System.out.println("\n--- Testing AES-256-GCM Payload Encryption ---");
		byte[] psk = new byte[32];
		RANDOM.nextBytes(psk);

		String plaintext = "{\"type\":\"request\",\"req_id\":\"test-123\",\"payload\":\"AQIDBA==\"}";
		String encrypted = PairingInfo.encryptPayload(plaintext, psk);
		System.out.println("Encrypted ciphertext length: " + encrypted.length());

		String decrypted = PairingInfo.decryptPayload(encrypted, psk);
		if (!plaintext.equals(decrypted)) {
			throw new AssertionError("Decrypted payload mismatch");
		}

		// Verify tampering detection
		byte[] raw = Base64.getDecoder().decode(encrypted);
		raw[raw.length - 1] ^= 0x01; // flip last bit of authentication tag
		String tampered = Base64.getEncoder().encodeToString(raw);

		try {
			PairingInfo.decryptPayload(tampered, psk);
			throw new AssertionError("Decryption of tampered ciphertext should have thrown AEADBadTagException");
		} catch (Exception expected) {
			System.out.println("Tampered payload correctly rejected: " + expected.getClass().getSimpleName());
		}

		System.out.println("AES-256-GCM Encryption Test Passed!");
	}

	private static void testNostrEventSignatures() {
		System.out.println("\n--- Testing Nostr Event Signatures & Kinds ---");
		byte[] priv = Bip340.generatePrivateKey();
		byte[] pub = Bip340.getPublicKey(priv);
		String topic = "test-topic-abc";

		// Encrypted paired event
		NostrEvent event = NostrEvent.create(priv, 25000, List.of(
				List.of("t", topic)
		), "encrypted_base64_payload_here");

		if (!event.verify()) {
			throw new AssertionError("Event failed signature verification");
		}
		if (event.kind != 25000) {
			throw new AssertionError("Event kind must be 25000");
		}
		if (!topic.equals(event.getTagValue("t"))) {
			throw new AssertionError("Topic tag mismatch");
		}

		System.out.println("Nostr Event Signatures Test Passed!");
	}

	private static void testMessageFlowSimulation() throws Exception {
		System.out.println("\n--- Testing Message Flow Simulation (with PSK Authenticated Encryption) ---");
		byte[] omsPriv = Bip340.generatePrivateKey();
		byte[] appPriv = Bip340.generatePrivateKey();
		byte[] psk = new byte[32];
		RANDOM.nextBytes(psk);
		String topic = "topic-flow-test";

		// 1. Android side sends encrypted {"type":"paired"}
		String pairedPlaintext = "{\"type\":\"paired\"}";
		String pairedEncrypted = PairingInfo.encryptPayload(pairedPlaintext, psk);
		NostrEvent pairedEvent = NostrEvent.create(appPriv, 25000, List.of(
				List.of("t", topic)
		), pairedEncrypted);

		if (!pairedEvent.verify()) throw new AssertionError("Invalid paired event signature");
		String decryptedPaired = PairingInfo.decryptPayload(pairedEvent.content, psk);
		JsonNode pairedNode = MAPPER.readTree(decryptedPaired);
		if (!"paired".equals(pairedNode.path("type").asText())) {
			throw new AssertionError("Expected type paired");
		}

		// 2. omsCompanion sends encrypted request
		byte[] sampleEncryptedData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
		String b64ReqPayload = Base64.getEncoder().encodeToString(sampleEncryptedData);
		String reqPlaintext = MAPPER.writeValueAsString(Map.of(
				"type", "request",
				"req_id", "req-12345",
				"payload", b64ReqPayload
		));
		String reqEncrypted = PairingInfo.encryptPayload(reqPlaintext, psk);
		NostrEvent reqEvent = NostrEvent.create(omsPriv, 25000, List.of(
				List.of("t", topic)
		), reqEncrypted);

		if (!reqEvent.verify()) throw new AssertionError("Invalid req signature");
		String decryptedReq = PairingInfo.decryptPayload(reqEvent.content, psk);
		JsonNode reqNode = MAPPER.readTree(decryptedReq);
		if (!"request".equals(reqNode.path("type").asText())) throw new AssertionError("Expected type request");
		if (!"req-12345".equals(reqNode.path("req_id").asText())) throw new AssertionError("Expected req_id req-12345");

		// 3. Android responds with encrypted response
		byte[] sampleResponseData = new byte[] { 8, 7, 6, 5, 4, 3, 2, 1 };
		String b64RespPayload = Base64.getEncoder().encodeToString(sampleResponseData);
		String respPlaintext = MAPPER.writeValueAsString(Map.of(
				"type", "response",
				"reply_to", "req-12345",
				"payload", b64RespPayload
		));
		String respEncrypted = PairingInfo.encryptPayload(respPlaintext, psk);
		NostrEvent respEvent = NostrEvent.create(appPriv, 25000, List.of(
				List.of("t", topic)
		), respEncrypted);

		if (!respEvent.verify()) throw new AssertionError("Invalid resp signature");
		String decryptedResp = PairingInfo.decryptPayload(respEvent.content, psk);
		JsonNode respNode = MAPPER.readTree(decryptedResp);
		if (!"response".equals(respNode.path("type").asText())) throw new AssertionError("Expected type response");
		if (!"req-12345".equals(respNode.path("reply_to").asText())) throw new AssertionError("Expected reply_to req-12345");
		byte[] decodedRespPayload = Base64.getDecoder().decode(respNode.path("payload").asText());
		if (!Arrays.equals(sampleResponseData, decodedRespPayload)) {
			throw new AssertionError("Response payload mismatch");
		}

		// 4. Disconnect event
		String discPlaintext = "{\"type\":\"disconnect\"}";
		String discEncrypted = PairingInfo.encryptPayload(discPlaintext, psk);
		NostrEvent discEvent = NostrEvent.create(omsPriv, 25000, List.of(
				List.of("t", topic)
		), discEncrypted);
		if (!discEvent.verify()) throw new AssertionError("Invalid disconnect signature");
		String decryptedDisc = PairingInfo.decryptPayload(discEvent.content, psk);
		JsonNode discNode = MAPPER.readTree(decryptedDisc);
		if (!"disconnect".equals(discNode.path("type").asText())) throw new AssertionError("Expected type disconnect");

		System.out.println("Message Flow Simulation Passed!");
	}
}
