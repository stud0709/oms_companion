package omscompanion.openjfx;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;

public class FileSync {
	private static final String TYPE_PROFILE = ".profile", NEW_PROFILE = "(new profile)",
			PLEASE_SELECT = "(please select)";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleObjectProperty<File> sourceDir = new SimpleObjectProperty<>(),
			destinationDir = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<Profile> currentProfile = new SimpleObjectProperty<>();

	private static final int STATE_INITIAL = 0, STATE_READY_TO_ANALYZE = 1, STATE_ANALYZING = 2,
			STATE_READY_TO_SYNC = 3, STATE_SYNCING = 4;
	private final SimpleIntegerProperty state = new SimpleIntegerProperty(-1);

	public class Profile {
		public String sourceDir, destinationDir, publicKeyName;
		public String[] exclusionList, inclusionList;
		boolean checksum;
		@JsonIgnore
		public SimpleObjectProperty<File> file = new SimpleObjectProperty<>();
		@JsonIgnore
		public Profile backup;

		public Profile(String sourceDir, String destinationDir, String[] exclusionList, String[] inclusionList,
				String publicKeyName, boolean checksum) {
			file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));
			this.sourceDir = sourceDir;
			this.destinationDir = destinationDir;
			this.exclusionList = exclusionList;
			this.inclusionList = inclusionList;
			this.publicKeyName = publicKeyName;
			this.checksum = checksum;
		}
	}

	private Profile newProfile() {
		var profile = new Profile(null, null, new String[] {}, new String[] {}, choice_public_key.getValue(),
				chk_checksum.isSelected());
		profile.backup = new Profile(null, null, new String[] {}, new String[] {}, choice_public_key.getValue(),
				chk_checksum.isSelected());
		return profile;
	}

	private AtomicBoolean loading = new AtomicBoolean();

	// icons by https://www.iconfinder.com/search?q=&iconset=heroicons-solid

	@FXML
	private Button btn_analyze;

	@FXML
	private Button btn_del_exclude;

	@FXML
	private Button btn_del_include;

	@FXML
	private Button btn_del_profile;

	@FXML
	private Button btn_destdir;

	@FXML
	private Button btn_new;

	@FXML
	private Button btn_open;

	@FXML
	private Button btn_save;

	@FXML
	private Button btn_save_as;

	@FXML
	private Button btn_srcdir;

	@FXML
	private Button btn_start_sync;

	@FXML
	private Button btn_stop;

	@FXML
	private CheckBox chk_checksum;

	@FXML
	private ChoiceBox<String> choice_public_key;

	@FXML
	private Label lbl_srcdir;

	@FXML
	private Label lbl_profile;

	@FXML
	private Label lbl_targetdir;

	@FXML
	private ListView<String> list_exclude;

	@FXML
	private ListView<String> list_include;

	@FXML
	private ListView<String> list_source;

	@FXML
	private ProgressIndicator progress_sync;

	private void init(Scene scene) throws Exception {
		Platform.runLater(() -> {
			scene.getWindow().setOnCloseRequest(event -> {
				if (isProfileChanged()) {
					var alert = new Alert(AlertType.WARNING);
					alert.setTitle(Main.APP_NAME);
					alert.setHeaderText("Unsaved Changes Detected");
					alert.setContentText("Close FileSync without saving changes?");
					alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
					var result = alert.showAndWait();
					result.ifPresent(bt -> {
						if (bt == ButtonType.NO) // do not close window
							event.consume();
					});
				}
			});
		});

		btn_analyze.setText(null);
		setIcon("analyze", btn_analyze);
		btn_analyze.setTooltip(new Tooltip("Analyze"));

		btn_del_exclude.setText(null);
		setIcon("trash_icon", btn_del_exclude);
		btn_del_exclude.setTooltip(new Tooltip("Delete Exclusion Rule"));

		btn_del_include.setText(null);
		setIcon("trash_icon", btn_del_include);
		btn_del_include.setTooltip(new Tooltip("Delete Inclusion Rule"));

		btn_del_profile.setText(null);
		setIcon("trash_icon", btn_del_profile);
		btn_del_profile.setTooltip(new Tooltip("Delete Profile"));

		btn_save.setText(null);
		setIcon("save_icon", btn_save);
		btn_save.setTooltip(new Tooltip("Save"));

		btn_save_as.setText(null);
		setIcon("save_as_icon", btn_save_as);
		btn_save_as.setTooltip(new Tooltip("Save As..."));

		btn_srcdir.setText(null);
		btn_srcdir.setTooltip(new Tooltip("Select Source Directory..."));
		setIcon("horizontal_dots", btn_srcdir);

		btn_destdir.setText(null);
		btn_destdir.setTooltip(new Tooltip("Select Destination Directory..."));
		setIcon("horizontal_dots", btn_destdir);

		btn_start_sync.setText(null);
		btn_start_sync.setTooltip(new Tooltip("Start Sync"));
		setIcon("logout_icon", btn_start_sync);

		btn_stop.setText(null);
		btn_stop.setTooltip(new Tooltip("Stop Sync"));
		setIcon("x_icon", btn_stop);

		btn_open.setText(null);
		btn_open.setTooltip(new Tooltip("Open Profile"));
		setIcon("horizontal_dots", btn_open);

		btn_new.setText(null);
		btn_new.setTooltip(new Tooltip("New Profile"));
		setIcon("plus_circle_icon", btn_new);

		EncryptionToolBar.initChoiceBox(choice_public_key);

		btn_del_exclude.setDisable(true);
		btn_del_include.setDisable(true);
		btn_save.setDisable(true);
		btn_save_as.setDisable(true);
		btn_analyze.setDisable(true);
		btn_start_sync.setDisable(true);
		btn_stop.setDisable(true);

		list_exclude.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_exclude.setDisable(list_exclude.getItems().isEmpty());
				if (!loading.get())
					currentProfile.get().exclusionList = list_exclude.getItems().toArray(new String[] {});
				updateUI(state.get());
			}
		});

		list_exclude.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> {
					btn_del_exclude.setDisable(list_exclude.getSelectionModel().getSelectedItems().isEmpty());
				});

		list_exclude.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list_include.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_include.setDisable(list_include.getItems().isEmpty());
				if (!loading.get())
					currentProfile.get().inclusionList = list_include.getItems().toArray(new String[] {});
				updateUI(state.get());
			}
		});

		list_include.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> {
					btn_del_include.setDisable(list_include.getSelectionModel().getSelectedItems().isEmpty());
				});

		list_include.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		state.addListener((observable, oldValue, newValue) -> updateUI(newValue));

		chk_checksum.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (!loading.get())
				currentProfile.get().checksum = newValue;
			updateUI(state.get());
		});

		sourceDir.addListener((observable, oldValue, newValue) -> {
			lbl_srcdir.setText(newValue == null ? PLEASE_SELECT : newValue.getAbsolutePath().toString());
			if (!loading.get())
				currentProfile.get().sourceDir = newValue.getAbsolutePath().toString();

			list_exclude.getItems().clear();
			list_include.getItems().clear();
			list_source.getItems().clear();

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_srcdir.setText(PLEASE_SELECT);

		destinationDir.addListener((observable, oldValue, newValue) -> {
			lbl_targetdir.setText(newValue == null ? PLEASE_SELECT : newValue.getAbsolutePath().toString());
			if (!loading.get())
				currentProfile.get().destinationDir = newValue.getAbsolutePath().toString();

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_targetdir.setText(PLEASE_SELECT);

		currentProfile.addListener((observable, oldValue, newValue) -> loadProfile(newValue));

		lbl_profile.setText(NEW_PROFILE);

		currentProfile.set(newProfile());
	}

	private void updateUI(Number newValue) {
		Platform.runLater(() -> {
			btn_start_sync
					.setDisable(state.get() != STATE_READY_TO_SYNC || choice_public_key.getSelectionModel().isEmpty());

			btn_stop.setDisable(!Arrays.asList(new Integer[] { STATE_ANALYZING, STATE_SYNCING }).contains(state.get()));

			for (var node : new Node[] { chk_checksum, btn_destdir, btn_srcdir, btn_open, choice_public_key,
					btn_del_include, btn_del_exclude, btn_new }) {
				node.setDisable(Arrays.asList(new Integer[] { STATE_ANALYZING, STATE_SYNCING }).contains(state.get()));
			}

			btn_del_profile
					.setDisable(Arrays.asList(new Integer[] { STATE_ANALYZING, STATE_SYNCING }).contains(state.get())
							|| currentProfile.get().file.get() == null);

			btn_analyze.setDisable(!Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_READY_TO_SYNC })
					.contains(state.get()));

			btn_save.setDisable(
					!Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_READY_TO_SYNC }).contains(state.get())
							|| currentProfile.get().file.get() == null || !isProfileChanged());

			btn_save_as.setDisable(!Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_READY_TO_SYNC })
					.contains(state.get()));
		});
	}

	protected boolean isProfileChanged() {
		try {
			String backup = objectMapper.writeValueAsString(currentProfile.get().backup);
			String current = objectMapper.writeValueAsString(currentProfile);

			return !backup.equals(current);
		} catch (JsonProcessingException ex) {
			ex.printStackTrace();
		}

		return false;
	}

	private void setIcon(String iconName, Labeled control) {
		var image = new Image(getClass().getResourceAsStream("/omscompanion/img/" + iconName + ".png"));
		var imageView = new ImageView(image);
		imageView.setFitWidth(16);
		imageView.setFitHeight(16);

		control.setGraphic(imageView);
	}

	public static void show(Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/FileSync.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((FileSync) fxmlLoader.getController()).init(scene);
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": FileSync");
				stage.setScene(scene);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					if (andThen != null)
						andThen.run();
				});
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

	private boolean isDirConfigValid() {
		var s = sourceDir.get();
		var d = destinationDir.get();

		if (s == null || d == null)
			return false;

		if (s.equals(d)) {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("Invalid Directories");
			alert.setContentText("Source and Destination directories may not be the same");
			alert.showAndWait();
			return false;
		}

		if (s.toPath().startsWith(d.toPath()) || d.toPath().startsWith(s.toPath())) {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("Invalid Directories");
			alert.setContentText("Source and Destination directories may not subfolders of each other");
			alert.showAndWait();
			return false;
		}

		return true;
	}

	/**
	 * Load profile settings into UI
	 * 
	 * @param profile
	 */
	private void loadProfile(Profile profile) {
		loading.set(true);

		try {
			onProfileFileChanged(profile.file.get());
			lbl_profile.setText(
					profile.file.get() == null ? NEW_PROFILE : profile.file.get().getAbsolutePath().toString());
			sourceDir.set(profile.sourceDir == null ? null : new File(profile.sourceDir));
			destinationDir.set(profile.destinationDir == null ? null : new File(profile.destinationDir));
			list_exclude.getItems().setAll(profile.exclusionList);
			list_include.getItems().setAll(profile.inclusionList);
			chk_checksum.setSelected(profile.checksum);
		} finally {
			loading.set(false);
		}
	}

	@FXML
	void actn_analyze(ActionEvent event) {
		state.set(STATE_ANALYZING);

		// ...

		state.set(STATE_READY_TO_SYNC);
	}

	@FXML
	void actn_del_exclude(ActionEvent event) {

	}

	@FXML
	void actn_del_include(ActionEvent event) {

	}

	@FXML
	void actn_del_profile(ActionEvent event) {

	}

	@FXML
	void actn_save(ActionEvent event) {
		try {
			objectMapper.writeValue(currentProfile.get().file.get(), currentProfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	void actn_save_as(ActionEvent event) {

	}

	@FXML
	void actn_select_dest(ActionEvent event) {
		Platform.runLater(() -> {
			var dirChooser = new DirectoryChooser();
			dirChooser.setTitle("FileSync Destination Directory");
			dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			var result = dirChooser.showDialog(FxMain.getPrimaryStage());
			if (result == null) {
				return;
			}

			destinationDir.set(result);
		});
	}

	@FXML
	void actn_select_src(ActionEvent event) {
		Platform.runLater(() -> {
			var dirChooser = new DirectoryChooser();
			dirChooser.setTitle("FileSync Source Directory");
			dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			var result = dirChooser.showDialog(FxMain.getPrimaryStage());
			if (result == null) {
				return;
			}

			sourceDir.set(result);
		});
	}

	@FXML
	void actn_start_sync(ActionEvent event) {
		state.set(STATE_SYNCING);

		// ...

		state.set(STATE_READY_TO_SYNC);
	}

	@FXML
	void actn_stop(ActionEvent event) {
		switch (state.get()) {
		case STATE_ANALYZING -> state.set(STATE_READY_TO_ANALYZE);
		case STATE_SYNCING -> state.set(STATE_READY_TO_SYNC);
		}
	}

	@FXML
	void actn_btn_new(ActionEvent event) {
		if (isProfileChanged()) {
			var alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("New Profile");
			alert.setContentText("Discard current changes" + (currentProfile.get().file.get() == null ? ""
					: " of " + currentProfile.get().file.get().getName()) + "?");

			alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
			alert.showAndWait().ifPresent(buttonType -> {
				if (buttonType == ButtonType.YES)
					currentProfile.set(newProfile());
			});
			return;
		}

		currentProfile.set(newProfile());
	}

	@FXML
	void actn_open_profile(ActionEvent event) throws StreamReadException, DatabindException, IOException {
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setInitialDirectory(Main.FILESYNC.toFile());
			fileChooser.setTitle("Open Profile");
			fileChooser.getExtensionFilters().add(new ExtensionFilter("FileSync Profile", "*" + TYPE_PROFILE));

			var selectedFile = fileChooser.showOpenDialog(FxMain.getPrimaryStage());

			if (selectedFile == null)
				return;

			lbl_profile.setText(selectedFile.getAbsolutePath());
			try {
				var profile = objectMapper.readValue(selectedFile, Profile.class);
				profile.backup = objectMapper.readValue(selectedFile, Profile.class);
				profile.file.set(selectedFile);
				currentProfile.set(profile);
			} catch (Exception ex) {
				currentProfile.set(null);
				FxMain.handleException(ex);
			}
		});
	}

	private void onProfileFileChanged(File f) {
		btn_del_profile.setDisable(f == null);
	}
}
