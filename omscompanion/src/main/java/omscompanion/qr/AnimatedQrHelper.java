package omscompanion.qr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.imageio.stream.FileImageOutputStream;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import omscompanion.AnimatedGifWriter;
import omscompanion.Main;

public class AnimatedQrHelper {
	private static long DEFAULT_DELAY = 100;
	private static final String PROP_DELAY = "qr_seq_delay";

	private int idx = 0;
	List<BufferedImage> images = new ArrayList<>();
	private final Timer timer = new Timer();
	private final Supplier<Boolean> test;
	Consumer<BufferedImage> imageConsumer;

	public AnimatedQrHelper(char[] message, Supplier<Boolean> test, Consumer<BufferedImage> imageConsumer) {
		this.test = test;
		this.imageConsumer = imageConsumer;

		try {
			var list = QRUtil.getQrSequence(message, QRUtil.getChunkSize(), QRUtil.getBarcodeSize());
			images = list.stream().map(m -> MatrixToImageWriter.toBufferedImage(m)).collect(Collectors.toList());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<BufferedImage> getImages() {
		return images;
	}

	public static long getSequenceDelay() {
		return Long.parseLong(Main.properties.getProperty(PROP_DELAY, "" + DEFAULT_DELAY));
	}

	public void start() {
		imageConsumer.accept(images.get(0));
		var delay = getSequenceDelay();

		timer.schedule(getTimerTask(delay), delay);
	}

	public Timer getTimer() {
		return timer;
	}

	private TimerTask getTimerTask(long delay) {
		return new TimerTask() {

			@Override
			public void run() {
				synchronized (AnimatedQrHelper.class) {
					if (!test.get())
						return;

					idx++;

					if (idx >= images.size())
						idx = 0;

					imageConsumer.accept(images.get(idx));

					timer.schedule(getTimerTask(delay), delay);
				}
			}
		};
	}

	public static File generateGif(char[] message) throws FileNotFoundException, IOException, WriterException {
		var f = new File("qr.gif");
		f.deleteOnExit();

		try (var fios = new FileImageOutputStream(f)) {
			var list = QRUtil.getQrSequence(message, QRUtil.getChunkSize(), QRUtil.getBarcodeSize());
			AnimatedGifWriter.createGif(list, fios, getSequenceDelay());
			fios.flush();
		}

		return f;
	}
}
