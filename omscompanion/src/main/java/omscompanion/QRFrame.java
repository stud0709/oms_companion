package omscompanion;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

public class QRFrame extends JFrame {
	private static final long serialVersionUID = -6749732054569148998L;
	private static QRFrame instance = null;
	private List<ImageIcon> images = new ArrayList<>();
	private final JLabel lbl;
	private final Timer timer = new Timer();
	private int idx = 0;
	private long delay;
	private final Runnable andThen;
	public static final int DELAY = 100;

	public QRFrame(String message, long delay, Runnable andThen)
			throws NoSuchAlgorithmException, IOException, WriterException {

		List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);

		this.delay = delay;
		this.andThen = andThen;

		synchronized (QRFrame.class) {
			if (instance != null) {
				instance.cleanup();
			}

			images = list.stream().map(m -> MatrixToImageWriter.toBufferedImage(m)).map(bi -> new ImageIcon(bi))
					.collect(Collectors.toList());

			setTitle("omsCompanion");
			setLayout(new BorderLayout());
			setSize(images.get(0).getIconWidth(), images.get(0).getIconHeight());
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

			lbl = new JLabel(images.get(0));
			add(lbl, BorderLayout.CENTER);
			pack();

			instance = this;
		}

		timer.schedule(getTimerTask(), this.delay);
	}

	private TimerTask getTimerTask() {
		return new TimerTask() {

			@Override
			public void run() {
				synchronized (QRFrame.class) {
					if (instance != QRFrame.this)
						return;

					idx++;

					if (idx >= images.size())
						idx = 0;

					SwingUtilities.invokeLater(() -> lbl.setIcon(images.get(idx)));

					timer.schedule(getTimerTask(), QRFrame.this.delay);
				}
			}
		};
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
