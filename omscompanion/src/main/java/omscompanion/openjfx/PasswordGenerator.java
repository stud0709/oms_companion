package omscompanion.openjfx;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.ClipboardUtil;
import omscompanion.FxMain;
import omscompanion.Main;

public class PasswordGenerator {
	private static final String PROP_UCASE = "pwgen_ucase", PROP_UCASE_LIST = "pwgen_ucase_list",
			PROP_LCASE = "pwgen_lcase", PROP_LCASE_LIST = "pwgen_lcase_list", PROP_DIGITS = "pwgen_digits",
			PROP_DIGITS_LIST = "pwgen_digits_list", PROP_SPECIALS = "pwgen_specials",
			PROP_SPECIALS_LIST = "pwgen_specials_list", PROP_SIMILAR = "pwgen_similar",
			PROP_SIMILAR_LIST = "pwgen_similar_LIST", PROP_PWD_LENGTH = "pwgen_length", PROP_OCCURS = "pwgen_occurrs",
			DEFAULT_DIGITS = "0123456789", DEFAULT_LCASE = "abcdefghijklmnopqrstuvwxyz",
			DEFAULT_SPECIALS = "!#$%&'*+,-.:;<=>?@_~", DEFAULT_SIMILAR = "01IOl|";

	private static final int PWD_LEN_DEFAULT = 10, PWD_LEN_MIN = 5, PWD_LEN_MAX = 50, OCCURS_DEFAULT = 1,
			OCCURS_MIN = 1, OCCURS_MAX = 10;

	@FXML
	private BorderPane bPane;

	@FXML
	private Button btnGenerate;

	@FXML
	private CheckBox chkDigits;

	@FXML
	private CheckBox chkLowerCase;

	@FXML
	private CheckBox chkSimilar;

	@FXML
	private CheckBox chkSpecials;

	@FXML
	private CheckBox chkUpperCase;

	@FXML
	private Label lblLength;

	@FXML
	private Label lblOccurrence;

	@FXML
	private Spinner<Integer> spinLength;

	@FXML
	private Spinner<Integer> spinOccurrence;

	@FXML
	private TextArea txtAreaPwd;

	@FXML
	private MenuItem mItmDigits;

	@FXML
	private MenuItem mItmLCase;

	@FXML
	private MenuItem mItmSimilar;

	@FXML
	private MenuItem mItmSpecials;

	@FXML
	private MenuItem mItmUcase;

	@FXML
	private MenuButton menuCharClasses;

	private EncryptionToolBar encryptionToolBar;

	private void init() throws Exception {
		chkDigits.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_DIGITS, "true")));
		chkLowerCase.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_LCASE, "true")));
		chkSimilar.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_SIMILAR, "true")));
		chkSpecials.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_SPECIALS, "true")));
		chkUpperCase.setSelected(Boolean.parseBoolean(Main.properties.getProperty(PROP_UCASE, "true")));

		chkDigits.selectedProperty().addListener((observable, oldValue, newValue) -> onChanged(PROP_DIGITS, newValue));
		chkLowerCase.selectedProperty()
				.addListener((observable, oldValue, newValue) -> onChanged(PROP_LCASE, newValue));
		chkSimilar.selectedProperty()
				.addListener((observable, oldValue, newValue) -> onChanged(PROP_SIMILAR, newValue));
		chkSpecials.selectedProperty()
				.addListener((observable, oldValue, newValue) -> onChanged(PROP_SPECIALS, newValue));
		chkUpperCase.selectedProperty()
				.addListener((observable, oldValue, newValue) -> onChanged(PROP_UCASE, newValue));

		var pwdLen = Integer.parseInt(Main.properties.getProperty(PROP_PWD_LENGTH, "" + PWD_LEN_DEFAULT));
		spinLength
				.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(PWD_LEN_MIN, PWD_LEN_MAX, pwdLen));
		spinLength.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.properties.setProperty(PROP_PWD_LENGTH, Integer.toString(newValue));
			newPassword();
		});

		var occurs = Integer.parseInt(Main.properties.getProperty(PROP_OCCURS, "" + OCCURS_DEFAULT));
		spinOccurrence
				.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(OCCURS_MIN, OCCURS_MAX, occurs));
		spinOccurrence.valueProperty().addListener((observable, oldValue, newValue) -> {
			Main.properties.setProperty(PROP_OCCURS, Integer.toString(newValue));
			newPassword();
		});

		encryptionToolBar = new EncryptionToolBar();
		encryptionToolBar.init();
		bPane.setBottom(encryptionToolBar);

		newPassword();
	}

	private void onChanged(String propDigitsList, Boolean newValue) {
		Main.properties.setProperty(propDigitsList, Boolean.toString(newValue));
		newPassword();
	}

	private void newPassword() {
		List<String> charClasses = new ArrayList<>();

		var length = Integer.parseInt(Main.properties.getProperty(PROP_PWD_LENGTH, "" + PWD_LEN_DEFAULT));

		if (chkUpperCase.isSelected()) {
			charClasses.add(Main.properties.getProperty(PROP_UCASE_LIST, DEFAULT_LCASE.toUpperCase()));
		}
		if (chkLowerCase.isSelected()) {
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
			var blacklist = Main.properties.getProperty(PROP_SIMILAR_LIST, DEFAULT_SIMILAR).toCharArray();
			Arrays.sort(blacklist);

			for (int i = 0; i < size; i++) {
				// deactivating "similar" will remove all similar characters
				var sb = new StringBuilder();
				var cArr = charClasses.remove(0).toCharArray();
				for (var c : cArr) {
					if (Arrays.binarySearch(blacklist, c) < 0) {
						sb.append(c);
					}
				}
				charClasses.add(sb.toString());
			}
		}

		// remove duplicates
		for (int i = 0; i < size; i++) {
			var s = charClasses.remove(0);
			charClasses.add(s.replaceAll("(.)\\1+", "$1"));
		}

		// remove empty classes
		charClasses = charClasses.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

		if (charClasses.isEmpty()) {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error generating password");
			alert.setHeaderText("No character classes allowed");
			alert.setContentText("Check your settings");
			alert.showAndWait();

			return;
		}

		// ensure minimal occurrence
		List<String> list = new ArrayList<>();
		for (var i = 0; i < Integer.parseInt(Main.properties.getProperty(PROP_OCCURS, "" + OCCURS_DEFAULT)); i++) {
			for (String s : charClasses) {
				list.add(s);
			}
		}

		var rnd = new SecureRandom();

		while (list.size() < length) {
			list.add(charClasses.get(rnd.nextInt(charClasses.size())));
		}

		var sb = new StringBuilder();

		for (int i = 0; i < length; i++) {
			var s = list.remove(rnd.nextInt(list.size()));
			var cArr = s.toCharArray();
			sb.append(cArr[rnd.nextInt(cArr.length)]);
		}

		var unprotected = sb.toString();
		txtAreaPwd.setText(unprotected);
		encryptionToolBar.setUnprotected(unprotected.getBytes());
	}

	@FXML
	void onGenerate(ActionEvent event) {
		newPassword();
	}

	@FXML
	void onMenuItemAction(ActionEvent event) {
		var source = (MenuItem) event.getSource();
		String propertyName = null, defaultValue = null;

		if (source == mItmDigits) {
			propertyName = PROP_DIGITS_LIST;
			defaultValue = DEFAULT_DIGITS;
		} else if (source == mItmLCase) {
			propertyName = PROP_LCASE_LIST;
			defaultValue = DEFAULT_LCASE;
		} else if (source == mItmSimilar) {
			propertyName = PROP_SIMILAR_LIST;
			defaultValue = DEFAULT_SIMILAR;
		} else if (source == mItmSpecials) {
			propertyName = PROP_SPECIALS_LIST;
			defaultValue = DEFAULT_SPECIALS;
		} else if (source == mItmUcase) {
			propertyName = PROP_UCASE_LIST;
			defaultValue = DEFAULT_LCASE.toUpperCase();
		}

		assert propertyName != null;

		String s = propertyName;

		TextInputDialog dialog = new TextInputDialog(Main.properties.getProperty(propertyName, defaultValue));
		dialog.setTitle("omsCompanion");
		dialog.setHeaderText(String.format("Character Class %s:", source.getText()));
		dialog.showAndWait().filter(response -> response != null)
				.ifPresent(response -> Main.properties.setProperty(s, response));

	}

	public static void show() {
		ClipboardUtil.suspendClipboardCheck();

		Runnable r = () -> {
			ClipboardUtil.set("");
			ClipboardUtil.resumeClipboardCheck();
		};

		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/PasswordGenerator.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((PasswordGenerator) fxmlLoader.getController()).init();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME);
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					r.run();
				});
			} catch (Exception ex) {
				FxMain.handleException(ex);
				r.run();
			}
		});
	}
}
