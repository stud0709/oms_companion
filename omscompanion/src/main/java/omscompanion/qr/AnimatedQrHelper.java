package omscompanion.qr;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

public class AnimatedQrHelper {
	public static long DELAY = 100;

	private int idx = 0;
	List<BufferedImage> images = new ArrayList<>();
	private final Timer timer = new Timer();
	private final Supplier<Boolean> test;
	private final long delay;
	Consumer<BufferedImage> imageConsumer;

	public AnimatedQrHelper(String message, long delay, Supplier<Boolean> test, Consumer<BufferedImage> imageConsumer) {
		this.test = test;
		this.delay = delay;
		this.imageConsumer = imageConsumer;

		try {
			List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);
			images = list.stream().map(m -> MatrixToImageWriter.toBufferedImage(m)).collect(Collectors.toList());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<BufferedImage> getImages() {
		return images;
	}

	public void start() {
		imageConsumer.accept(images.get(0));

		timer.schedule(getTimerTask(), this.delay);
	}

	public Timer getTimer() {
		return timer;
	}

	private TimerTask getTimerTask() {
		return new TimerTask() {

			@Override
			public void run() {
				synchronized (QRFrame.class) {
					if (!test.get())
						return;

					idx++;

					if (idx >= images.size())
						idx = 0;

					imageConsumer.accept(images.get(idx));

					timer.schedule(getTimerTask(), delay);
				}
			}
		};
	}
}
