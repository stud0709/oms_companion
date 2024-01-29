package omscompanion.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.OmsDataInputStream;
import omscompanion.OmsDataOutputStream;
import omscompanion.openjfx.QRFrame;

public class PairingInfo {
	private final KeyPair rsaKeyPair;
	private ConnectionSettings connectionSettings;
	private static Thread sender = null;
	private static Socket socket = null;
	private static PairingInfo instance = null;
	public static final String PROP_SO_TIMEOUT = "so_timeout", PROP_REQUEST_TIMEOUT_S = "request_timeout_s";

	public record ConnectionSettings(RSAPublicKey publicKeySend, RSAPublicKey initialKey, InetAddress inetAddress,
			int port, boolean isAutoTypeWiFi) {
		public ConnectionSettings withPublicKeySend(RSAPublicKey pKey) {
			return new ConnectionSettings(pKey, initialKey, inetAddress, port, isAutoTypeWiFi);
		}

		public ConnectionSettings withInetAddress(InetAddress adr) {
			return new ConnectionSettings(publicKeySend, initialKey, adr, port, isAutoTypeWiFi);
		}

		public ConnectionSettings withPort(int p) {
			return new ConnectionSettings(publicKeySend, initialKey, inetAddress, p, isAutoTypeWiFi);
		}

		public ConnectionSettings withAutoTypeWiFi(boolean b) {
			return new ConnectionSettings(publicKeySend, initialKey, inetAddress, port, b);
		}

		public ConnectionSettings withInitialKey(RSAPublicKey iKey) {
			return new ConnectionSettings(publicKeySend, iKey, inetAddress, port, isAutoTypeWiFi);
		}

		public ConnectionSettings() {
			this(null, null, null, 0, false);
		}
	}

	public PairingInfo() throws NoSuchAlgorithmException {
		rsaKeyPair = RSAUtils.newKeyPair(RSAUtils.getKeyLength());

		synchronized (PairingInfo.class) {
			instance = this;
			sender = null;
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	public static PairingInfo getInstance() {
		synchronized (PairingInfo.class) {
			return instance;
		}
	}

	public static void disconnect() {
		synchronized (PairingInfo.class) {
			instance = null;
			sender = null;
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	private byte[] getPrompt(String id, ConnectionSettings connectionSettings, byte[] privateKeyMaterialSend)
			throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
			// (1) application identifier
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_PAIRING_INFO);

			// (2) Pairing Request ID
			dataOutputStream.writeString(id);

			// (3) own public key
			dataOutputStream.writeByteArray(((RSAPublicKey) rsaKeyPair.getPublic()).getEncoded());

			// (4) private key for the other party to answer
			dataOutputStream.writeByteArray(privateKeyMaterialSend);

			// (5) AutoTypeWiFi
			dataOutputStream.writeBoolean(connectionSettings.isAutoTypeWiFi);

			return MessageComposer.createRsaAesEnvelope(connectionSettings.initialKey,
					RSAUtils.getRsaTransformationIdx(), AESUtil.getKeyLength(), AESUtil.getTransformationIdx(),
					baos.toByteArray());
		}
	}

	public void displayPrompt(String id, ConnectionSettings connectionSettings, Runnable andThen)
			throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		var keyPairSend = RSAUtils.newKeyPair(RSAUtils.getKeyLength());

// Why we generate the private key for the OneMoreSecret on this side:
// - This key protects the connection
// - We'll encrypt the key data and forget in the next line keeping only the public key
// - With this in place, you only need IP and port of OneMoreSecret to establish the 
//   connection - something you can enter manually, no Bluetooth connection needed.			       
//   Otherwise, OneMoreSecret will have to type the private key - it takes time, 
//   and you will need a bluetooth connection for that (or it gets ugly)		

		QRFrame.showForMessage(getPrompt(id, connectionSettings, keyPairSend.getPrivate().getEncoded()), false, true,
				null, reply -> onHandshake(reply,
						connectionSettings.withPublicKeySend((RSAPublicKey) keyPairSend.getPublic()), andThen));
	}

	private void onHandshake(String reply, ConnectionSettings connectionSettings, Runnable andThen) {
		if (reply == null || reply.isEmpty()) { // not successful
			synchronized (PairingInfo.class) {
				instance = null;
			}
			return;
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(reply));
				OmsDataInputStream dataInputStream = new OmsDataInputStream(bais)) {
			// (1) IP address
			var address = ByteBuffer.allocate(4).putInt(dataInputStream.readInt()).array();
			var iAddress = InetAddress.getByAddress(address);

			// (2) port
			var port = dataInputStream.readShort();

			// pairing successful
			this.connectionSettings = connectionSettings.withInetAddress(iAddress).withPort(port);

			synchronized (PairingInfo.class) {
				instance = this;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			synchronized (PairingInfo.class) {
				instance = null;
			}
		} finally {
			if (andThen != null)
				andThen.run();
		}
	}

	public void sendMessage(byte[] message, Consumer<byte[]> onReply)
			throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
			BadPaddingException, IOException {

		// encrypt message with public key of the current connection
		byte[] dataToSend;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
			// (1) RSA transformation index
			dataOutputStream.writeUnsignedShort(RSAUtils.getRsaTransformationIdx());

			// (2) write encrypted message
			var encryptedMessage = RSAUtils.process(Cipher.ENCRYPT_MODE, connectionSettings.publicKeySend,
					RSAUtils.getRsaTransformation().transformation, message);
			dataOutputStream.writeByteArray(encryptedMessage);

			dataToSend = baos.toByteArray();
		}

		var ts = System.currentTimeMillis();
		var timeout = getRequestTimeoutS() * 1000;

		synchronized (this) {
			sender = new Thread(() -> {
				while (!Thread.interrupted() && System.currentTimeMillis() - ts < timeout) {
					synchronized (PairingInfo.class) {
						if (instance != PairingInfo.this || sender != Thread.currentThread())
							return;
					}

					// try to connect
					try (var s = new Socket()) {
						s.connect(new InetSocketAddress(connectionSettings.inetAddress, connectionSettings.port),
								Integer.parseInt(Main.properties.getProperty(PROP_SO_TIMEOUT, "" + 2_000)));

						socket = s;

						try (OutputStream os = s.getOutputStream();
								InputStream is = s.getInputStream();
								ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
							os.write(dataToSend);
							os.flush();

							// now wait for the reply
							s.setSoTimeout(0);

							byte[] buf = new byte[1024];
							int cnt;
							while ((cnt = is.read(buf)) != -1) {
								baos.write(buf, 0, cnt);
							}

							// TODO: allow multiple replies within one session (e.g. more than one AutoType
							// message)

							synchronized (PairingInfo.class) {
								if (instance != PairingInfo.this || sender != Thread.currentThread() || socket != s)
									return;

								onReply.accept(decrypt(baos.toByteArray()));

								if (sender == Thread.currentThread())
									sender = null;
							}
							return;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			});
			sender.start();
		}
	}

	private byte[] decrypt(byte[] reply) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException,
			IllegalBlockSizeException, BadPaddingException, IOException {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(reply);
				OmsDataInputStream dataInputStream = new OmsDataInputStream(bais)) {

			// (1) RSA transformation
			var rsaTransformationIdx = dataInputStream.readUnsignedShort();
			var rsaTransformation = RsaTransformation.values()[rsaTransformationIdx].transformation;

			// (2) Payload
			return RSAUtils.process(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate(), rsaTransformation,
					dataInputStream.readByteArray());
		}
	}

	public static int getRequestTimeoutS() {
		return Integer.parseInt(Main.properties.getProperty(PROP_REQUEST_TIMEOUT_S, "" + 30));
	}
}
