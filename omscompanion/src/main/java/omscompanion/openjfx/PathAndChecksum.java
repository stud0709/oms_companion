package omscompanion.openjfx;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.annotation.JsonIgnore;

import omscompanion.Main;

public class PathAndChecksum {
	public byte[] pathHash;
	public byte[] checksum;

	public PathAndChecksum(Path path, byte[] checksum) throws NoSuchAlgorithmException, IOException {
		this.pathHash = MessageDigest.getInstance("SHA-256").digest(path.toString().getBytes());
		this.checksum = checksum;
	}

	@JsonIgnore
	public String getReadableHash() {
		return Main.byteArrayToHex(pathHash, false);
	}

	public PathAndChecksum() {
	}
}
