package omscompanion.openjfx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
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
	private static final String TYPE_PROFILE = ".omsfs", NEW_PROFILE = "(new profile)",
			PLEASE_SELECT = "(please select)";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleObjectProperty<Path> sourceDir = new SimpleObjectProperty<>(),
			destinationDir = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<Profile> currentProfile = new SimpleObjectProperty<>();

	private static final int STATE_INITIAL = 0, STATE_READY_TO_ANALYZE = 1, STATE_ANALYZING = 2,
			STATE_READY_TO_SYNC = 3, STATE_SYNCING = 4, TOTAL_SIZE_UNKNOWN = -1;
	private final SimpleIntegerProperty state = new SimpleIntegerProperty(-1);
	private final ObservableList<SyncEntry> tableItems = FXCollections.observableArrayList();
	private final FilteredList<SyncEntry> filteredList = new FilteredList<>(tableItems);
	private final SimpleLongProperty totalSize = new SimpleLongProperty();

	private class SyncEntry {
		public final SimpleObjectProperty<Path> pathProperty = new SimpleObjectProperty<>();
		public final long length;

		public Exception accessException = null;

		public SyncEntry(Path path) {
			pathProperty.set(path);
			var _length = 0L;
			Exception ex = null;
			try {
				_length = Files.isDirectory(path) ? 0 : Files.size(path);
			} catch (IOException e) {
				ex = e;
			}

			length = _length;
			accessException = ex;
		}
	}

	public static record PathAndChecksum(String path, byte[] checksum) {
	};

	public static record PathLengthAndDate(String path, long length, long date) {
	};

	public static class ProfileSettings {
		public String sourceDir, destinationDir, publicKeyName;
		public String[] exclusionList, inclusionList;
		boolean checksum;

		public ProfileSettings(String sourceDir, String destinationDir, String[] exclusionList, String[] inclusionList,
				String publicKeyName, boolean checksum) {
			this.sourceDir = sourceDir;
			this.destinationDir = destinationDir;
			this.publicKeyName = publicKeyName;
			this.exclusionList = exclusionList;
			this.inclusionList = inclusionList;
			this.checksum = checksum;
		}
	}

	public static class Profile {
		public ProfileSettings settings;
		public PathAndChecksum[] pathAndChecksum;
		public PathLengthAndDate[] pathLengthAndDate;

		@JsonIgnore
		public SimpleObjectProperty<File> file = new SimpleObjectProperty<>();
		@JsonIgnore
		public Profile backup;

		public Profile(ProfileSettings settings, PathAndChecksum[] pathAndChecksum,
				PathLengthAndDate[] pathLengthAndDate) {
			this.settings = settings;
			this.pathAndChecksum = pathAndChecksum;
			this.pathLengthAndDate = pathLengthAndDate;
		}
	}

	private Profile newProfile() {
		var profile = new Profile(new ProfileSettings(null, null, new String[] {}, new String[] {},
				choice_public_key.getValue(), chk_checksum.isSelected()), new PathAndChecksum[] {},
				new PathLengthAndDate[] {});
		profile.file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));
		profile.backup = new Profile(new ProfileSettings(null, null, new String[] {}, new String[] {},
				choice_public_key.getValue(), chk_checksum.isSelected()), new PathAndChecksum[] {},
				new PathLengthAndDate[] {});
		return profile;
	}

	private AtomicBoolean loading = new AtomicBoolean();

	// icons by https://www.iconfinder.com/search?q=&iconset=heroicons-solid

	@FXML
	private Button btn_add_exclude;

	@FXML
	private Button btn_add_include;

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
	private TableColumn<SyncEntry, Path> col_filename;

	@FXML
	private TableColumn<SyncEntry, SyncEntry> col_flag_compare;

	@FXML
	private TableColumn<SyncEntry, Path> col_flag_folder;

	@FXML
	private TableColumn<SyncEntry, Path> col_level;

	@FXML
	private Label lbl_srcdir;

	@FXML
	private Label lbl_profile;

	@FXML
	private Label lbl_targetdir;

	@FXML
	private Label lbl_total;

	@FXML
	private ListView<String> list_exclude;

	@FXML
	private ListView<String> list_include;

	@FXML
	private TableView<SyncEntry> tbl_source;

	private void init(Scene scene) throws Exception {
		Platform.runLater(() -> {
			scene.getWindow().setOnCloseRequest(event -> {
				if (isProfileChanged() && isDirConfigValid()) {
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
		btn_analyze.setGraphic(getIcon("analyze"));
		btn_analyze.setTooltip(new Tooltip("Analyze"));

		btn_del_exclude.setText(null);
		btn_del_exclude.setGraphic(getIcon("trash_icon"));
		btn_del_exclude.setTooltip(new Tooltip("Delete Exclusion Rule"));

		btn_del_include.setText(null);
		btn_del_include.setGraphic(getIcon("trash_icon"));
		btn_del_include.setTooltip(new Tooltip("Delete Inclusion Rule"));

		btn_del_profile.setText(null);
		btn_del_profile.setGraphic(getIcon("trash_icon"));
		btn_del_profile.setTooltip(new Tooltip("Delete Profile"));

		btn_save.setText(null);
		btn_save.setGraphic(getIcon("save_icon"));
		btn_save.setTooltip(new Tooltip("Save"));

		btn_save_as.setText(null);
		btn_save_as.setGraphic(getIcon("save_as_icon"));
		btn_save_as.setTooltip(new Tooltip("Save As..."));

		btn_srcdir.setText(null);
		btn_srcdir.setTooltip(new Tooltip("Select Source Directory..."));
		btn_srcdir.setGraphic(getIcon("horizontal_dots"));

		btn_destdir.setText(null);
		btn_destdir.setTooltip(new Tooltip("Select Destination Directory..."));
		btn_destdir.setGraphic(getIcon("horizontal_dots"));

		btn_start_sync.setText(null);
		btn_start_sync.setTooltip(new Tooltip("Start Sync"));
		btn_start_sync.setGraphic(getIcon("logout_icon"));

		btn_stop.setText(null);
		btn_stop.setTooltip(new Tooltip("Stop Sync"));
		btn_stop.setGraphic(getIcon("x_icon"));

		btn_open.setText(null);
		btn_open.setTooltip(new Tooltip("Open Profile"));
		btn_open.setGraphic(getIcon("horizontal_dots"));

		btn_new.setText(null);
		btn_new.setTooltip(new Tooltip("New Profile"));
		btn_new.setGraphic(getIcon("plus_circle_icon"));

		btn_add_exclude.setGraphic(getIcon("plus_circle_icon"));
		btn_add_exclude.setText(null);

		btn_add_include.setGraphic(getIcon("plus_circle_icon"));
		btn_add_include.setText(null);

		EncryptionToolBar.initChoiceBox(choice_public_key);

		btn_del_exclude.setDisable(true);
		btn_del_include.setDisable(true);
		btn_save.setDisable(true);
		btn_save_as.setDisable(true);
		btn_analyze.setDisable(true);
		btn_start_sync.setDisable(true);
		btn_stop.setDisable(true);
		btn_add_exclude.setDisable(true);
		btn_add_include.setDisable(true);

		lbl_total.setGraphic(getIcon("database_icon"));
		lbl_total.setTooltip(new Tooltip("Total Data"));

		filteredList.predicateProperty().addListener((observable, oldValue, newValue) -> calculateTotalSize());

		setupTable();

		list_exclude.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_exclude.setDisable(list_exclude.getItems().isEmpty());
				if (!loading.get())
					currentProfile.get().settings.exclusionList = list_exclude.getItems().toArray(new String[] {});
				updateUI();
			}
		});

		totalSize.addListener((observable, oldValue, newValue) -> onTotalSizeChanged(newValue));
		totalSize.set(TOTAL_SIZE_UNKNOWN);

		list_exclude.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_exclude.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list_include.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_include.setDisable(list_include.getItems().isEmpty());
				if (!loading.get())
					currentProfile.get().settings.inclusionList = list_include.getItems().toArray(new String[] {});
				updateUI();
			}
		});

		list_include.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_include.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		state.addListener((observable, oldValue, newValue) -> updateUI());

		chk_checksum.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (!loading.get()) {
				currentProfile.get().settings.checksum = newValue;
				state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
			}
			updateUI();
		});

		sourceDir.addListener((observable, oldValue, newValue) -> {
			lbl_srcdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfile.get().settings.sourceDir = newValue.toFile().getAbsolutePath();

			list_exclude.getItems().clear();
			list_include.getItems().clear();

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_srcdir.setText(PLEASE_SELECT);

		destinationDir.addListener((observable, oldValue, newValue) -> {
			lbl_targetdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfile.get().settings.destinationDir = newValue.toFile().getAbsolutePath();

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_targetdir.setText(PLEASE_SELECT);

		currentProfile.addListener((observable, oldValue, newValue) -> loadProfile(newValue));

		lbl_profile.setText(NEW_PROFILE);

		currentProfile.set(newProfile());
	}

	private void calculateTotalSize() {
		totalSize
				.set(filteredList.stream().collect(Collectors.summarizingLong(syncEntry -> syncEntry.length)).getSum());
	}

	private void onTotalSizeChanged(Number newValue) {
		Platform.runLater(() -> {
			if (newValue.longValue() == TOTAL_SIZE_UNKNOWN) {
				lbl_total.setText("unknown");
			} else {
				var size = newValue.doubleValue();
				var uom = "B";

				if (size > 1024) {
					uom = "KB";
					size /= 1024D;
				}

				if (size > 1024) {
					uom = "MB";
					size /= 1024D;
				}

				if (size > 1024) {
					uom = "GB";
					size /= 1024D;
				}

				lbl_total.setText(String.format("%.3f %s", size, uom));
			}
		});
	}

	private void setupTable() {
		tbl_source.setItems(filteredList);
		tbl_source.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		col_filename.prefWidthProperty().bind(tbl_source.widthProperty().subtract(col_flag_compare.widthProperty())
				.subtract(col_flag_folder.widthProperty()).subtract(col_level.widthProperty()));
		col_filename.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_filename.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (empty) {
					setText(null);
					setTooltip(null);
				} else {
					setText(item.getFileName().toString());
					setTooltip(new Tooltip("..." + File.separatorChar + getSubpath(item)));
				}
			};
		});

		col_flag_folder.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_flag_folder.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (!empty && Files.isDirectory(item)) {
					this.setGraphic(getIcon("folder_icon"));
				} else {
					setGraphic(null);
				}
			};
		});

		col_flag_folder.setGraphic(getIcon("folder_icon"));

		col_level.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_level.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (empty) {
					setText(null);
				} else {
					setText(Integer.toString(item.getNameCount() - sourceDir.get().getNameCount()));
				}
			};
		});

		col_flag_compare.setCellFactory(col -> new TableCell<SyncEntry, SyncEntry>() {
			@Override
			protected void updateItem(SyncEntry item, boolean empty) {
				if (empty) {
					setGraphic(null);
					setTooltip(null);
				} else {
					if (item != null && item.accessException != null) {
						this.setGraphic(getIcon("lightning_bolt_icon"));
						this.setTooltip(new Tooltip(item.accessException.getMessage()));
					} else {
						setGraphic(null);
						setTooltip(null);
					}
				}
			}
		});

		var lvl_label = new Label();
		lvl_label.setGraphic(getIcon("down_double_icon"));
		lvl_label.setTooltip(new Tooltip("Drilldown Level"));

		col_level.setGraphic(lvl_label);

		tbl_source.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			btn_add_exclude.setDisable(newValue == null);
			btn_add_include.setDisable(newValue == null);
		});
	}

	private void updateUI() {
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

			btn_del_exclude.setDisable(list_exclude.getSelectionModel().getSelectedItems().isEmpty()
					|| state.get() != STATE_READY_TO_SYNC);

			btn_del_include.setDisable(list_include.getSelectionModel().getSelectedItems().isEmpty()
					|| state.get() != STATE_READY_TO_SYNC);

			if (Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_INITIAL }).contains(state.get())) {
				tableItems.clear();
				totalSize.set(TOTAL_SIZE_UNKNOWN);
			}
		});
	}

	protected boolean isProfileChanged() {
		try {
			String backup = objectMapper.writeValueAsString(currentProfile.get().backup.settings);
			String current = objectMapper.writeValueAsString(currentProfile.get().settings);

			return !backup.equals(current);
		} catch (JsonProcessingException ex) {
			ex.printStackTrace();
		}

		return false;
	}

	private ImageView getIcon(String iconName) {
		var image = new Image(getClass().getResourceAsStream("/omscompanion/img/" + iconName + ".png"));
		var imageView = new ImageView(image);
		imageView.setFitWidth(16);
		imageView.setFitHeight(16);

		return imageView;
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

		if (s.startsWith(d) || d.startsWith(s)) {
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
			sourceDir.set(profile.settings.sourceDir == null ? null : Path.of(profile.settings.sourceDir));
			destinationDir
					.set(profile.settings.destinationDir == null ? null : Path.of(profile.settings.destinationDir));
			list_exclude.getItems().setAll(profile.settings.exclusionList);
			list_include.getItems().setAll(profile.settings.inclusionList);
			chk_checksum.setSelected(profile.settings.checksum);
		} finally {
			loading.set(false);
		}
	}

	@FXML
	void actn_analyze(ActionEvent event) {
		state.set(STATE_ANALYZING);

		new Thread(() -> analyze()).start();
	}

	private void analyze() {
		try {
			Files.walk(sourceDir.get()).anyMatch(p -> analyzeSingle(p));
			calculateTotalSize();
			state.set(STATE_READY_TO_SYNC);
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

	private boolean analyzeSingle(Path p) {
		if (state.get() != STATE_ANALYZING)
			return true; // stop processing

		var level = p.getNameCount() - sourceDir.get().getNameCount();

		if (level == 0)
			return false; // parent folder

		var labelText = new Label(String.format("[%s] ...", level) + File.separatorChar + p.getFileName());
		labelText.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);

		tableItems.add(new SyncEntry(p));

		return false; // continue processing
	}

	@FXML
	void actn_del_exclude(ActionEvent event) {
		list_exclude.getItems().remove(list_exclude.getSelectionModel().getSelectedItem());
		filteredList.setPredicate(e -> matches(e));
	}

	@FXML
	void actn_del_include(ActionEvent event) {
		list_include.getItems().remove(list_include.getSelectionModel().getSelectedItem());
		filteredList.setPredicate(e -> matches(e));
	}

	@FXML
	void actn_del_profile(ActionEvent event) {
		var alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle(Main.APP_NAME);
		alert.setHeaderText("Delete Profile");
		alert.setContentText("Delete " + currentProfile.get().file.get().getName() + "?");
		alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
		alert.showAndWait().ifPresent(buttonType -> {
			if (buttonType == ButtonType.YES)
				try {
					Files.delete(currentProfile.get().file.get().toPath());
					currentProfile.set(newProfile());
				} catch (IOException e) {
					FxMain.handleException(e);
				}
		});
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
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setTitle("Save Profile As...");
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			fileChooser.getExtensionFilters().setAll(new ExtensionFilter("FileSync Profile", "*" + TYPE_PROFILE));
			var file = fileChooser.showSaveDialog(FxMain.getPrimaryStage());
			if (file == null) {
				return;
			}

			try {
				objectMapper.writeValue(file, currentProfile);
				currentProfile.get().file.set(file);
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
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

			destinationDir.set(result.toPath());
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

			sourceDir.set(result.toPath());
		});
	}

	@FXML
	void actn_start_sync(ActionEvent event) {
		state.set(STATE_SYNCING);

		new Thread(() -> {
			// as the files are encrypted, we can only compare against the data in the
			// profile.
			state.set(STATE_READY_TO_SYNC);
		}).start();

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
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			fileChooser.setTitle("Open Profile");
			fileChooser.getExtensionFilters().add(new ExtensionFilter("FileSync Profile", "*" + TYPE_PROFILE));

			var selectedFile = fileChooser.showOpenDialog(FxMain.getPrimaryStage());

			if (selectedFile == null)
				return;

			lbl_profile.setText(selectedFile.getAbsolutePath());
			try {
				var profile = objectMapper.readValue(selectedFile, Profile.class);
				profile.backup = objectMapper.readValue(selectedFile, Profile.class);
				profile.file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));

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

	@FXML
	private void actn_add_to_list(ActionEvent event) {
		var syncEntry = tbl_source.getSelectionModel().selectedItemProperty().get();

		var textInputDialog = new TextInputDialog(getSubpath(syncEntry.pathProperty.get()));

		textInputDialog.setTitle(Main.APP_NAME);
		textInputDialog.setHeaderText(String.format(
				"Create %s Rule. Asterix (*) and question mark (?) may be used as wildcard. INCLUDE rules will be processed first.",
				event.getSource() == btn_add_include ? "INCLUDE" : "EXCLUDE"));
		textInputDialog.setContentText("..." + File.separatorChar);

		var rule = textInputDialog.showAndWait();

		if (!rule.isPresent())
			return;

		if (event.getSource() == btn_add_exclude) {
			list_exclude.getItems().add(rule.get());
		} else {
			list_include.getItems().add(rule.get());
		}

		filteredList.setPredicate(e -> matches(e));
	}

	private String getSubpath(Path item) {
		return item.subpath(sourceDir.get().getNameCount(), item.getNameCount()).toString();
	}

	private boolean matches(SyncEntry syncEntry) {
		var subpath = getSubpath(syncEntry.pathProperty.get());

		if (!list_include.getItems().isEmpty()) {
			// check for include
			if (!list_include.getItems().stream().map(s -> toPattern(s)).anyMatch(s -> subpath.matches(s)))
				return false;
		}

		return !list_exclude.getItems().stream().map(s -> toPattern(s)).anyMatch(s -> subpath.matches(s));
	}

	private String toPattern(String s) {
		var pat = Pattern.compile("([^\\*\\?]*)([\\*\\?])?");
		var m = pat.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			if (m.group(1) != null)
				sb.append(Pattern.quote(m.group(1)));

			if (m.group(2) != null)
				switch (m.group(2)) {
				case "*" -> sb.append(".*");
				case "?" -> sb.append(".");
				}
		}
		return sb.toString();
	}
}
