package omscompanion;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.stream.FileImageOutputStream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public class QRFrame extends JFrame {
	private static final long serialVersionUID = -6749732054569148998L;
	private static QRFrame instance = null;
	private final JLabel lbl;
	private final Runnable andThen;
	public static final int AUTOCLOSE = 60_000;
	private final String message;
	private final AtomicBoolean cleanupOnDeactivate = new AtomicBoolean(true);

	public QRFrame(String message, long delay, Runnable andThen)
			throws NoSuchAlgorithmException, IOException, WriterException {
		this.message = message;

		AnimatedQrHelper qrHelper = new AnimatedQrHelper(message, delay, () -> instance == QRFrame.this,
				bi -> setImage(bi));

		this.andThen = andThen;

		synchronized (QRFrame.class) {
			if (instance != null) {
				instance.cleanup();
			}

			setTitle("omsCompanion");
			setLayout(new BorderLayout());
			setSize(qrHelper.getImages().get(0).getWidth(), qrHelper.getImages().get(0).getHeight());
//			setUndecorated(true);
			setResizable(false);
			setAlwaysOnTop(true);
			addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					System.out.println(e);
					switch (e.getKeyCode()) {
					case KeyEvent.VK_ESCAPE:
						cleanup();
						break;
					default:
						break;
					}
				}
			});
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					cleanup();
				}

				@Override
				public void windowIconified(WindowEvent e) {
					cleanup();
				}

				@Override
				public void windowDeactivated(WindowEvent e) {
					if (cleanupOnDeactivate.get())
						cleanup();
				}
			});
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentMoved(ComponentEvent e) {
					// TODO save new window position
				}
			});

			setAutoRequestFocus(true);
			setFocusableWindowState(true);

			lbl = new JLabel(new ImageIcon(qrHelper.getImages().get(0)));
			add(lbl, BorderLayout.CENTER);

			JPopupMenu menu = new JPopupMenu();

			JMenuItem saveGif = new JMenuItem("\u21e9 GIF");
			saveGif.addActionListener(e -> onBtnGif());
			menu.add(saveGif);

			JMenuItem saveGifBase64 = new JMenuItem("\u21e9 BASE64 GIF");
			saveGifBase64.addActionListener(e -> onBtnImg());
			menu.add(saveGifBase64);

			JMenuItem saveMessage = new JMenuItem("\u21e9 " + MessageComposer.OMS_PREFIX);
			saveMessage.addActionListener(e -> onBtnText());
			menu.add(saveMessage);

			lbl.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (SwingUtilities.isRightMouseButton(e)) {
						menu.show(QRFrame.this, e.getX(), e.getY());
					}
				};
			});

			pack();

			instance = this;
		}

		qrHelper.start();

		// close after some time
		if (AUTOCLOSE != 0) {
			qrHelper.getTimer().schedule(new TimerTask() {

				@Override
				public void run() {
					cleanup();
				}
			}, AUTOCLOSE);
		}
	}

	private void setImage(BufferedImage bi) {
		SwingUtilities.invokeLater(() -> lbl.setIcon(new ImageIcon(bi)));
	}

	private void cleanup() {
		synchronized (QRFrame.class) {
			if (instance == this) {
				setVisible(false);
				instance = null;

				if (andThen != null)
					andThen.run();
			}
			dispose();
		}
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

			cleanupOnDeactivate.set(false);
			JOptionPane.showMessageDialog(this, ex.getMessage());
			cleanupOnDeactivate.set(true);

			return null;
		}

		return f;
	}

	private void onBtnGif() {
		File f = generateGif();

		if (f == null)
			return;

		ClipboardUtil.set(f);

		cleanupOnDeactivate.set(false);
		JOptionPane.showMessageDialog(this,
				"QR code sequence has been generated and copied to the clipboard as GIF image");
		cleanupOnDeactivate.set(true);
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

			cleanupOnDeactivate.set(false);
			JOptionPane.showMessageDialog(this, ex.getMessage());
			cleanupOnDeactivate.set(true);

			return;
		}

		cleanupOnDeactivate.set(false);
		JOptionPane.showMessageDialog(this,
				"QR code sequence has been generated and copied to the clipboard as BASE64");
		cleanupOnDeactivate.set(true);

	}

	private void onBtnText() {
		createTextLink();

		cleanupOnDeactivate.set(false);
		JOptionPane.showMessageDialog(this,
				"Data (" + MessageComposer.OMS_PREFIX + "...) has been copied to the clipboard");
		cleanupOnDeactivate.set(true);
	}

	private void createTextLink() {
		String omsURL = MessageComposer.encodeAsOmsText(message);
		ClipboardUtil.set(omsURL);
	}
}
