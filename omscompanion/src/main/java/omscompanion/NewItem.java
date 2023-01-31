package omscompanion;

import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.border.EmptyBorder;

import com.google.zxing.common.BitMatrix;

public class NewItem extends JFrame implements WindowListener {
	private static final long serialVersionUID = 6915397327082537786L;

	private final String message;

	private JPanel contentPane;

	private Runnable andThen;

	/**
	 * Create the frame.
	 * 
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 * @throws InvalidAlgorithmParameterException
	 * @throws BadPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws NoSuchPaddingException
	 * @throws InvalidKeyException
	 */
	public NewItem(String s, Runnable andThen) throws Exception {
		setType(Type.UTILITY);
		setTitle("Encrypt Data from Clipboard");
		setResizable(false);
		setAlwaysOnTop(true);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		JTextArea txtrUseTheTool = new JTextArea();
		txtrUseTheTool.setWrapStyleWord(true);
		txtrUseTheTool.setLineWrap(true);
		txtrUseTheTool.setText(
				"We have encrypted your data and copied them in oms://...  format back to the clipboard. \r\n\r\nUse the tool bar below to generate other formats (they will be copied to the clipboard as well).\r\n\r\nThe clipboard will be cleared as soon as you close this window. ");
		contentPane.add(txtrUseTheTool, BorderLayout.CENTER);

		JToolBar toolBar = new JToolBar();
		toolBar.setRollover(true);
		toolBar.setFloatable(false);
		contentPane.add(toolBar, BorderLayout.SOUTH);

		JButton btnText = new JButton("oms://... ");
		toolBar.add(btnText);

		JButton btnGifClipboard = new JButton("GIF File");
		toolBar.add(btnGifClipboard);

		JButton btnImg = new JButton("GIF BASE64");
		toolBar.add(btnImg);

		// TODO: multiple public keys
		Path pkPath = Files.list(Main.PUBLIC_KEY_STORAGE).filter(p -> !Files.isDirectory(p))
				.filter(p -> p.getFileName().toString().endsWith(".x.509")).findAny().get();

		RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(pkPath));

		message = new EncryptedMessageTransfer(s.getBytes(), pk, EncryptedMessageTransfer.RSA_TRANSFORMATION)
				.getMessage();

		createTextLink();

		btnText.addActionListener(e -> onBtnText());
		btnGifClipboard.addActionListener(e -> onBtnGif());
		btnImg.addActionListener(e -> onBtnImg());

		this.andThen = andThen;

		addWindowListener(this);
	}

	private void createTextLink() {
		String omsURL = MessageComposer.asURL(message);
		ClipboardUtil.set(omsURL);
	}

	private void onBtnText() {
		createTextLink();

		JOptionPane.showMessageDialog(this,
				"Data (" + MessageComposer.OMS_URL + "...) has been copied to the clipboard");
	}

	private File generateGif() {
		File f = new File("qr.gif");
		f.deleteOnExit();

		try (FileImageOutputStream fios = new FileImageOutputStream(f)) {
			List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);
			AnimatedGifWriter.createGif(list, fios, QRFrame.DELAY);
			fios.flush();
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, ex.getMessage());
			return null;
		}

		return f;
	}

	private void onBtnGif() {
		File f = generateGif();

		if (f == null)
			return;

		ClipboardUtil.set(f);

		JOptionPane.showMessageDialog(this,
				"QR code sequence has been generated and copied to the clipboard as GIF image");
	}

	private void onBtnImg() {
		File f = generateGif();

		if (f == null)
			return;

		try {
			byte[] bArr = Files.readAllBytes(f.toPath());
			ClipboardUtil.set(Base64.getEncoder().encodeToString(bArr));
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, ex.getMessage());
			return;
		}

		JOptionPane.showMessageDialog(this,
				"QR code sequence has been generated and copied to the clipboard as BASE64");

	}

	@Override
	public void windowOpened(WindowEvent e) {

	}

	@Override
	public void windowClosing(WindowEvent e) {

	}

	@Override
	public void windowClosed(WindowEvent e) {
		// Clear the clipboard
		ClipboardUtil.set("");
		if (andThen != null)
			andThen.run();
	}

	@Override
	public void windowIconified(WindowEvent e) {

	}

	@Override
	public void windowDeiconified(WindowEvent e) {

	}

	@Override
	public void windowActivated(WindowEvent e) {

	}

	@Override
	public void windowDeactivated(WindowEvent e) {

	}
}
