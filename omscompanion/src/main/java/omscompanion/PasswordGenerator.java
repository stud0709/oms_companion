package omscompanion;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.stream.FileImageOutputStream;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.google.zxing.common.BitMatrix;

import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedMessageTransfer;
import omscompanion.crypto.RSAUtils;
import omscompanion.qr.AnimatedQrHelper;
import omscompanion.qr.QRFrame;
import omscompanion.qr.QRUtil;

public class PasswordGenerator extends JFrame implements WindowListener, ItemListener, ActionListener {

	private static final long serialVersionUID = -580194366071379247L;

	private JPanel contentPane;
	private final JTextArea textAreaPassword;

	private static final String PROP_UCASE = "pwgen_ucase", PROP_UCASE_LIST = "pwgen_ucase_list",
			PROP_LCASE = "pwgen_lcase", PROP_LCASE_LIST = "pwgen_lcase_list", PROP_DIGITS = "pwgen_digits",
			PROP_DIGITS_LIST = "pwgen_digits_list", PROP_SPECIALS = "pwgen_specials",
			PROP_SPECIALS_LIST = "pwgen_specials_list", PROP_SIMILAR = "pwgen_similar",
			PROP_SIMILAR_LIST = "pwgen_similar_LIST", PROP_PWD_LENGTH = "pwgen_length", PROP_OCCURS = "pwgen_occurrs",
			DEFAULT_DIGITS = "0123456789", DEFAULT_LCASE = "abcdefghijklmnopqrstuvwxyz",
			DEFAULT_SPECIALS = "!#$%&'*+,-.:;<=>?@_~", DEFAULT_SIMILAR = "01IOl|";

	private static final int PWD_LEN_DEFAULT = 10, PWD_LEN_MIN = 5, PWD_LEN_MAX = 50, OCCURS_DEFAULT = 1,
			OCCURS_MIN = 1, OCCURS_MAX = 10;
	private JCheckBox chkUcase;
	private JCheckBox chkLcase;
	private JCheckBox chkDigits;
	private JCheckBox chkSpecials;
	private JCheckBox chkSimilar;
	private JSpinner spinnerLength;
	private JSpinner spinnerOccurrence;

	private JComboBox<String> comboUseKey;

	/**
	 * Create the frame.
	 * 
	 * @throws IOException
	 */
	public PasswordGenerator() throws IOException {
		setTitle("Password Generator");
		setType(Type.UTILITY);
		setResizable(false);
		setAlwaysOnTop(true);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 511, 312);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu mnSettings = new JMenu("Settings");
		menuBar.add(mnSettings);

		JMenuItem mnItemUcase = new JMenuItem("Upper-Case...");
		mnItemUcase.addActionListener(this);
		mnItemUcase.setActionCommand(PROP_UCASE_LIST);
		mnSettings.add(mnItemUcase);

		JMenuItem mnItemLcase = new JMenuItem("Lower-Case...");
		mnItemLcase.setActionCommand(PROP_LCASE_LIST);
		mnItemLcase.addActionListener(this);
		mnSettings.add(mnItemLcase);

		JMenuItem mnItemDigits = new JMenuItem("Digits...");
		mnItemDigits.setActionCommand(PROP_DIGITS_LIST);
		mnItemDigits.addActionListener(this);
		mnSettings.add(mnItemDigits);

		JMenuItem mnItemSpecials = new JMenuItem("Specials...");
		mnItemSpecials.setActionCommand(PROP_SPECIALS_LIST);
		mnItemSpecials.addActionListener(this);
		mnSettings.add(mnItemSpecials);

		JMenuItem mnItemSimilar = new JMenuItem("Similar...");
		mnItemSimilar.setActionCommand(PROP_SIMILAR_LIST);
		mnItemSimilar.addActionListener(this);
		mnSettings.add(mnItemSimilar);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

		JPanel panel = new JPanel();
		contentPane.add(panel);

		chkUcase = new JCheckBox("A...Z");
		chkUcase.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_UCASE, "true")));
		chkUcase.setActionCommand(PROP_UCASE);
		chkUcase.addItemListener(this);
		panel.add(chkUcase);

		chkLcase = new JCheckBox("a...z");
		chkLcase.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_LCASE, "true")));
		chkLcase.setActionCommand(PROP_LCASE);
		chkLcase.addItemListener(this);
		panel.add(chkLcase);

		chkDigits = new JCheckBox("0...9");
		chkDigits.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_DIGITS, "true")));
		chkDigits.setActionCommand(PROP_DIGITS);
		chkDigits.addItemListener(this);
		panel.add(chkDigits);

		chkSpecials = new JCheckBox("!%&#...");
		chkSpecials.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_SPECIALS, "true")));
		chkSpecials.setActionCommand(PROP_SPECIALS);
		chkSpecials.addItemListener(this);
		panel.add(chkSpecials);

		chkSimilar = new JCheckBox("Similar");
		chkSimilar.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_SIMILAR, "true")));
		chkSimilar.setActionCommand(PROP_SIMILAR);
		chkSimilar.addItemListener(this);
		panel.add(chkSimilar);

		JPanel panel_4 = new JPanel();
		contentPane.add(panel_4);

		JLabel lblLength = new JLabel("Length:");
		panel_4.add(lblLength);

		spinnerLength = new JSpinner();
		panel_4.add(spinnerLength);
		int pwdLen = Integer.parseInt(Main.properties.getProperty(PROP_PWD_LENGTH, "" + PWD_LEN_DEFAULT));
		spinnerLength.setModel(new SpinnerNumberModel(pwdLen, PWD_LEN_MIN, PWD_LEN_MAX, 1));
		spinnerLength.addChangeListener(e -> {
			Main.properties.setProperty(PROP_PWD_LENGTH, spinnerLength.getValue().toString());
			newPassword();
		});

		JLabel lblOccurrence = new JLabel("Occurrence:");
		panel_4.add(lblOccurrence);

		spinnerOccurrence = new JSpinner();
		panel_4.add(spinnerOccurrence);
		int occurs = Integer.parseInt(Main.properties.getProperty(PROP_OCCURS, "" + OCCURS_DEFAULT));
		spinnerOccurrence.setModel(new SpinnerNumberModel(occurs, OCCURS_MIN, OCCURS_MAX, 1));
		spinnerOccurrence.addChangeListener(e -> {
			Main.properties.setProperty(PROP_OCCURS, spinnerOccurrence.getValue().toString());
			newPassword();
		});

		JButton btnGenPwd = new JButton("Generate");
		btnGenPwd.addActionListener(e -> newPassword());
		panel_4.add(btnGenPwd);

		textAreaPassword = new JTextArea();
		textAreaPassword.setLineWrap(true);
		contentPane.add(textAreaPassword);

		JPanel panel_1 = new JPanel();
		contentPane.add(panel_1);
		panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.Y_AXIS));

		JPanel panel_2 = new JPanel();
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.X_AXIS));
		panel_1.add(panel_2);

		JPanel panel_3 = new JPanel();
		panel_3.setLayout(new BoxLayout(panel_3, BoxLayout.X_AXIS));
		panel_1.add(panel_3);

		JLabel lblKeyLabel = new JLabel("Key: ");
		panel_2.add(lblKeyLabel);

		List<String> publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE).map(p -> p.getFileName().toString())
				.filter(fn -> fn.toLowerCase().endsWith(NewItem.FILE_TYPE_PUCLIC_KEY)).collect(Collectors.toList());
		String[] items = publicKeys.toArray(new String[] {});

		comboUseKey = new JComboBox<>();
		comboUseKey.setEditable(false);
		comboUseKey.setModel(new DefaultComboBoxModel<>(items));

		String defaultKey = Main.getDefaultKey();

		if (items.length == 1 || defaultKey == null || !publicKeys.contains(defaultKey)) {
			comboUseKey.setSelectedIndex(0);
		} else {
			comboUseKey.setSelectedItem(defaultKey);
		}

		panel_2.add(comboUseKey);

		JButton btnSetDefault = new JButton("Set Default");
		btnSetDefault.addActionListener(e -> onSetDefault(comboUseKey));
		btnSetDefault.setEnabled(!publicKeys.isEmpty());
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

		newPassword();

		if (publicKeys.isEmpty()) {
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PasswordGenerator.this,
					"Cannot encrypt: no keys found in " + Main.PUBLIC_KEY_STORAGE.toAbsolutePath().toString()));
		}

	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		JCheckBox checkBox = (JCheckBox) e.getItem();
		Main.properties.setProperty(checkBox.getActionCommand(), Boolean.toString(checkBox.isSelected()));
		newPassword();
	}

	private void onPreview(JButton btn) {
		btn.setEnabled(false);
		try {
			new QRFrame(encryptMessage(), AnimatedQrHelper.getSequenceDelay(),
					() -> SwingUtilities.invokeLater(() -> btn.setEnabled(true))).setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String encryptMessage() throws Exception {
		Path pkPath = Main.PUBLIC_KEY_STORAGE.resolve((String) comboUseKey.getSelectedItem());

		RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(pkPath));

		return new EncryptedMessageTransfer(textAreaPassword.getText().getBytes(), pk,
				RSAUtils.getRsaTransformationIdx(), AESUtil.getKeyLength(), AESUtil.getAesTransformationIdx())
				.getMessage();
	}

	private void onSetDefault(JComboBox<String> comboUseKey) {
		Main.setDefaultKeyAlias((String) comboUseKey.getSelectedItem());
	}

	private void createTextLink() {
		try {
			String omsURL = MessageComposer.encodeAsOmsText(encryptMessage());
			ClipboardUtil.set(omsURL);
		} catch (Exception e) {
			e.printStackTrace();
		}

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
			List<BitMatrix> list = QRUtil.getQrSequence(encryptMessage(), QRUtil.getChunkSize(),
					QRUtil.getBarcodeSize());
			AnimatedGifWriter.createGif(list, fios, AnimatedQrHelper.getSequenceDelay());
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

	private void newPassword() {
		List<String> charClasses = new ArrayList<>();

		int length = Integer.parseInt(Main.properties.getProperty(PROP_PWD_LENGTH, "" + PWD_LEN_DEFAULT));

		if (chkUcase.isSelected()) {
			charClasses.add(Main.properties.getProperty(PROP_UCASE_LIST, DEFAULT_LCASE.toUpperCase()));
		}
		if (chkLcase.isSelected()) {
			charClasses.add(Main.properties.getProperty(PROP_LCASE_LIST, DEFAULT_LCASE));
		}
		if (chkDigits.isSelected()) {
			charClasses.add(Main.properties.getProperty(PROP_DIGITS_LIST, DEFAULT_DIGITS));
		}
		if (chkSpecials.isSelected()) {
			charClasses.add(Main.properties.getProperty(PROP_SPECIALS_LIST, DEFAULT_SPECIALS));
		}

		int size = charClasses.size();

		if (!chkSimilar.isSelected()) {
			char[] blacklist = Main.properties.getProperty(PROP_SIMILAR_LIST, DEFAULT_SIMILAR).toCharArray();
			Arrays.sort(blacklist);

			for (int i = 0; i < size; i++) {
				// deactivating "similar" will remove all similar characters
				StringBuilder sb = new StringBuilder();
				char[] cArr = charClasses.remove(0).toCharArray();
				for (char c : cArr) {
					if (Arrays.binarySearch(blacklist, c) < 0) {
						sb.append(c);
					}
				}
				charClasses.add(sb.toString());
			}
		}

		// remove duplicates
		for (int i = 0; i < size; i++) {
			String s = charClasses.remove(0);
			charClasses.add(s.replaceAll("(.)\\1+", "$1"));
		}

		// remove empty classes
		charClasses = charClasses.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

		if (charClasses.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No symbols available, check settings");
			return;
		}

		// ensure minimal occurrence
		List<String> list = new ArrayList<>();
		for (int i = 0; i < Integer.parseInt(Main.properties.getProperty(PROP_OCCURS, "" + OCCURS_DEFAULT)); i++) {
			for (String s : charClasses) {
				list.add(s);
			}
		}

		SecureRandom rnd = new SecureRandom();

		while (list.size() < length) {
			list.add(charClasses.get(rnd.nextInt(charClasses.size())));
		}

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < length; i++) {
			String s = list.remove(rnd.nextInt(list.size()));
			char cArr[] = s.toCharArray();
			sb.append(cArr[rnd.nextInt(cArr.length)]);
		}

		textAreaPassword.setText(sb.toString());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String caption = ((JMenuItem) e.getSource()).getText();
		String value = Main.properties.getProperty(e.getActionCommand());

		if (value == null) {
			switch (e.getActionCommand()) {
			case PROP_DIGITS_LIST:
				value = DEFAULT_DIGITS;
				break;
			case PROP_SIMILAR_LIST:
				value = DEFAULT_SIMILAR;
				break;
			case PROP_LCASE_LIST:
				value = DEFAULT_LCASE;
				break;
			case PROP_UCASE_LIST:
				value = DEFAULT_LCASE.toUpperCase();
				break;
			case PROP_SPECIALS_LIST:
				value = DEFAULT_SPECIALS;
				break;
			}
		}

		value = JOptionPane.showInputDialog(chkDigits, caption, value);

		if (value == null)
			return;

		Main.properties.setProperty(e.getActionCommand(), value);
	}
}
