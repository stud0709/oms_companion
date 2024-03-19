package omscompanion.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import omscompanion.Base58;
import omscompanion.FxMain;
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
	public static final String PROP_SO_TIMEOUT = "so_timeout", PROP_REQUEST_TIMEOUT_S = "request_timeout_s",
			PROP_PAIRING_DEF_INIT_KEY = "pairing_def_init_key";
	private static final Logger logger = Logger.getLogger(PairingInfo.class.getName());

	public record ConnectionSettings(RSAPublicKey publicKeySend, RSAPublicKey initialKey, InetAddress inetAddress,
			int port) {
		public ConnectionSettings withPublicKeySend(RSAPublicKey pKey) {
			return new ConnectionSettings(pKey, initialKey, inetAddress, port);
		}

		public ConnectionSettings withInetAddress(InetAddress adr) {
			return new ConnectionSettings(publicKeySend, initialKey, adr, port);
		}

		public ConnectionSettings withPort(int p) {
			return new ConnectionSettings(publicKeySend, initialKey, inetAddress, p);
		}

		public ConnectionSettings withInitialKey(RSAPublicKey iKey) {
			return new ConnectionSettings(publicKeySend, iKey, inetAddress, port);
		}

		public ConnectionSettings() {
			this(null, null, null, 0);
		}
	}

	public PairingInfo() throws NoSuchAlgorithmException {

		rsaKeyPair = RSAUtils.newKeyPair(RSAUtils.getKeyLength());

		synchronized (PairingInfo.class) {
			instance = this;
			sender = null;
			if (socket != null) {
				logger.info("Cleanup on new PairingInfo");
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				socket = null;
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
			if (socket != null) {
				logger.info("Cleanup on disconnect");
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				socket = null;
			}
		}
	}

	private byte[] getPrompt(String id, ConnectionSettings connectionSettings, byte[] privateKeyMaterialSend)
			throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
			// (1) application identifier
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_WIFI_PAIRING);

			// (2) Pairing Request ID
			dataOutputStream.writeString(id);

			// (3) own public key
			dataOutputStream.writeByteArray(((RSAPublicKey) rsaKeyPair.getPublic()).getEncoded());

			// (4) private key for the other party to answer
			dataOutputStream.writeByteArray(privateKeyMaterialSend);

			return MessageComposer.createRsaAesEnvelope(connectionSettings.initialKey, RSAUtils.getTransformationIdx(),
					AESUtil.getKeyLength(), AESUtil.getTransformationIdx(), baos.toByteArray());
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
				if (andThen != null)
					andThen.run();
			}
			return;
		}

		try (ByteArrayInputStream bais = new ByteArrayInputStream(Base58.decode(reply))) {
			// (1) IP address
			var bArr = new byte[4];
			bais.read(bArr);
			var iAddress = InetAddress.getByAddress(bArr);

			// (2) port
			bais.read(bArr);
			var port = ByteBuffer.wrap(bArr).getInt();

			// pairing successful
			this.connectionSettings = connectionSettings.withInetAddress(iAddress).withPort(port);

			synchronized (PairingInfo.class) {
				instance = this;
			}
		} catch (Exception ex) {
			synchronized (PairingInfo.class) {
				instance = null;
			}
			FxMain.handleException(ex);
		} finally {
			if (andThen != null)
				andThen.run();
		}
	}

	public void sendMessage(byte[] message, Consumer<byte[]> onReply)
			throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
			BadPaddingException, IOException {

		var ts = System.currentTimeMillis();
		var timeout = getRequestTimeoutS() * 1000;

		synchronized (this) {
			sender = new Thread(() -> {
				try {
					logger.info("Thread started");
					while (!Thread.interrupted() && System.currentTimeMillis() - ts < timeout) {
						synchronized (PairingInfo.class) {
							if (instance != PairingInfo.this || sender != Thread.currentThread())
								return;
						}

						// try to connect
						try (var s = new Socket()) {
							s.setSoTimeout(timeout);

							logger.info("Connecting...");

							s.connect(new InetSocketAddress(connectionSettings.inetAddress, connectionSettings.port),
									Integer.parseInt(Main.properties.getProperty(PROP_SO_TIMEOUT, "" + 1_000)));

							socket = s;

							logger.info("connected");

							var os = s.getOutputStream();

							try (var dataInputStream = new OmsDataInputStream(s.getInputStream())) {

								var dataToSend = MessageComposer.createRsaAesEnvelope(connectionSettings.publicKeySend,
										RSAUtils.getTransformationIdx(), AESUtil.getKeyLength(),
										AESUtil.getTransformationIdx(), message);

								os.write(dataToSend);
								os.flush();

								logger.info("Data has been sent, waiting for reply");

								s.shutdownOutput();

								// now wait for the reply
								var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);
								var encryptedMessage = dataInputStream.readByteArray();

								logger.info(
										String.format("Reply has been received, %d bytes", encryptedMessage.length));

								// decrypt AES key
								var aesSecretKeyData = RSAUtils.process(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate(),
										envelope.rsaTransormation(), envelope.encryptedAesSecretKey());
								var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

								// (7) AES-encrypted message
								var decryptedMessage = AESUtil.process(Cipher.DECRYPT_MODE, encryptedMessage,
										aesSecretKey, new IvParameterSpec(envelope.iv()), envelope.aesTransformation());

								synchronized (PairingInfo.class) {
									if (instance != PairingInfo.this || sender != Thread.currentThread()
											|| socket != s) {
										logger.info("Instance, thread or socket mismatch");
										return;
									}

									logger.info("forwarding reply to the consumer");
									onReply.accept(decryptedMessage);

									if (sender == Thread.currentThread())
										sender = null;
								}

								return;
							}
						} catch (Exception e) {
							e.printStackTrace();
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
								break;
							}
						} finally {
							logger.info("Socket closed");
						}
					}

					logger.info("Request timed out");
					onReply.accept(null); // pass empty reply
				} finally {
					logger.info("Thread stopped");
				}
			});
			sender.start();
		}
	}

	public static int getRequestTimeoutS() {
		return Integer.parseInt(Main.properties.getProperty(PROP_REQUEST_TIMEOUT_S, "" + 30));
	}

	public static String getDefaultInitialKeyAlias() {
		return Main.properties.getProperty(PROP_PAIRING_DEF_INIT_KEY, Main.getDefaultKeyAlias());
	}
}
