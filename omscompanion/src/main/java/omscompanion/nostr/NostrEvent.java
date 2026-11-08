package omscompanion.nostr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NostrEvent {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@JsonProperty("id")
	public String id;

	@JsonProperty("pubkey")
	public String pubkey;

	@JsonProperty("created_at")
	public long createdAt;

	@JsonProperty("kind")
	public int kind;

	@JsonProperty("tags")
	public List<List<String>> tags = new ArrayList<>();

	@JsonProperty("content")
	public String content = "";

	@JsonProperty("sig")
	public String sig;

	public NostrEvent() {
	}

	public NostrEvent(String id, String pubkey, long createdAt, int kind, List<List<String>> tags, String content,
			String sig) {
		this.id = id;
		this.pubkey = pubkey;
		this.createdAt = createdAt;
		this.kind = kind;
		this.tags = tags != null ? tags : new ArrayList<>();
		this.content = content != null ? content : "";
		this.sig = sig;
	}

	public static NostrEvent create(byte[] privateKey, int kind, List<List<String>> tags, String content) {
        return create(privateKey, kind, tags, content, Instant.now().getEpochSecond());
    }
    public static NostrEvent create(byte[] privateKey, int kind, List<List<String>> tags, String content, long now) {
		byte[] pubKeyBytes = Bip340.getPublicKey(privateKey);
		String pubKeyHex = Bip340.bytesToHex(pubKeyBytes);

		List<List<String>> safeTags = tags != null ? tags : new ArrayList<>();
		String safeContent = content != null ? content : "";

		// Canonical serialization: [0, pubkey, created_at, kind, tags, content]
		String serialized = serializeForId(pubKeyHex, now, kind, safeTags, safeContent);
		byte[] idHash = sha256(serialized.getBytes(StandardCharsets.UTF_8));
		String idHex = Bip340.bytesToHex(idHash);

		byte[] sigBytes = Bip340.sign(privateKey, idHash);
		String sigHex = Bip340.bytesToHex(sigBytes);

		return new NostrEvent(idHex, pubKeyHex, now, kind, safeTags, safeContent, sigHex);
	}

	public static String serializeForId(String pubKeyHex, long createdAt, int kind, List<List<String>> tags,
			String content) {
		try {
			List<Object> raw = new ArrayList<>();
			raw.add(0);
			raw.add(pubKeyHex);
			raw.add(createdAt);
			raw.add(kind);
			raw.add(tags);
			raw.add(content);
			return MAPPER.writeValueAsString(raw);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean verify() {
		try {
			String serialized = serializeForId(pubkey, createdAt, kind, tags, content);
			byte[] idHash = sha256(serialized.getBytes(StandardCharsets.UTF_8));
			String calculatedId = Bip340.bytesToHex(idHash);
			if (!calculatedId.equalsIgnoreCase(id)) {
				return false;
			}
			byte[] pubKeyBytes = Bip340.hexToBytes(pubkey);
			byte[] sigBytes = Bip340.hexToBytes(sig);
			return Bip340.verify(pubKeyBytes, idHash, sigBytes);
		} catch (Exception e) {
			return false;
		}
	}

	public String getTagValue(String tagName) {
		if (tags == null)
			return null;
		for (List<String> tag : tags) {
			if (tag != null && tag.size() >= 2 && tagName.equals(tag.get(0))) {
				return tag.get(1);
			}
		}
		return null;
	}

	public String toJson() {
		try {
			return MAPPER.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static NostrEvent fromJson(String json) throws JsonProcessingException {
		return MAPPER.readValue(json, NostrEvent.class);
	}

	private static byte[] sha256(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return md.digest(data);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
