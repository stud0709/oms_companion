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
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.google.zxing.common.BitMatrix;

import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedMessageTransfer;
import omscompanion.crypto.RSAUtils;
import omscompanion.qr.AnimatedQrHelper;
import omscompanion.qr.QRFrame;
import omscompanion.qr.QRUtil;

public class NewItem extends JFrame implements WindowListener {
	private static final long serialVersionUID = 6915397327082537786L;

	private String message;

	private JPanel contentPane;

	private Runnable andThen;

	private static final String FILE_TYPE_PUCLIC_KEY = ".x.509";

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
		setBounds(100, 100, 500, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		contentPane.add(panel, BorderLayout.SOUTH);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JTextArea txtrUseTheTool = new JTextArea();
		txtrUseTheTool.setEditable(false);
		txtrUseTheTool.setWrapStyleWord(true);
		txtrUseTheTool.setLineWrap(true);
		txtrUseTheTool.setText("We have encrypted your data and copied them in " + MessageComposer.OMS_PREFIX
				+ "...  format back to the clipboard. \r\n\r\nUse the tool bar below to generate other formats (they will be copied to the clipboard as well).\r\n\r\nThe clipboard will be cleared as soon as you close this window. ");
		contentPane.add(txtrUseTheTool, BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		contentPane.add(panel_1, BorderLayout.SOUTH);
		panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.Y_AXIS));

		JPanel panel_2 = new JPanel();
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.X_AXIS));
		panel_1.add(panel_2);

		JPanel panel_3 = new JPanel();
		panel_3.setLayout(new BoxLayout(panel_3, BoxLayout.X_AXIS));
		panel_1.add(panel_3);

		JLabel lblNewLabel = new JLabel("Key: ");
		panel_2.add(lblNewLabel);

		List<String> publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE).map(p -> p.getFileName().toString())
				.filter(fn -> fn.toLowerCase().endsWith(FILE_TYPE_PUCLIC_KEY)).collect(Collectors.toList());
		String[] items = publicKeys.toArray(new String[] {});

		JComboBox<String> comboUseKey = new JComboBox<>();
		comboUseKey.setEditable(false);
		comboUseKey.setModel(new DefaultComboBoxModel<>(items));

		String defaultKey = Main.properties.getProperty(Main.PROP_DEFAULT_KEY);

		if (items.length == 1 || defaultKey == null || !publicKeys.contains(defaultKey)) {
			comboUseKey.setSelectedIndex(0);
		} else {
			comboUseKey.setSelectedItem(defaultKey);
		}

		comboUseKey.addItemListener(e -> onKeySelected(comboUseKey, s));

		panel_2.add(comboUseKey);

		JButton btnSetDefault = new JButton("Set Default");
		btnSetDefault.addActionListener(e -> onSetDefault(comboUseKey));
		btnSetDefault.setEnabled(publicKeys.size() > 1);
		panel_3.add(btnSetDefault);

		JButton btnText = new JButton(MessageComposer.OMS_PREFIX);
		btnText.setEnabled(!publicKeys.isEmpty());
		panel_3.add(btnText);

		JButton btnGifClipboard = new JButton("GIF File");
		btnGifClipboard.setEnabled(!publicKeys.isEmpty());
		panel_3.add(btnGifClipboard);

		JButton btnImg = new JButton("GIF BASE64");
		btnImg.setEnabled(!publicKeys.isEmpty());
		panel_3.add(btnImg);

		JButton btnPreview = new JButton("Preview QR");
		btnPreview.setEnabled(!publicKeys.isEmpty());
		btnPreview.addActionListener(e -> onPreview(btnPreview));
		panel_3.add(btnPreview);

		btnText.addActionListener(e -> onBtnText());
		btnGifClipboard.addActionListener(e -> onBtnGif());
		btnImg.addActionListener(e -> onBtnImg());

		this.andThen = andThen;

		addWindowListener(this);

		if (publicKeys.isEmpty()) {
			txtrUseTheTool
					.setText("Cannot encrypt: no keys found in " + Main.PUBLIC_KEY_STORAGE.toAbsolutePath().toString());
		} else {
			encryptMessage(comboUseKey, s);
			createTextLink();
		}
	}

	private void onPreview(JButton btn) {
		btn.setEnabled(false);
		try {
			new QRFrame(message, AnimatedQrHelper.DELAY, () -> SwingUtilities.invokeLater(() -> btn.setEnabled(true)))
					.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void onKeySelected(JComboBox<String> comboUseKey, String s) {
		try {
			encryptMessage(comboUseKey, s);
			createTextLink();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void encryptMessage(JComboBox<String> comboUseKey, String s) throws Exception {
		Path pkPath = Main.PUBLIC_KEY_STORAGE.resolve((String) comboUseKey.getSelectedItem());

		RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(pkPath));

		message = new EncryptedMessageTransfer(s.getBytes(), AESUtil.getKeyLength(), pk,
				RSAUtils.getRsaTransformationIdx(), AESUtil.getAesTransformationIdx()).getMessage();
	}

	private void onSetDefault(JComboBox<String> comboUseKey) {
		Main.properties.put(Main.PROP_DEFAULT_KEY, comboUseKey.getSelectedItem());
	}

	private void createTextLink() {
		String omsURL = MessageComposer.encodeAsOmsText(message);
		ClipboardUtil.set(omsURL);
	}

	private void onBtnText() {
		createTextLink();

		JOptionPane.showMessageDialog(this,
				"Data (" + MessageComposer.OMS_PREFIX + "...) has been copied to the clipboard");
	}

	private File generateGif() {
		File f = new File("qr.gif");
		f.deleteOnExit();

		try (FileImageOutputStream fios = new FileImageOutputStream(f)) {
			List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);
			AnimatedGifWriter.createGif(list, fios, AnimatedQrHelper.DELAY);
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
