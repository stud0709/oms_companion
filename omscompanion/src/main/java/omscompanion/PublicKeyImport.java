package omscompanion;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

public class PublicKeyImport extends JFrame {

	private static final long serialVersionUID = 4743883515322987016L;
	private JPanel contentPane;
	private JTextField textFieldKeyAlias;

	public PublicKeyImport() {
		setType(Type.UTILITY);
		setTitle("Private Key Import");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		contentPane.add(panel, BorderLayout.NORTH);

		JPanel panel_2 = new JPanel();
		panel_2.setLayout(new BoxLayout(panel_2, BoxLayout.X_AXIS));
		panel.add(panel_2);

		JLabel lblKeyAlias = new JLabel("Key Alias: ");
		panel_2.add(lblKeyAlias);

		textFieldKeyAlias = new JTextField();
		textFieldKeyAlias.setColumns(10);
		panel_2.add(textFieldKeyAlias);

		JTextArea textAreaKeyEntry = new JTextArea();
		textAreaKeyEntry.setLineWrap(true);
		textAreaKeyEntry.setBorder(new TitledBorder("Key Data (BASE64)"));
		contentPane.add(textAreaKeyEntry, BorderLayout.CENTER);

		JPanel panel_1 = new JPanel();
		contentPane.add(panel_1, BorderLayout.SOUTH);

		JButton btnSave = new JButton("Save");
		panel_1.add(btnSave);

		btnSave.addActionListener(e -> {
			try {
				String alias = this.textFieldKeyAlias.getText().trim();
				if (alias.isEmpty()) {
					throw new Exception("Key Alias may not be empty");
				}

				byte[] bArr = Base64.getDecoder().decode(textAreaKeyEntry.getText().trim().replaceAll("[\\r\\n]", ""));
				PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bArr));

				Path p = NewKeyPair.savePublicKey(alias, publicKey, backupPath -> {
					int choice = JOptionPane.showConfirmDialog(PublicKeyImport.this, "Create backup and overwrite?",
							"Key '" + alias + "' already exists.", JOptionPane.YES_NO_OPTION);

					return choice == JOptionPane.YES_OPTION;
				});

				if (p == null)
					return;

				JOptionPane.showMessageDialog(PublicKeyImport.this, "Import was successful!");
				this.setVisible(false);

			} catch (Exception ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, ex.getMessage());
			}
		});
	}

}
