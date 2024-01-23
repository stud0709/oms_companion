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
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Random;
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
	private RSAPublicKey publicKeyConnection;
	private InetAddress inetAddress;
	private int port;
	private static Thread sender = null;
	private static Socket socket = null;
	private static PairingInfo instance = null;
	private static final String PROP_SO_TIMEOUT = "so_timeout";

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

	private byte[] getPrompt(int id) throws IOException, NoSuchAlgorithmException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

			// (1) application identifier
			dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_PAIRING_INFO);

			// (2) connection id
			dataOutputStream.writeUnsignedShort(id);

			// (3) own public key
			dataOutputStream.writeByteArray(((RSAPublicKey) rsaKeyPair.getPublic()).getEncoded());

			return baos.toByteArray();
		}
	}

	public void displayPrompt(Runnable andThen) throws IOException, NoSuchAlgorithmException {
		var rnd = new Random();
		var id = rnd.nextInt(65535);
		QRFrame.showForMessage(getPrompt(id), false, true, Integer.toString(id), reply -> onHandshake(reply, andThen));
	}

	private void onHandshake(String reply, Runnable andThen) {
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
			this.inetAddress = InetAddress.getByAddress(address);
			// (2) port
			this.port = dataInputStream.readShort();
			// (3) public key OneMoreSecret
			this.publicKeyConnection = RSAUtils.getFromX509(dataInputStream.readByteArray());

			// pairing successful
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
			var encryptedMessage = RSAUtils.process(Cipher.ENCRYPT_MODE, publicKeyConnection,
					RSAUtils.getRsaTransformation().transformation, message);
			dataOutputStream.writeByteArray(encryptedMessage);

			dataToSend = baos.toByteArray();
		}

		var ts = System.currentTimeMillis();
		var timeout = Integer.parseInt(Main.properties.getProperty(PROP_SO_TIMEOUT, "" + 30_000));

		synchronized (this) {
			sender = new Thread(() -> {
				while (!Thread.interrupted() && System.currentTimeMillis() - ts < timeout) {
					synchronized (PairingInfo.class) {
						if (instance != PairingInfo.this || sender != Thread.currentThread())
							return;
					}

					// try to connect
					try (var s = new Socket()) {
						s.connect(new InetSocketAddress(inetAddress, port),
								Integer.parseInt(Main.properties.getProperty(PROP_SO_TIMEOUT, "" + 5_000)));

						socket = s;

						try (OutputStream os = s.getOutputStream();
								InputStream is = s.getInputStream();
								ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
							os.write(dataToSend);
							os.flush();

							// now wait for the reply
							s.setSoTimeout(timeout);

							byte[] buf = new byte[1024];
							int cnt;
							while ((cnt = is.read(buf)) != -1) {
								baos.write(buf, 0, cnt);
							}

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
}
