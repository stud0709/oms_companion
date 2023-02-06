package omscompanion;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.TimerTask;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.google.zxing.WriterException;

public class QRFrame extends JFrame {
	private static final long serialVersionUID = -6749732054569148998L;
	private static QRFrame instance = null;
	private final JLabel lbl;
	private final Runnable andThen;
	public static final int AUTOCLOSE = 60_000;

	public QRFrame(String message, long delay, Runnable andThen)
			throws NoSuchAlgorithmException, IOException, WriterException {

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
}
