package omscompanion.openjfx;

import java.util.Timer;
import java.util.TimerTask;

import com.google.zxing.WriterException;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.qr.AnimatedQrHelper;
import omscompanion.qr.QRUtil;

public class QRFrame {
	private String message;
	private static final Timer timer = new Timer();
	private static Stage instance;

	private static final String PROP_QR_FRAME_AUTOCLOSE = "qr_frame_autoclose";

	@FXML
	private ContextMenu ctxMenu;

	@FXML
	private Label lblQrCode;

	@FXML
	private MenuItem mItmAsBase64;

	@FXML
	private MenuItem mItmAsGifFile;

	@FXML
	private MenuItem mItmAsText;

	@FXML
	private ImageView imgQR;

	private static long getQrFrameAutoclose() {
		return Long.parseLong(Main.properties.getProperty(PROP_QR_FRAME_AUTOCLOSE, "60000"));
	}

	public void init(String message, Stage stage) throws WriterException {
		this.message = message;

		synchronized (QRFrame.class) {
			instance = stage;
		}

		var img = new WritableImage(QRUtil.getBarcodeSize(), QRUtil.getBarcodeSize());

		imgQR.setFitWidth(img.getWidth());
		imgQR.setFitHeight(img.getHeight());
		imgQR.setImage(img);

		var qrHelper = new AnimatedQrHelper(message.toCharArray(), () -> instance == stage,
				bi -> Platform.runLater(() -> SwingFXUtils.toFXImage(bi, img)));

		qrHelper.start();
	}

	@FXML
	void onMenuItemAction(ActionEvent event) {
		switch (((Node) event.getSource()).getId()) {
		case "mItmAsText":
			FxMain.onAsText(message.getBytes());
			break;
		case "mItmAsGifFile":
			FxMain.onGifFile(message.toCharArray());
			break;
		case "mItmAsBase64":
			FxMain.onGifBase64(message.toCharArray());
			break;
		}
	}

	public static void showForMessage(String message, boolean autoClose, Runnable andThen) {
		if (instance != null) {
			Platform.runLater(() -> instance.close());
		}

		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/QRFrame.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				var frameController = (QRFrame) fxmlLoader.getController();
				var stage = new Stage();
				frameController.init(message, stage);
				stage.setTitle("omsCompanion");
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					synchronized (QRFrame.class) {
						if (instance == stage)
							instance = null;
					}

					if (andThen != null)
						andThen.run();
				});
				if (autoClose) {
					// schedule automatic close
					var tt = new TimerTask() {
						@Override
						public void run() {
							Platform.runLater(() -> stage.close());
						}
					};
					timer.schedule(tt, getQrFrameAutoclose());
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				if (andThen != null)
					andThen.run();
			}
		});
	}
}
