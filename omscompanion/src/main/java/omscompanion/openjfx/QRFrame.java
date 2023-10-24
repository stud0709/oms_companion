package omscompanion.openjfx;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import com.google.zxing.WriterException;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.qr.AnimatedQrHelper;
import omscompanion.qr.QRUtil;

public class QRFrame {
	private byte[] message;
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

	@FXML
	private TextField txtInput;

	private static long getQrFrameAutoclose() {
		return Long.parseLong(Main.properties.getProperty(PROP_QR_FRAME_AUTOCLOSE, "30000"));
	}

	public void init(byte[] message, Stage stage, boolean allowTextInput) throws WriterException {
		this.message = message;

		synchronized (QRFrame.class) {
			instance = stage;
		}

		var img = new WritableImage(QRUtil.getBarcodeSize(), QRUtil.getBarcodeSize());

		imgQR.setFitWidth(img.getWidth());
		imgQR.setFitHeight(img.getHeight());
		imgQR.setImage(img);

		if (allowTextInput) {
			txtInput.requestFocus();
		} else {
			txtInput.setVisible(false);
		}

		var qrHelper = new AnimatedQrHelper(MessageComposer.encodeAsOmsText(message).toCharArray(),
				() -> instance == stage, bi -> Platform.runLater(() -> SwingFXUtils.toFXImage(bi, img)));

		qrHelper.start();
	}

	@FXML
	void onMenuItemAction(ActionEvent event) {
		switch (((MenuItem) event.getSource()).getId()) {
		case "mItmAsText":
			FxMain.onAsText(message);
			break;
		case "mItmAsGifFile":
			FxMain.onGifFile(message);
			break;
		case "mItmAsBase64":
			FxMain.onGifBase64(message);
			break;
		}
	}

	public static void showForMessage(byte[] message, boolean autoClose, boolean allowTextInput,
			Consumer<String> andThen) {
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
				frameController.init(message, stage, allowTextInput);
				stage.setTitle("omsCompanion");
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					String s = frameController.txtInput.getText();

					synchronized (QRFrame.class) {
						if (instance == stage)
							instance = null;
					}

					if (andThen != null)
						andThen.accept(s);
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
					andThen.accept(null);
			}
		});
	}

	@FXML
	void onKeyPressed(KeyEvent event) {
		if (event.getCode() == KeyCode.ENTER) {
			instance.getWindows().get(0).hide();
		}
	}
}
