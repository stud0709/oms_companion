package omscompanion.nostr;

import java.util.List;

public class NostrTest {
	public static void main(String[] args) {
		System.out.println("Testing BIP-340 Key Generation...");
		byte[] priv = Bip340.generatePrivateKey();
		byte[] pub = Bip340.getPublicKey(priv);
		System.out.println("Private Key: " + Bip340.bytesToHex(priv));
		System.out.println("Public Key:  " + Bip340.bytesToHex(pub));

		System.out.println("Testing Nostr Event Creation & Signing...");
		List<List<String>> tags = List.of(
				List.of("t", "test-topic-12345"),
				List.of("type", "ping")
		);
		NostrEvent event = NostrEvent.create(priv, 25000, tags, "{\"type\":\"ping\"}");
		System.out.println("Event JSON: " + event.toJson());

		boolean verified = event.verify();
		System.out.println("Event Verified: " + verified);
		if (!verified) {
			throw new RuntimeException("Signature verification failed!");
		}

		System.out.println("All Nostr tests passed successfully!");
	}
}
