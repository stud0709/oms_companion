package omscompanion.openjfx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import omscompanion.crypto.RSAUtils;

public class RSAPublicKeyItem {

	public RSAPublicKeyItem(Path path) {
		this.path = path;
		RSAPublicKey pk = null;
		try {
			pk = RSAUtils.getFromX509(Files.readAllBytes(path));
		} catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
			e.printStackTrace();
			pk = null;
		}

		this.publicKey = pk;
	}

	public final Path path;
	public final RSAPublicKey publicKey;

	@Override
	public String toString() {
		return path.getFileName().toString();
	}
}
