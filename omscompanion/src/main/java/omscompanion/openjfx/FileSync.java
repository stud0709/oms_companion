package omscompanion.openjfx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import javafx.geometry.Dimension2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedFile;
import omscompanion.crypto.RSAUtils;

public class FileSync {
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
	private ChoiceBox<RSAPublicKeyItem> choice_public_key;

	@FXML
	private TableColumn<SyncEntry, Path> col_filename;

	@FXML
	private TableColumn<SyncEntry, CompareResult> col_sync_result;

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
	private Label lbl_deletions;

	@FXML
	private Label lbl_matches;

	@FXML
	private Label lbl_mirrors;

	@FXML
	private Label lbl_total_files;

	@FXML
	private Label lbl_info;

	@FXML
	private ToggleButton toggle_errors;

	@FXML
	private ListView<String> list_exclude;

	@FXML
	private ListView<String> list_include;

	@FXML
	private TableView<SyncEntry> tbl_source;

	private static final String TYPE_PROFILE = ".omsfs", NEW_PROFILE = "(new profile)",
			PLEASE_SELECT = "(please select)";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleObjectProperty<Path> sourceDir = new SimpleObjectProperty<>(),
			destinationDir = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<Profile> currentProfileProperty = new SimpleObjectProperty<>();

	private enum State {
		INITIAL, READY_TO_ANALYZE, ANALYZING, READY_TO_SYNC, SYNCING, DONE_SYNCING;
	}

	private enum Result {
		ERROR, MATCH, MIRRORED;
	}

	private final SimpleObjectProperty<State> stateProperty = new SimpleObjectProperty<>(State.INITIAL);
	private final ObservableList<SyncEntry> tableItems = FXCollections.observableArrayList();
	private final FilteredList<SyncEntry> filteredList = new FilteredList<>(tableItems);
	private final SimpleLongProperty totalSizeProperty = new SimpleLongProperty(Long.MIN_VALUE);
	private final SimpleIntegerProperty matchesProperty = new SimpleIntegerProperty(),
			errorsProperty = new SimpleIntegerProperty(), mirrorsProperty = new SimpleIntegerProperty(),
			deletionsProperty = new SimpleIntegerProperty(), totalFilesProperty = new SimpleIntegerProperty();

	private class SyncEntry {
		public final SimpleObjectProperty<Path> sourcePathProperty = new SimpleObjectProperty<>();
		public final long length;
		public final SimpleObjectProperty<CompareResult> compareProperty = new SimpleObjectProperty<>();
		public final SimpleObjectProperty<Path> keyPathProperty = new SimpleObjectProperty<>();

		public SyncEntry(Path sourcePath) {
			sourcePathProperty.set(sourcePath);
			var _length = 0L;
			try {
				_length = Files.isDirectory(sourcePath) ? 0 : Files.size(sourcePath);
			} catch (IOException e) {
				e.printStackTrace();
				compareProperty.set(new CompareResult(e));
			}

			length = _length;
			keyPathProperty.set(sourcePath.subpath(sourceDir.get().getNameCount(), sourcePath.getNameCount()));

			compareProperty.addListener((observable, oldValue, newValue) -> {
				if (Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(stateProperty.get()))
					synchronized (FileSync.this) {
						switch (newValue.result) {
						case MIRRORED -> mirrorsProperty.set(mirrorsProperty.get() + 1);
						case ERROR -> errorsProperty.set(errorsProperty.get() + 1);
						case MATCH -> matchesProperty.set(matchesProperty.get() + 1);
						}
					}
			});
		}
	}

	public static record CompareResult(Result result, Exception exception) {
		public CompareResult(Exception e) {
			this(Result.ERROR, e);
		}

		public CompareResult(Result compare) {
			this(compare, null);
		}
	};

	private AtomicBoolean loading = new AtomicBoolean(true);

	private Profile newProfile() {
		var profile = new Profile(
				new ProfileSettings(null, null, new String[] {}, new String[] {},
						choice_public_key.getValue() == null ? null : choice_public_key.getValue().toString()),
				new PathAndChecksum[] {});
		profile.backup = new Profile(
				new ProfileSettings(null, null, new String[] {}, new String[] {},
						choice_public_key.getValue() == null ? null : choice_public_key.getValue().toString()),
				new PathAndChecksum[] {});
		return profile;
	}

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
		btn_analyze.setGraphic(getImageView("analyze"));
		btn_analyze.setTooltip(new Tooltip("Analyze"));

		btn_del_exclude.setText(null);
		btn_del_exclude.setGraphic(getImageView("trash_icon"));
		btn_del_exclude.setTooltip(new Tooltip("Delete Exclusion Rule"));

		btn_del_include.setText(null);
		btn_del_include.setGraphic(getImageView("trash_icon"));
		btn_del_include.setTooltip(new Tooltip("Delete Inclusion Rule"));

		btn_del_profile.setText(null);
		btn_del_profile.setGraphic(getImageView("trash_icon"));
		btn_del_profile.setTooltip(new Tooltip("Delete Profile"));

		btn_save.setText(null);
		btn_save.setGraphic(getImageView("save_icon"));
		btn_save.setTooltip(new Tooltip("Save"));

		btn_save_as.setText(null);
		btn_save_as.setGraphic(getImageView("save_as_icon"));
		btn_save_as.setTooltip(new Tooltip("Save As..."));

		btn_srcdir.setText(null);
		btn_srcdir.setTooltip(new Tooltip("Select Source Directory..."));
		btn_srcdir.setGraphic(getImageView("horizontal_dots"));

		btn_destdir.setText(null);
		btn_destdir.setTooltip(new Tooltip("Select Destination Directory..."));
		btn_destdir.setGraphic(getImageView("horizontal_dots"));

		btn_start_sync.setText(null);
		btn_start_sync.setTooltip(new Tooltip("Start Sync"));
		btn_start_sync.setGraphic(getImageView("logout_icon"));

		btn_stop.setText(null);
		btn_stop.setTooltip(new Tooltip("Stop Sync"));
		btn_stop.setGraphic(getImageView("x_icon"));

		btn_open.setText(null);
		btn_open.setTooltip(new Tooltip("Open Profile"));
		btn_open.setGraphic(getImageView("horizontal_dots"));

		btn_new.setText(null);
		btn_new.setTooltip(new Tooltip("New Profile"));
		btn_new.setGraphic(getImageView("plus_circle_icon"));

		btn_add_exclude.setGraphic(getImageView("plus_circle_icon"));
		btn_add_exclude.setText(null);

		btn_add_include.setGraphic(getImageView("plus_circle_icon"));
		btn_add_include.setText(null);

		choice_public_key.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if (loading.get())
				return;
			currentProfileProperty.get().settings.publicKeyName = newValue.toString();
		});

		FxMain.initChoiceBox(choice_public_key);

		btn_del_exclude.setDisable(true);
		btn_del_include.setDisable(true);
		btn_save.setDisable(true);
		btn_save_as.setDisable(true);
		btn_analyze.setDisable(true);
		btn_start_sync.setDisable(true);
		btn_stop.setDisable(true);
		btn_add_exclude.setDisable(true);
		btn_add_include.setDisable(true);

		lbl_total.setGraphic(getImageView("database_icon"));
		lbl_total.setTooltip(new Tooltip("Total Data"));

		toggle_errors.setGraphic(getImageView("lightning_bolt_icon"));
		toggle_errors.setTooltip(new Tooltip("Display errors only"));
		toggle_errors.setText("0");
		toggle_errors.selectedProperty()
				.addListener((observable, oldValue, newValue) -> filteredList.setPredicate(e -> matches(e)));

		lbl_deletions.setGraphic(getImageView("trash_icon"));
		lbl_deletions.setTooltip(new Tooltip("Don't exist in the source folder any more; deleted"));
		lbl_deletions.setText("0");

		lbl_mirrors.setGraphic(getImageView("logout_icon"));
		lbl_mirrors.setTooltip(new Tooltip("Mirrored"));
		lbl_mirrors.setText("0");

		lbl_matches.setGraphic(getImageView("check_icon"));
		lbl_matches.setTooltip(new Tooltip("Unchanged"));
		lbl_matches.setText("0");

		lbl_total_files.setGraphic(getImageView("document_icon"));
		lbl_total_files.setTooltip(new Tooltip("Total Files"));
		lbl_total_files.setText("0");

		lbl_info.setGraphic(getImageView("circle_information_icon"));
		lbl_info.setText(null);

		filteredList.predicateProperty().addListener((observable, oldValue, newValue) -> calculateTotals());

		setupTable();

		list_exclude.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_exclude.setDisable(list_exclude.getItems().isEmpty());
				if (!loading.get())
					currentProfileProperty.get().settings.exclusionList = list_exclude.getItems()
							.toArray(new String[] {});
				updateUI();
			}
		});

		totalSizeProperty.addListener((observable, oldValue, newValue) -> onTotalSizeChanged(newValue));
		totalSizeProperty.set(0);

		list_exclude.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_exclude.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list_include.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_include.setDisable(list_include.getItems().isEmpty());
				if (!loading.get())
					currentProfileProperty.get().settings.inclusionList = list_include.getItems()
							.toArray(new String[] {});
				updateUI();
			}
		});

		list_include.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_include.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		stateProperty.addListener((observable, oldValue, newValue) -> updateUI());

		sourceDir.addListener((observable, oldValue, newValue) -> {
			lbl_srcdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfileProperty.get().settings.sourceDir = newValue.toFile().getAbsolutePath();

			list_exclude.getItems().clear();
			list_include.getItems().clear();

			stateProperty.set(isDirConfigValid() ? State.READY_TO_ANALYZE : State.INITIAL);
		});

		lbl_srcdir.setText(PLEASE_SELECT);

		destinationDir.addListener((observable, oldValue, newValue) -> {
			lbl_targetdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfileProperty.get().settings.destinationDir = newValue.toFile().getAbsolutePath();

			stateProperty.set(isDirConfigValid() ? State.READY_TO_ANALYZE : State.INITIAL);
		});

		lbl_targetdir.setText(PLEASE_SELECT);

		currentProfileProperty.addListener((observable, oldValue, newValue) -> loadProfile(newValue));

		lbl_profile.setText(NEW_PROFILE);

		currentProfileProperty.set(newProfile());

		errorsProperty.addListener((observable, oldValue, newValue) -> Platform
				.runLater(() -> toggle_errors.setText(NumberFormat.getNumberInstance().format(newValue))));
		matchesProperty.addListener((observable, oldValue, newValue) -> Platform
				.runLater(() -> lbl_matches.setText(NumberFormat.getNumberInstance().format(newValue))));
		deletionsProperty.addListener((observable, oldValue, newValue) -> Platform
				.runLater(() -> lbl_deletions.setText(NumberFormat.getNumberInstance().format(newValue))));
		mirrorsProperty.addListener((observable, oldValue, newValue) -> Platform
				.runLater(() -> lbl_mirrors.setText(NumberFormat.getNumberInstance().format(newValue))));
		totalFilesProperty.addListener((observable, oldValue, newValue) -> Platform
				.runLater(() -> lbl_total_files.setText(NumberFormat.getNumberInstance().format(newValue))));

		info("Please select a profile or public key, source, and destination directories");
	}

	private void calculateTotals() {
		totalSizeProperty
				.set(filteredList.stream().collect(Collectors.summarizingLong(syncEntry -> syncEntry.length)).getSum());
		totalFilesProperty.set((int) filteredList.stream()
				.filter(syncEntry -> !Files.isDirectory(syncEntry.sourcePathProperty.get())).count());
		errorsProperty.set((int) filteredList.stream().filter(syncEntry -> syncEntry.compareProperty.get() != null
				&& syncEntry.compareProperty.get().result == Result.ERROR).count());
		matchesProperty.set((int) filteredList.stream().filter(syncEntry -> syncEntry.compareProperty.get() != null
				&& syncEntry.compareProperty.get().result == Result.MATCH).count());
		mirrorsProperty.set((int) filteredList.stream().filter(syncEntry -> syncEntry.compareProperty.get() != null
				&& syncEntry.compareProperty.get().result == Result.MIRRORED).count());
	}

	private void onTotalSizeChanged(Number newValue) {
		Platform.runLater(() -> {
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
		});
	}

	private void setupTable() {
		tbl_source.setItems(filteredList);
		tbl_source.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		col_filename.prefWidthProperty().bind(tbl_source.widthProperty().subtract(col_sync_result.widthProperty())
				.subtract(col_flag_folder.widthProperty()).subtract(col_level.widthProperty()));
		col_filename.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().keyPathProperty);
		col_filename.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (empty) {
					setText(null);
					setTooltip(null);
				} else {
					setText(item.getFileName().toString());
					setTooltip(new Tooltip("..." + File.separatorChar + item.toString()));
				}
			};
		});

		col_flag_folder.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().sourcePathProperty);
		col_flag_folder.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (!empty && Files.isDirectory(item)) {
					this.setGraphic(getImageView("folder_icon"));
				} else {
					setGraphic(null);
				}
			};
		});

		col_flag_folder.setGraphic(getImageView("folder_icon"));

		var lvl_label = new Label();
		lvl_label.setGraphic(getImageView("down_double_icon"));
		lvl_label.setTooltip(new Tooltip("Drilldown Level"));

		col_level.setGraphic(lvl_label);
		col_level.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().sourcePathProperty);
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

		var result_label = new Label();
		result_label.setGraphic(getImageView("view_grid_icon"));
		result_label.setTooltip(new Tooltip("Sync Result"));

		col_sync_result.setGraphic(result_label);
		col_sync_result.setCellValueFactory(param -> param.getValue().compareProperty);
		col_sync_result.setCellFactory(col -> new TableCell<SyncEntry, CompareResult>() {
			@Override
			protected void updateItem(CompareResult compareResult, boolean empty) {
				if (empty) {
					setGraphic(null);
					setTooltip(null);
				} else {
					if (compareResult != null) {
						switch (compareResult.result) {
						case ERROR -> {
							this.setGraphic(getImageView("lightning_bolt_icon"));
							var ex = compareResult.exception;
							this.setTooltip(
									new Tooltip(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()));
						}
						case MIRRORED -> {
							this.setGraphic(getImageView("logout_icon"));
							setTooltip(new Tooltip("Mirrored to the destination folder"));
						}
						case MATCH -> {
							this.setGraphic(getImageView("check_icon"));
							setTooltip(new Tooltip("Unchanged"));
						}
						}
					} else {
						setGraphic(null);
						setTooltip(null);
					}
				}
			}
		});

		tbl_source.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> updateUI());
	}

	private void updateUI() {
		var state = stateProperty.get();

		if (Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.INITIAL, State.ANALYZING }).contains(state)) {
			tableItems.clear();
			totalSizeProperty.set(0);
			totalFilesProperty.set(0);
			errorsProperty.set(0);
			mirrorsProperty.set(0);
			matchesProperty.set(0);
			deletionsProperty.set(0);
		}

		Platform.runLater(() -> {

			btn_start_sync.setDisable(
					state != State.READY_TO_SYNC || currentProfileProperty.get().settings.publicKeyName == null);

			btn_stop.setDisable(!Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(state));

			for (var node : new Node[] { btn_destdir, btn_srcdir, btn_open, choice_public_key, btn_del_include,
					btn_del_exclude, btn_new, toggle_errors }) {
				node.setDisable(Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(state));
			}

			btn_del_profile.setDisable(Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(state)
					|| currentProfileProperty.get().file.get() == null);

			btn_analyze.setDisable(
					!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC, State.DONE_SYNCING })
							.contains(state));

			btn_save.setDisable(
					!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC, State.DONE_SYNCING })
							.contains(state) || currentProfileProperty.get().file.get() == null || !isProfileChanged());

			btn_save_as.setDisable(
					!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC, State.DONE_SYNCING })
							.contains(state));

			btn_add_exclude.setDisable(tbl_source.getSelectionModel().isEmpty()
					|| !Arrays.asList(new State[] { State.READY_TO_SYNC, State.DONE_SYNCING }).contains(state));

			btn_add_include.setDisable(tbl_source.getSelectionModel().isEmpty()
					|| !Arrays.asList(new State[] { State.READY_TO_SYNC, State.DONE_SYNCING }).contains(state));

			btn_del_exclude.setDisable(list_exclude.getSelectionModel().getSelectedItems().isEmpty()
					|| !Arrays.asList(new State[] { State.READY_TO_SYNC, State.DONE_SYNCING }).contains(state));

			btn_del_include.setDisable(list_include.getSelectionModel().getSelectedItems().isEmpty()
					|| !Arrays.asList(new State[] { State.READY_TO_SYNC, State.DONE_SYNCING }).contains(state));

			toggle_errors.setSelected(false);
			toggle_errors.setDisable(
					!Arrays.asList(new State[] { State.READY_TO_SYNC, State.DONE_SYNCING }).contains(state));
		});
	}

	protected boolean isProfileChanged() {
		try {
			String backup = objectMapper.writeValueAsString(currentProfileProperty.get().backup.settings);
			String current = objectMapper.writeValueAsString(currentProfileProperty.get().settings);

			return !backup.equals(current);
		} catch (JsonProcessingException ex) {
			ex.printStackTrace();
		}

		return false;
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
				stage.getIcons().add(FxMain.getImage("qr-code"));
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
			profile.file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));
			onProfileFileChanged(profile.file.get());
			lbl_profile.setText(
					profile.file.get() == null ? NEW_PROFILE : profile.file.get().getAbsolutePath().toString());
			sourceDir.set(profile.settings.sourceDir == null ? null : Path.of(profile.settings.sourceDir));
			destinationDir
					.set(profile.settings.destinationDir == null ? null : Path.of(profile.settings.destinationDir));
			list_exclude.getItems().setAll(profile.settings.exclusionList);
			list_include.getItems().setAll(profile.settings.inclusionList);
			filteredList.setPredicate(e -> matches(e));
		} finally {
			loading.set(false);
		}
	}

	@FXML
	void actn_analyze(ActionEvent event) {
		info("Analyzing source directory, please wait...");
		stateProperty.set(State.ANALYZING);

		new Thread(() -> {
			try {
				Files.walk(sourceDir.get()).anyMatch(p -> analyzeSingle(p));
				calculateTotals();
				if (stateProperty.get() != State.ANALYZING)// has been cancelled
					return;
				stateProperty.set(State.READY_TO_SYNC);
				info("Finished analyzing!");
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		}).start();
	}

	private boolean analyzeSingle(Path p) {
		if (stateProperty.get() != State.ANALYZING)
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
		alert.setContentText("Delete " + currentProfileProperty.get().file.get().getName() + "?");
		alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
		alert.showAndWait().ifPresent(buttonType -> {
			if (buttonType == ButtonType.YES)
				try {
					Files.delete(currentProfileProperty.get().file.get().toPath());
					currentProfileProperty.set(newProfile());
				} catch (IOException e) {
					FxMain.handleException(e);
				}
		});
	}

	@FXML
	void actn_save(ActionEvent event) {
		if (!currentProfileProperty.get().backup.settings.publicKeyName
				.equals(currentProfileProperty.get().settings.publicKeyName)) {
			// public key has changed, revoke all checksums - this will cause the encryption
			// of files with the new key next time FileSync runs
			currentProfileProperty.get().pathAndChecksum = new PathAndChecksum[] {};
		}
		try {
			objectMapper.writeValue(currentProfileProperty.get().file.get(), currentProfileProperty.get());
			currentProfileProperty.get().backup = objectMapper.readValue(currentProfileProperty.get().file.get(),
					Profile.class);
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

			if (!currentProfileProperty.get().backup.settings.publicKeyName
					.equals(currentProfileProperty.get().settings.publicKeyName)) {
				// public key has changed, revoke all checksums - this will cause the encryption
				// of files with the new key next time FileSync runs
				currentProfileProperty.get().pathAndChecksum = new PathAndChecksum[] {};
			}

			try {
				objectMapper.writeValue(file, currentProfileProperty.get());
				currentProfileProperty.get().file.set(file);
				currentProfileProperty.get().backup = objectMapper.readValue(file, Profile.class);
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
		info("Processing data, please wait...");
		stateProperty.set(State.SYNCING);

		new Thread(() -> {
			// as the files are encrypted, we can only compare against the data in the
			// profile.
			var pathHashAndChecksum = Collections
					.unmodifiableMap(Arrays.stream(currentProfileProperty.get().pathAndChecksum)
							.filter(pac -> pac.checksum != null) /* a directory */
							.collect(Collectors.toMap(pac -> pac.getReadableHash(), pac -> pac.checksum)));

			var pathAndChecksumNew = new ArrayList<PathAndChecksum>();

			// Part 1: left to right
			tbl_source.getItems().stream().filter(syncEntry -> syncEntry.compareProperty.get() == null
					|| syncEntry.compareProperty.get().result != Result.ERROR).anyMatch(syncEntry -> {
						if (stateProperty.get() != State.SYNCING)
							return true;

						syncSingle(syncEntry, pathHashAndChecksum, pathAndChecksumNew);

						return false;
					});

			if (stateProperty.get() != State.SYNCING)
				return;

			deleteFromMirror();

			currentProfileProperty.get().pathAndChecksum = pathAndChecksumNew.toArray(new PathAndChecksum[] {});

			var profileFile = currentProfileProperty.get().file.get();

			if (profileFile != null) {
				currentProfileProperty.get().backup.settings.publicKeyName = currentProfileProperty
						.get().settings.publicKeyName;
				actn_save(null);
			}

			stateProperty.set(State.DONE_SYNCING);

			info("Sync is completed." + profileFile == null ? ""
					: String.format(" Profile %s has been saved.", profileFile.getName()));
		}).start();
	}

	private void syncSingle(SyncEntry syncEntry, Map<String, byte[]> pathAndChecksumRef,
			ArrayList<PathAndChecksum> pathAndChecksumNew) {
		var sourcePath = syncEntry.sourcePathProperty.get();

		try {
			var keyPath = syncEntry.keyPathProperty.get();

			if (Files.isDirectory(sourcePath)) {
				var targetPath = destinationDir.get().resolve(keyPath);

				if (Files.exists(targetPath)) {
					syncEntry.compareProperty.set(new CompareResult(Result.MATCH));
				} else {
					Files.createDirectories(targetPath); // create if not exists
					syncEntry.compareProperty.set(new CompareResult(Result.MIRRORED));
				}

				pathAndChecksumNew.add(new PathAndChecksum(keyPath, null));
			} else {
				var encryptedFileName = String.format("%s.%s", keyPath.getFileName().toString(),
						MessageComposer.OMS_FILE_TYPE);
				var targetPath = keyPath.getNameCount() == 1 ? destinationDir.get().resolve(encryptedFileName)
						: destinationDir.get().resolve(keyPath.getParent()).resolve(encryptedFileName);

				// lookup reference data
				BiConsumer<SyncEntry, Path> fileProcessor = null;

				PathAndChecksum pathAndChecksum;

				try (FileInputStream fis = new FileInputStream(sourcePath.toFile())) {
					var md = MessageDigest.getInstance("SHA-256");
					var bArr = new byte[1024];
					int cnt;
					while ((cnt = fis.read(bArr)) != -1) {
						md.update(bArr, 0, cnt);
					}
					pathAndChecksum = new PathAndChecksum(keyPath, md.digest());
				}

				if (Files.exists(targetPath)) {
					var checksumRef = pathAndChecksumRef.get(pathAndChecksum.getReadableHash());
					if (checksumRef == null || !Arrays.equals(checksumRef, pathAndChecksum.checksum) ||
					/*
					 * if public key changes, all data has to be encrypted with the new one
					 */
							!currentProfileProperty.get().settings.publicKeyName
									.equals(currentProfileProperty.get().backup.settings.publicKeyName)) {
						fileProcessor = mirrorFile;
					}
				} else {
					fileProcessor = mirrorFile;
				}

				if (fileProcessor == null) {
					syncEntry.compareProperty.set(new CompareResult(Result.MATCH));
					pathAndChecksumNew.add(pathAndChecksum);
				} else {
					fileProcessor.accept(syncEntry, targetPath);
					if (syncEntry.compareProperty.get().result != Result.ERROR) {
						// all is well, update hash maps
						pathAndChecksumNew.add(pathAndChecksum);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			syncEntry.compareProperty.set(new CompareResult(e));
		} finally {
			if (stateProperty.get() == State.SYNCING)
				synchronized (FileSync.this) {
					totalSizeProperty.set(totalSizeProperty.get() - syncEntry.length);
					if (!Files.isDirectory(syncEntry.sourcePathProperty.get()))
						totalFilesProperty.set(totalFilesProperty.get() - 1);
				}
		}
	}

	private void deleteFromMirror() {
		info("Checking destination directory for deleted files...");

		var destPath = destinationDir.get();
		try {
			var deletions = Files.walk(destPath).filter(p -> !p.equals(destPath)) /* exclude destination's root */
					.map(p -> p.subpath(destPath.getNameCount(),
							p.getNameCount())/* path relative to destination's root */)
					.filter(p -> {
						/*
						 * we are walking through the encrypted directory. Directory names are
						 * unchanged, but file names end with OMS_FILE_TYPE
						 */
						if (!Files.isDirectory(p)) {
							var fn = p.getFileName().toString();

							if (fn.endsWith("." + MessageComposer.OMS_FILE_TYPE)) {
								var fn_original = Path.of(fn.substring(0,
										fn.length() - MessageComposer.OMS_FILE_TYPE.length() - 1 /* the dot */));

								if (p.getNameCount() == 1) {
									p = fn_original;
								} else {
									p = p.getParent().resolve(fn_original);
								}
							}
						}

						Path _p = p;

						return !filteredList.stream().anyMatch(syncEntry -> syncEntry.keyPathProperty.get().equals(_p));
					}).collect(Collectors.toList());

			Collections.sort(deletions, (p1, p2) -> {
				var i = Integer.compare(p2.getNameCount(), p1.getNameCount());
				if (i == 0)
					i = p2.toString().compareTo(p1.toString());
				return i;
			});

			deletions.stream().forEach(p -> {
				try {
					Files.delete(destPath.resolve(p));
					synchronized (deletionsProperty) {
						deletionsProperty.set(deletionsProperty.get() + 1);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private BiConsumer<SyncEntry, Path> mirrorFile = (syncEntry, targetPath) -> {
		try (FileInputStream fis = new FileInputStream(syncEntry.sourcePathProperty.get().toFile())) {
			EncryptedFile.create(fis, targetPath.toFile(),
					choice_public_key.getSelectionModel().getSelectedItem().publicKey,
					RSAUtils.getTransformationIdx(), AESUtil.getKeyLength(), AESUtil.getTransformationIdx(),
					() -> stateProperty.get() != State.SYNCING);
			syncEntry.compareProperty.set(new CompareResult(Result.MIRRORED));
		} catch (Exception e) {
			e.printStackTrace();
			syncEntry.compareProperty.set(new CompareResult(e));
		}
	};

	@FXML
	void actn_stop(ActionEvent event) {
		info("Cancelled!");
		switch (stateProperty.get()) {
		case ANALYZING -> stateProperty.set(State.READY_TO_ANALYZE);
		case SYNCING -> stateProperty.set(State.DONE_SYNCING);
		default -> throw new IllegalStateException();
		}
	}

	@FXML
	void actn_btn_new(ActionEvent event) {
		if (isProfileChanged()) {
			var alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("New Profile");
			alert.setContentText("Discard current changes" + (currentProfileProperty.get().file.get() == null ? ""
					: " of " + currentProfileProperty.get().file.get().getName()) + "?");

			alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
			alert.showAndWait().ifPresent(buttonType -> {
				if (buttonType == ButtonType.YES)
					currentProfileProperty.set(newProfile());
			});
			return;
		}

		currentProfileProperty.set(newProfile());
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
				currentProfileProperty.set(profile);
			} catch (Exception ex) {
				currentProfileProperty.set(null);
				FxMain.handleException(ex);
			}
		});
	}

	private void onProfileFileChanged(File f) {
		btn_del_profile.setDisable(f == null);
		lbl_profile.setText(f == null ? NEW_PROFILE : f.getAbsolutePath().toString());
	}

	@FXML
	private void actn_add_to_list(ActionEvent event) {
		var syncEntry = tbl_source.getSelectionModel().selectedItemProperty().get();

		var textInputDialog = new TextInputDialog(syncEntry.keyPathProperty.get().toString());

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

	private boolean matches(SyncEntry syncEntry) {
		if (!list_include.getItems().isEmpty()) {
			// check for include
			if (!list_include.getItems().stream().map(s -> toPattern(s))
					.anyMatch(s -> syncEntry.keyPathProperty.get().toString().matches(s)))
				return false;
		}

		return !list_exclude.getItems().stream().map(s -> toPattern(s))
				.anyMatch(s -> syncEntry.keyPathProperty.get().toString().matches(s))
				&& (!toggle_errors.isSelected() || (syncEntry.compareProperty.get() != null
						&& syncEntry.compareProperty.get().result == Result.ERROR));
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

	private final SimpleObjectProperty<Thread> infoThreadProperty = new SimpleObjectProperty<>();

	private void info(String message) {
		Platform.runLater(() -> {
			lbl_info.setText(message);
			var t = new Thread(() -> {
				try {
					Thread.sleep(10_000);
				} catch (InterruptedException e) {
				}

				synchronized (infoThreadProperty) {
					if (infoThreadProperty.get() == Thread.currentThread()) {
						Platform.runLater(() -> lbl_info.setText(null));
						infoThreadProperty.set(null);
					}
				}
			});
			synchronized (infoThreadProperty) {
				infoThreadProperty.set(t);
			}
			t.start();
		});
	}

	private static ImageView getImageView(String name) {
		return FxMain.getImageView(name, new Dimension2D(16, 16));
	}
}
