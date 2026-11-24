package controller;

import dao.ParkirDAO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.print.PrinterJob;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Petugas;
import model.SlotParkir;
import model.SlotPenuhException;
import model.TransaksiParkir;
import service.ParkirService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller utama Sistem Manajemen Parkir.
 * Mengontrol logika antarmuka (Dashboard, Transaksi, Slot, Laporan),
 * operasi CRUD database SQLite, dan sinkronisasi status slot & transaksi.
 */
public class ParkirController implements Initializable {

    private final ParkirService parkirService;
    private Petugas petugasAktif;
    private TransaksiParkir selectedTransaksi;
    private SlotParkir selectedSlot;
    private String currentPage = "Dashboard";

    // Format tanggal Indonesia
    private static final DateTimeFormatter DATE_HEADER_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"));
    private static final DateTimeFormatter TIME_INPUT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // ==========================================
    // FXML INJECTIONS: SIDEBAR & HEADER
    // ==========================================
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavTransaksi;
    @FXML private Button btnNavSlot;
    @FXML private Button btnNavLaporan;
    @FXML private Label lblPetugasInitial;
    @FXML private Label lblPetugasNama;
    @FXML private Label lblPetugasRole;
    @FXML private Button btnLogout;
    @FXML private Label lblHeaderDate;

    // Page Header Banner
    @FXML private Label lblPageNumber;
    @FXML private Label lblPageTitle;
    @FXML private Label lblPageDesc;
    @FXML private HBox boxHeaderActions;
    @FXML private Button btnTopAction;
    @FXML private Button btnTopExport;
    @FXML private Button btnTopPrint;

    // Stack Containers for Pages
    @FXML private VBox vboxDashboard;
    @FXML private VBox vboxTransaksi;
    @FXML private VBox vboxSlot;
    @FXML private VBox vboxLaporan;

    // ==========================================
    // FXML INJECTIONS: DASHBOARD
    // ==========================================
    @FXML private Label lblStatMasuk;
    @FXML private Label lblStatAktif;
    @FXML private Label lblStatSlot;
    @FXML private Label lblStatSlotNote;
    @FXML private Label lblStatPendapatan;
    @FXML private Label lblStatPendNote;

    @FXML private TableView<TransaksiParkir> tblDashboard;
    @FXML private TableColumn<TransaksiParkir, String> colDashId;
    @FXML private TableColumn<TransaksiParkir, String> colDashKendaraan;
    @FXML private TableColumn<TransaksiParkir, String> colDashSlot;
    @FXML private TableColumn<TransaksiParkir, String> colDashMasuk;
    @FXML private TableColumn<TransaksiParkir, String> colDashStatus;

    @FXML private Label lblPetugasWidgetInitial;
    @FXML private Label lblPetugasWidgetNama;

    @FXML private Label lblSlotRoda2;
    @FXML private Label lblSlotRoda4;
    @FXML private Label lblSlotTruk;
    @FXML private ProgressBar pbRoda2;
    @FXML private ProgressBar pbRoda4;
    @FXML private ProgressBar pbTruk;
    @FXML private Label lblSlotInfoRoda2;
    @FXML private Label lblSlotInfoRoda4;
    @FXML private Label lblSlotInfoTruk;

    // ==========================================
    // FXML INJECTIONS: TRANSAKSI PARKIR
    // ==========================================
    @FXML private Label lblFormTitle;
    @FXML private TextField txtPlatNomor;
    @FXML private TextField txtMerkKendaraan;
    @FXML private ComboBox<String> cmbJenisKendaraan;
    @FXML private ComboBox<String> cmbSlotParkir;
    @FXML private ToggleGroup tgMember;
    @FXML private RadioButton rbUmum;
    @FXML private RadioButton rbMember;
    @FXML private CheckBox chkMember; // Fallback jika menggunakan checkbox
    @FXML private CheckBox chkTitipHelm;
    @FXML private DatePicker dpWaktuMasuk;
    @FXML private TextField txtJamMasuk;
    @FXML private Button btnSimpanTransaksi;
    @FXML private Button btnResetForm;

    @FXML private TextField txtCari;
    @FXML private ComboBox<String> cmbFilterStatus;

    @FXML private TableView<TransaksiParkir> tblTransaksi;
    @FXML private TableColumn<TransaksiParkir, String> colIdTransaksi;
    @FXML private TableColumn<TransaksiParkir, String> colPlatNomor;
    @FXML private TableColumn<TransaksiParkir, String> colMerk;
    @FXML private TableColumn<TransaksiParkir, String> colKendaraan;
    @FXML private TableColumn<TransaksiParkir, String> colJenis;
    @FXML private TableColumn<TransaksiParkir, String> colSlot;
    @FXML private TableColumn<TransaksiParkir, String> colWaktuMasuk;
    @FXML private TableColumn<TransaksiParkir, String> colWaktuKeluar;
    @FXML private TableColumn<TransaksiParkir, String> colStatus;
    @FXML private TableColumn<TransaksiParkir, String> colTotalBiaya;
    @FXML private TableColumn<TransaksiParkir, Void> colAksi;
    @FXML private Label lblJumlahTransaksi;

    // ==========================================
    // FXML INJECTIONS: MANAJEMEN SLOT
    // ==========================================
    @FXML private Label lblTotalSlot;
    @FXML private Label lblSlotTersedia;
    @FXML private Label lblSlotTerisi;

    @FXML private Label lblSlotFormTitle;
    @FXML private TextField txtKodeSlot;
    @FXML private ComboBox<String> cmbLantaiZona;
    @FXML private TextField txtJenisSlot;
    @FXML private Button btnSimpanSlot;
    @FXML private Button btnHapusSlot;
    @FXML private ComboBox<String> cmbFilterSlot;

    @FXML private TableView<SlotParkir> tblSlot;
    @FXML private TableColumn<SlotParkir, String> colKodeSlot;
    @FXML private TableColumn<SlotParkir, String> colLantaiZona;
    @FXML private TableColumn<SlotParkir, String> colZonaSlot;
    @FXML private TableColumn<SlotParkir, String> colJenisSlot;
    @FXML private TableColumn<SlotParkir, String> colStatusSlot;
    @FXML private FlowPane fpSlotGrid;

    // ==========================================
    // FXML INJECTIONS: LAPORAN PENDAPATAN
    // ==========================================
    @FXML private DatePicker dpLaporanDari;
    @FXML private DatePicker dpLaporanSampai;
    @FXML private ComboBox<String> cmbLaporanStatus;

    @FXML private Label lblLapTotalTx;
    @FXML private Label lblLapAktif;
    @FXML private Label lblLapSelesai;
    @FXML private Label lblLapPendapatan;

    @FXML private TableView<TransaksiParkir> tblLaporan;
    @FXML private TableColumn<TransaksiParkir, String> colLapId;
    @FXML private TableColumn<TransaksiParkir, String> colLapPlat;
    @FXML private TableColumn<TransaksiParkir, String> colLapMerk;
    @FXML private TableColumn<TransaksiParkir, String> colLapJenis;
    @FXML private TableColumn<TransaksiParkir, String> colLapSlot;
    @FXML private TableColumn<TransaksiParkir, String> colLapMasuk;
    @FXML private TableColumn<TransaksiParkir, String> colLapKeluar;
    @FXML private TableColumn<TransaksiParkir, String> colLapStatus;
    @FXML private TableColumn<TransaksiParkir, String> colLapBiaya;

    // ==========================================
    // FXML INJECTIONS: FLOATING NOTICE TOAST
    // ==========================================
    @FXML private HBox boxNotice;
    @FXML private Label lblNoticeText;

    // Data lists
    private final ObservableList<TransaksiParkir> transaksiList = FXCollections.observableArrayList();
    private final ObservableList<TransaksiParkir> dashboardList = FXCollections.observableArrayList();
    private final ObservableList<TransaksiParkir> laporanList = FXCollections.observableArrayList();
    private final ObservableList<SlotParkir> slotList = FXCollections.observableArrayList();

    public ParkirController() {
        this(new ParkirDAO());
    }

    public ParkirController(ParkirDAO dao) {
        this.parkirService = new ParkirService(dao);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initHeaderDate();
        initComboBoxes();
        initTableColumns();
        initListeners();
        muatSemuaData();
    }

    // ==========================================
    // NAVIGASI HALAMAN (TABS)
    // ==========================================

    @FXML
    public void navDashboard() {
        switchPage("Dashboard");
    }

    @FXML
    public void navTransaksi() {
        switchPage("Transaksi Parkir");
    }

    @FXML
    public void navSlot() {
        switchPage("Manajemen Slot");
    }

    @FXML
    public void navLaporan() {
        switchPage("Laporan Pendapatan");
    }

    public void switchPage(String pageName) {
        this.currentPage = pageName;

        // Reset visibility semua halaman
        if (vboxDashboard != null) { vboxDashboard.setVisible(false); vboxDashboard.setManaged(false); }
        if (vboxTransaksi != null) { vboxTransaksi.setVisible(false); vboxTransaksi.setManaged(false); }
        if (vboxSlot != null) { vboxSlot.setVisible(false); vboxSlot.setManaged(false); }
        if (vboxLaporan != null) { vboxLaporan.setVisible(false); vboxLaporan.setManaged(false); }

        // Reset styling nav buttons
        resetNavButtonStyles();

        // Update header banner
        switch (pageName) {
            case "Dashboard" -> {
                if (vboxDashboard != null) { vboxDashboard.setVisible(true); vboxDashboard.setManaged(true); }
                setPageBanner("01", "Dashboard", "Ringkasan aktivitas dan operasional parkir hari ini.");
                highlightNavButton(btnNavDashboard);
                setTopButtonsVisibility(true, false, false);
            }
            case "Transaksi Parkir" -> {
                if (vboxTransaksi != null) { vboxTransaksi.setVisible(true); vboxTransaksi.setManaged(true); }
                setPageBanner("02", "Transaksi Parkir", "Catat kendaraan masuk, kelola transaksi, dan proses kendaraan keluar.");
                highlightNavButton(btnNavTransaksi);
                setTopButtonsVisibility(false, false, false);
            }
            case "Manajemen Slot" -> {
                if (vboxSlot != null) { vboxSlot.setVisible(true); vboxSlot.setManaged(true); }
                setPageBanner("03", "Manajemen Slot", "Kelola zona, jenis kendaraan, dan ketersediaan slot parkir.");
                highlightNavButton(btnNavSlot);
                setTopButtonsVisibility(false, false, false);
            }
            case "Laporan Pendapatan" -> {
                if (vboxLaporan != null) { vboxLaporan.setVisible(true); vboxLaporan.setManaged(true); }
                setPageBanner("04", "Laporan Pendapatan", "Pantau pendapatan dan riwayat transaksi parkir.");
                highlightNavButton(btnNavLaporan);
                setTopButtonsVisibility(false, true, true);
                terapkanFilterLaporan();
            }
        }

        muatSemuaData();
    }

    private void setPageBanner(String num, String title, String desc) {
        if (lblPageNumber != null) lblPageNumber.setText(num);
        if (lblPageTitle != null) lblPageTitle.setText(title);
        if (lblPageDesc != null) lblPageDesc.setText(desc);
    }

    private void resetNavButtonStyles() {
        List<Button> buttons = Arrays.asList(btnNavDashboard, btnNavTransaksi, btnNavSlot, btnNavLaporan);
        for (Button b : buttons) {
            if (b != null) {
                b.getStyleClass().removeAll("nav-btn-active");
                if (!b.getStyleClass().contains("nav-btn")) {
                    b.getStyleClass().add("nav-btn");
                }
            }
        }
    }

    private void highlightNavButton(Button activeBtn) {
        if (activeBtn != null && !activeBtn.getStyleClass().contains("nav-btn-active")) {
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }

    private void setTopButtonsVisibility(boolean showMasuk, boolean showExport, boolean showPrint) {
        if (btnTopAction != null) {
            btnTopAction.setVisible(showMasuk);
            btnTopAction.setManaged(showMasuk);
        }
        if (btnTopExport != null) {
            btnTopExport.setVisible(showExport);
            btnTopExport.setManaged(showExport);
        }
        if (btnTopPrint != null) {
            btnTopPrint.setVisible(showPrint);
            btnTopPrint.setManaged(showPrint);
        }
    }

    @FXML
    public void aksiCepatKeluar() {
        navTransaksi();
        if (cmbFilterStatus != null) {
            cmbFilterStatus.setValue("Sedang Parkir");
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Keluar dari sesi petugas?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Konfirmasi Logout");
        alert.setHeaderText("Logout Petugas");
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
                    Parent root = loader.load();
                    petugasAktif = null;
                    Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                    Scene scene = new Scene(root, 1200, 760);
                    stage.setTitle("Sistem Manajemen Parkir Kampus — Login");
                    stage.setScene(scene);
                    stage.show();
                    stage.setMaximized(true);
                } catch (IOException e) {
                    tampilkanNotice("Gagal kembali ke login: " + e.getMessage());
                }
            }
        });
    }

    // ==========================================
    // INITIALISASI TANGGAL & COMBOBOX
    // ==========================================

    private void initHeaderDate() {
        if (lblHeaderDate != null) {
            lblHeaderDate.setText(LocalDate.now().format(DATE_HEADER_FORMAT));
        }
    }

    private void initComboBoxes() {
        if (dpWaktuMasuk != null && dpWaktuMasuk.getValue() == null) dpWaktuMasuk.setValue(LocalDate.now());
        if (txtJamMasuk != null && txtJamMasuk.getText().isBlank()) txtJamMasuk.setText(LocalTime.now().format(TIME_INPUT_FORMAT));
        // Jenis Kendaraan
        if (cmbJenisKendaraan != null) {
            cmbJenisKendaraan.getItems().setAll("Roda2", "Roda4", "Truk");
            cmbJenisKendaraan.setValue("Roda2");
            cmbJenisKendaraan.valueProperty().addListener((obs, oldVal, newVal) -> {
                updateSlotComboBox(newVal);
                if (chkTitipHelm != null) {
                    chkTitipHelm.setDisable(!"Roda2".equalsIgnoreCase(newVal));
                    if (!"Roda2".equalsIgnoreCase(newVal)) {
                        chkTitipHelm.setSelected(false);
                    }
                }
            });
        }

        // Filter Status Transaksi
        if (cmbFilterStatus != null) {
            cmbFilterStatus.getItems().setAll("Semua", "Sedang Parkir", "Selesai");
            cmbFilterStatus.setValue("Semua");
            cmbFilterStatus.valueProperty().addListener((obs, oldVal, newVal) -> cariTransaksi());
        }

        // Slot ComboBoxes
        if (cmbLantaiZona != null) {
            cmbLantaiZona.getItems().setAll("Zona A", "Zona B", "Zona C");
            cmbLantaiZona.setValue("Zona A");
            cmbLantaiZona.valueProperty().addListener((obs, oldZone, newZone) -> updateSlotZoneFields(newZone));
        }
        if (txtKodeSlot != null) {
            txtKodeSlot.setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().matches("\\d{0,9}") ? change : null));
        }
        updateSlotZoneFields(cmbLantaiZona != null ? cmbLantaiZona.getValue() : "Zona A");

        if (cmbFilterSlot != null) {
            cmbFilterSlot.getItems().setAll("Semua", "Roda2", "Roda4", "Truk");
            cmbFilterSlot.setValue("Semua");
        }

        // Laporan ComboBox
        if (cmbLaporanStatus != null) {
            cmbLaporanStatus.getItems().setAll("Semua", "Sedang Parkir", "Selesai");
            cmbLaporanStatus.setValue("Semua");
        }
    }

    private void initTableColumns() {
        // 1. Table Transaksi Utama
        if (tblTransaksi != null) {
            if (colIdTransaksi != null) colIdTransaksi.setCellValueFactory(new PropertyValueFactory<>("idTransaksi"));
            if (colPlatNomor != null) colPlatNomor.setCellValueFactory(new PropertyValueFactory<>("platNomor"));
            if (colMerk != null) colMerk.setCellValueFactory(new PropertyValueFactory<>("merkKendaraan"));
            if (colKendaraan != null) {
                colKendaraan.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPlatNomor()));
                colKendaraan.setCellFactory(column -> new TableCell<>() {
                    @Override protected void updateItem(String plate, boolean empty) {
                        super.updateItem(plate, empty);
                        if (empty || plate == null) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            TransaksiParkir tx = getTableRow().getItem();
                            if (tx == null) { setGraphic(null); return; }
                            Label plateLabel = new Label(tx.getPlatNomor());
                            plateLabel.getStyleClass().add("vehicle-plate");
                            Label detailLabel = new Label(tx.getMerkKendaraan() + " · " + labelJenis(tx.getJenisKendaraan()));
                            detailLabel.getStyleClass().add("vehicle-detail");
                            VBox vehicle = new VBox(3, plateLabel, detailLabel);
                            setGraphic(vehicle);
                            setText(null);
                        }
                    }
                });
            }
            if (colJenis != null) colJenis.setCellValueFactory(new PropertyValueFactory<>("jenisKendaraan"));
            if (colSlot != null) colSlot.setCellValueFactory(new PropertyValueFactory<>("kodeSlot"));
            if (colWaktuMasuk != null) colWaktuMasuk.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getWaktuMasukFormatted()));
            if (colWaktuKeluar != null) colWaktuKeluar.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getWaktuKeluarFormatted()));
            if (colStatus != null) {
                colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
                setupStatusColumn(colStatus);
            }
            if (colAksi != null) setupTransactionActionColumn(colAksi);
            if (colTotalBiaya != null) colTotalBiaya.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalBiayaFormatted()));

            tblTransaksi.setItems(transaksiList);
        }

        // 2. Table Dashboard (Recent 5)
        if (tblDashboard != null) {
            if (colDashId != null) colDashId.setCellValueFactory(new PropertyValueFactory<>("idTransaksi"));
            if (colDashKendaraan != null) {
                colDashKendaraan.setCellValueFactory(cell -> new SimpleStringProperty(
                        cell.getValue().getPlatNomor() + " · " + cell.getValue().getMerkKendaraan()
                ));
            }
            if (colDashSlot != null) colDashSlot.setCellValueFactory(new PropertyValueFactory<>("kodeSlot"));
            if (colDashMasuk != null) colDashMasuk.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getWaktuMasukFormatted()));
            if (colDashStatus != null) {
                colDashStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
                setupStatusColumn(colDashStatus);
            }
            tblDashboard.setItems(dashboardList);
        }

        // 3. Table Slot Parkir
        if (tblSlot != null) {
            if (colKodeSlot != null) colKodeSlot.setCellValueFactory(new PropertyValueFactory<>("kodeSlot"));
            if (colLantaiZona != null) colLantaiZona.setCellValueFactory(new PropertyValueFactory<>("lantaiZona"));
            if (colZonaSlot != null) colZonaSlot.setCellValueFactory(new PropertyValueFactory<>("lantaiZona"));
            if (colJenisSlot != null) colJenisSlot.setCellValueFactory(new PropertyValueFactory<>("jenisKendaraan"));
            if (colStatusSlot != null) {
                colStatusSlot.setCellValueFactory(new PropertyValueFactory<>("status"));
                setupSlotStatusColumn(colStatusSlot);
            }
            tblSlot.setItems(slotList);
        }

        // 4. Table Laporan
        if (tblLaporan != null) {
            if (colLapId != null) colLapId.setCellValueFactory(new PropertyValueFactory<>("idTransaksi"));
            if (colLapPlat != null) colLapPlat.setCellValueFactory(new PropertyValueFactory<>("platNomor"));
            if (colLapMerk != null) colLapMerk.setCellValueFactory(new PropertyValueFactory<>("merkKendaraan"));
            if (colLapJenis != null) colLapJenis.setCellValueFactory(new PropertyValueFactory<>("jenisKendaraan"));
            if (colLapSlot != null) colLapSlot.setCellValueFactory(new PropertyValueFactory<>("kodeSlot"));
            if (colLapMasuk != null) colLapMasuk.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getWaktuMasukFormatted()));
            if (colLapKeluar != null) colLapKeluar.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getWaktuKeluarFormatted()));
            if (colLapStatus != null) {
                colLapStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
                setupStatusColumn(colLapStatus);
            }
            if (colLapBiaya != null) colLapBiaya.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTotalBiayaFormatted()));

            tblLaporan.setItems(laporanList);
        }
    }

    private void setupStatusColumn(TableColumn<TransaksiParkir, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("status-badge");
                    if ("PARKIR".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("status-badge-active");
                    } else {
                        badge.getStyleClass().add("status-badge-done");
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void setupTransactionActionColumn(TableColumn<TransaksiParkir, Void> column) {
        column.setCellFactory(tableColumn -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                TransaksiParkir tx = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(4);
                if ("SELESAI".equalsIgnoreCase(tx.getStatus())) {
                    Button receipt = rowAction("STRUK", "btn-secondary");
                    receipt.setOnAction(event -> { pilihTransaksiBaris(tx); tampilkanDialogStruk(tx); });
                    actions.getChildren().add(receipt);
                } else {
                    Button edit = rowAction("UBAH", "btn-secondary");
                    edit.setOnAction(event -> { pilihTransaksiBaris(tx); editTransaksiDipilih(); });
                    Button checkout = rowAction("KELUAR", "btn-primary");
                    SVGPath checkoutArrow = new SVGPath();
                    checkoutArrow.setContent("M2 12h18 M14 6l6 6-6 6");
                    checkoutArrow.setStyle("-fx-fill: transparent; -fx-stroke: white; -fx-stroke-width: 1.6;");
                    checkout.setGraphic(checkoutArrow);
                    checkout.setOnAction(event -> { pilihTransaksiBaris(tx); prosesKendaraanKeluar(); });
                    actions.getChildren().addAll(edit, checkout);
                }
                Button delete = rowAction("×", "btn-secondary");
                delete.setAccessibleText("Hapus " + tx.getPlatNomor());
                delete.setOnAction(event -> { pilihTransaksiBaris(tx); hapusTransaksiDipilih(); });
                actions.getChildren().add(delete);
                setGraphic(actions);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
            }
        });
    }

    private String labelJenis(String jenis) {
        if (jenis == null) return "Kendaraan";
        return switch (jenis) {
            case "Roda2" -> "Motor";
            case "Roda4" -> "Mobil";
            default -> "Truk";
        };
    }

    private Button rowAction(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().setAll(styleClass);
        button.setMinHeight(40);
        button.setPrefHeight(40);
        return button;
    }

    private void pilihTransaksiBaris(TransaksiParkir tx) {
        selectedTransaksi = tx;
        if (tblTransaksi != null) tblTransaksi.getSelectionModel().select(tx);
    }

    private void setupSlotStatusColumn(TableColumn<SlotParkir, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("TERISI".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: #252525; -fx-text-fill: white; -fx-padding: 3 8; -fx-font-family: 'IBM Plex Mono', monospace; -fx-font-size: 11px;");
                    } else {
                        badge.setStyle("-fx-border-color: #b5b5b5; -fx-text-fill: #454545; -fx-padding: 2 8; -fx-font-family: 'IBM Plex Mono', monospace; -fx-font-size: 11px;");
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void initListeners() {
        if (txtCari != null) {
            txtCari.textProperty().addListener((obs, oldVal, newVal) -> cariTransaksi());
        }

        if (tblTransaksi != null) {
            tblTransaksi.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
                this.selectedTransaksi = selected;
            });
        }

        if (tblSlot != null) {
            tblSlot.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
                this.selectedSlot = selected;
                if (selected != null && txtKodeSlot != null) {
                    txtKodeSlot.setText(selected.getNomorSlot());
                    if (cmbLantaiZona != null) cmbLantaiZona.setValue(selected.getZona());
                    if (txtJenisSlot != null) txtJenisSlot.setText(selected.getJenisKendaraan());
                    if (lblSlotFormTitle != null) lblSlotFormTitle.setText("Ubah Slot " + selected.getKodeSlot());
                }
            });
        }
    }

    // ==========================================
    // OPERASI CRUD TRANSAKSI PARKIR
    // ==========================================

    @FXML
    public void simpanTransaksi() {
        String plat = (txtPlatNomor != null) ? txtPlatNomor.getText().trim().toUpperCase() : "";
        String merk = (txtMerkKendaraan != null) ? txtMerkKendaraan.getText().trim() : "";
        String jenis = (cmbJenisKendaraan != null && cmbJenisKendaraan.getValue() != null) ? cmbJenisKendaraan.getValue() : "Roda2";
        String slot = (cmbSlotParkir != null) ? cmbSlotParkir.getValue() : null;

        if (petugasAktif == null) {
            tampilkanNotice("Sesi petugas tidak aktif. Silakan login kembali.");
            return;
        }

        boolean isMemberVal = (rbMember != null && rbMember.isSelected()) || (chkMember != null && chkMember.isSelected());
        boolean titipHelmVal = (chkTitipHelm != null && chkTitipHelm.isSelected());
        String petugas = petugasAktif.getUsername();
        LocalDateTime waktuMasuk;
        try {
            LocalDate tanggal = dpWaktuMasuk != null && dpWaktuMasuk.getValue() != null
                    ? dpWaktuMasuk.getValue() : LocalDate.now();
            LocalTime jam = txtJamMasuk != null && !txtJamMasuk.getText().isBlank()
                    ? LocalTime.parse(txtJamMasuk.getText().trim(), TIME_INPUT_FORMAT) : LocalTime.now();
            waktuMasuk = LocalDateTime.of(tanggal, jam);
        } catch (java.time.DateTimeException e) {
            tampilkanNotice("Format waktu masuk harus HH:mm.");
            return;
        }

        if (plat.isEmpty()) {
            tampilkanNotice("Plat nomor wajib diisi!");
            return;
        }

        if (merk.isEmpty()) {
            tampilkanNotice("Merk kendaraan wajib diisi!");
            return;
        }

        if (waktuMasuk.isAfter(LocalDateTime.now())) {
            tampilkanNotice("Waktu masuk tidak boleh melebihi waktu sekarang.");
            return;
        }

        try {
            if (selectedTransaksi != null && btnSimpanTransaksi != null && "Update Transaksi".equalsIgnoreCase(btnSimpanTransaksi.getText())) {
                String slotEdit = slot != null && !slot.isEmpty() ? slot : selectedTransaksi.getKodeSlot();
                TransaksiParkir edited = new TransaksiParkir(selectedTransaksi.getIdTransaksi(), plat, merk, jenis,
                        slotEdit, isMemberVal, "Roda2".equalsIgnoreCase(jenis) && titipHelmVal,
                        waktuMasuk, selectedTransaksi.getPetugas());
                parkirService.updateActiveTransaction(edited);
                tampilkanNotice("Transaksi " + edited.getIdTransaksi() + " berhasil diperbarui.");
            } else {
                // Create Transaksi Baru
                TransaksiParkir baru = new TransaksiParkir(
                        null, plat, merk, jenis, slot, isMemberVal,
                        "Roda2".equalsIgnoreCase(jenis) && titipHelmVal,
                        waktuMasuk, petugas
                );

                parkirService.checkIn(baru);
                tampilkanNotice("Kendaraan " + plat + " masuk di slot " + baru.getKodeSlot() + ".");
            }

            resetFormTransaksi();
            muatSemuaData();
        } catch (SlotPenuhException e) {
            tampilkanNotice("Slot penuh. Tidak ada slot tersedia untuk jenis kendaraan ini.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            tampilkanNotice(e.getMessage());
        } catch (SQLException e) {
            tampilkanNotice("Database Error: " + e.getMessage());
        }
    }

    @FXML
    public void prosesKendaraanKeluar() {
        TransaksiParkir tx = (tblTransaksi != null) ? tblTransaksi.getSelectionModel().getSelectedItem() : selectedTransaksi;
        if (tx == null) {
            tampilkanNotice("Pilih transaksi kendaraan yang akan keluar terlebih dahulu.");
            return;
        }

        if ("SELESAI".equalsIgnoreCase(tx.getStatus())) {
            tampilkanNotice("Transaksi ini sudah selesai (kendaraan sudah keluar).");
            return;
        }

        LocalDateTime waktuKeluar = LocalDateTime.now();
        TransaksiParkir preview;
        try {
            preview = parkirService.previewCheckout(tx, waktuKeluar);
        } catch (IllegalArgumentException | IllegalStateException e) {
            tampilkanNotice(e.getMessage());
            return;
        }
        double totalBiaya = preview.getTotalBiaya();
        long durasiJam = preview.hitungDurasiJam();

        // Dialog Checkout persis seperti modal App.tsx line 122
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Proses Kendaraan Keluar");
        dialog.setHeaderText("Konfirmasi Kendaraan Keluar");

        VBox content = new VBox(14);
        content.setStyle("-fx-padding: 20; -fx-pref-width: 440; -fx-background-color: #fafafa;");

        Label lblPlate = new Label(preview.getPlatNomor());
        lblPlate.setStyle("-fx-font-family: 'IBM Plex Mono', monospace; -fx-font-size: 24px; -fx-font-weight: bold;");

        Label lblSub = new Label(preview.getMerkKendaraan() + " · Slot " + preview.getKodeSlot());
        lblSub.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");

        VBox detailBox = new VBox(8);
        detailBox.setStyle("-fx-border-color: #bbb; -fx-border-width: 1 0 1 0; -fx-border-style: dashed; -fx-padding: 14 0;");

        detailBox.getChildren().addAll(
                createRowLabel("Waktu masuk", preview.getWaktuMasukFormatted()),
                createRowLabel("Durasi (pembulatan)", durasiJam + " jam"),
                createRowLabel("Pelanggan", preview.isMember() ? "Member · diskon 10%" : "Umum")
        );

        if (preview.isTitipHelm()) {
            Label lblHelm = new Label("Termasuk titip helm Rp 2.000");
            lblHelm.setStyle("-fx-font-size: 12px; -fx-text-fill: #444;");
            detailBox.getChildren().add(lblHelm);
        }

        HBox totalRow = new HBox();
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Label lblTotalTitle = new Label("Total pembayaran");
        lblTotalTitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        Label lblTotalValue = new Label(tx.getTotalBiayaFormatted());
        lblTotalValue.setStyle("-fx-font-family: 'IBM Plex Mono', monospace; -fx-font-size: 26px; -fx-font-weight: bold;");
        totalRow.getChildren().addAll(lblTotalTitle, r, lblTotalValue);

        content.getChildren().addAll(lblPlate, lblSub, detailBox, totalRow);
        dialog.getDialogPane().setContent(content);

        ButtonType btnBayar = new ButtonType("KONFIRMASI BAYAR & KELUAR →", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnBayar, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(res -> {
            if (res == btnBayar) {
                try {
                    TransaksiParkir completed = parkirService.checkout(tx.getIdTransaksi(), waktuKeluar);
                    tampilkanNotice(completed.getKodeSlot() == null || completed.getKodeSlot().isBlank()
                            ? "Pembayaran berhasil. Slot asal sudah dihapus."
                            : "Pembayaran berhasil. Slot " + completed.getKodeSlot() + " kembali tersedia.");
                    muatSemuaData();
                    // Tampilkan Struk Pembayaran
                    tampilkanDialogStruk(completed);
                } catch (SQLException e) {
                    tampilkanNotice("Database Error saat checkout: " + e.getMessage());
                } catch (IllegalArgumentException | IllegalStateException e) {
                    tampilkanNotice(e.getMessage());
                }
            }
        });
    }

    private HBox createRowLabel(String left, String right) {
        HBox row = new HBox();
        Label l = new Label(left);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        Label val = new Label(right);
        val.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        row.getChildren().addAll(l, r, val);
        return row;
    }

    @FXML
    public void editTransaksiDipilih() {
        TransaksiParkir tx = (tblTransaksi != null) ? tblTransaksi.getSelectionModel().getSelectedItem() : selectedTransaksi;
        if (tx == null) {
            tampilkanNotice("Pilih transaksi yang ingin diubah terlebih dahulu.");
            return;
        }

        if ("SELESAI".equalsIgnoreCase(tx.getStatus())) {
            tampilkanNotice("Transaksi selesai tidak dapat diubah.");
            return;
        }

        this.selectedTransaksi = tx;
        if (txtPlatNomor != null) txtPlatNomor.setText(tx.getPlatNomor());
        if (txtMerkKendaraan != null) txtMerkKendaraan.setText(tx.getMerkKendaraan());
        if (cmbJenisKendaraan != null) cmbJenisKendaraan.setValue(tx.getJenisKendaraan());
        if (cmbSlotParkir != null && tx.getKodeSlot() != null && !tx.getKodeSlot().isBlank()) cmbSlotParkir.setValue(tx.getKodeSlot());
        if (rbMember != null) rbMember.setSelected(tx.isMember());
        if (rbUmum != null) rbUmum.setSelected(!tx.isMember());
        if (chkMember != null) chkMember.setSelected(tx.isMember());
        if (chkTitipHelm != null) chkTitipHelm.setSelected(tx.isTitipHelm());
        if (dpWaktuMasuk != null && tx.getWaktuMasuk() != null) dpWaktuMasuk.setValue(tx.getWaktuMasuk().toLocalDate());
        if (txtJamMasuk != null && tx.getWaktuMasuk() != null) txtJamMasuk.setText(tx.getWaktuMasuk().toLocalTime().format(TIME_INPUT_FORMAT));

        if (lblFormTitle != null) lblFormTitle.setText("Ubah Transaksi " + tx.getIdTransaksi());
        if (btnSimpanTransaksi != null) btnSimpanTransaksi.setText("Update Transaksi");
    }

    @FXML
    public void hapusTransaksiDipilih() {
        TransaksiParkir tx = (tblTransaksi != null) ? tblTransaksi.getSelectionModel().getSelectedItem() : selectedTransaksi;
        if (tx == null) {
            tampilkanNotice("Pilih transaksi yang ingin dihapus.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus transaksi " + tx.getIdTransaksi() + " (" + tx.getPlatNomor() + ")?\nTindakan ini tidak dapat dibatalkan.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Konfirmasi Hapus");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    parkirService.deleteTransaction(tx.getIdTransaksi());
                    tampilkanNotice("Data transaksi berhasil dihapus.");
                    resetFormTransaksi();
                    muatSemuaData();
                } catch (SQLException e) {
                    tampilkanNotice("Gagal menghapus: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    public void cetakStrukDipilih() {
        TransaksiParkir tx = (tblTransaksi != null) ? tblTransaksi.getSelectionModel().getSelectedItem() : selectedTransaksi;
        if (tx == null && tblLaporan != null) {
            tx = tblLaporan.getSelectionModel().getSelectedItem();
        }
        if (tx == null) {
            tampilkanNotice("Pilih transaksi untuk mencetak struk.");
            return;
        }
        tampilkanDialogStruk(tx);
    }

    private void tampilkanDialogStruk(TransaksiParkir tx) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Struk Pembayaran");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/view/style.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("receipt-dialog");
        dialog.getDialogPane().setPrefWidth(520);

        VBox dialogBody = new VBox(0);
        HBox modalHeader = new HBox();
        modalHeader.setAlignment(Pos.CENTER_LEFT);
        modalHeader.getStyleClass().add("receipt-modal-header");
        Label modalTitle = new Label("Struk Pembayaran");
        modalTitle.getStyleClass().add("receipt-modal-title");
        Region modalSpacer = new Region();
        HBox.setHgrow(modalSpacer, Priority.ALWAYS);
        Button closeIcon = new Button("×");
        closeIcon.getStyleClass().add("receipt-close");
        closeIcon.setAccessibleText("Tutup struk");
        closeIcon.setOnAction(event -> dialog.close());
        modalHeader.getChildren().addAll(modalTitle, modalSpacer, closeIcon);

        VBox receipt = new VBox(16);
        receipt.getStyleClass().add("receipt-content");
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        VBox heading = new VBox(4);
        Label brand = new Label("PARKIR KAMPUS");
        brand.getStyleClass().add("receipt-brand");
        Label subtitle = new Label("BUKTI PEMBAYARAN PARKIR");
        subtitle.getStyleClass().add("receipt-caption");
        heading.getChildren().addAll(brand, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label receiptId = new Label(tx.getIdTransaksi());
        receiptId.getStyleClass().add("receipt-id");
        titleBar.getChildren().addAll(heading, spacer, receiptId);

        VBox rows = new VBox(12);
        rows.getStyleClass().add("receipt-rows");
        rows.getChildren().addAll(
                receiptRow("Plat nomor", tx.getPlatNomor()),
                receiptRow("Kendaraan", tx.getMerkKendaraan() + " · " + labelJenis(tx.getJenisKendaraan())),
                receiptRow("Slot", tx.getKodeSlot()),
                receiptRow("Masuk", tx.getWaktuMasukFormatted()),
                receiptRow("Keluar", tx.getWaktuKeluarFormatted()),
                receiptRow("Petugas", petugasAktif != null ? petugasAktif.getNama()
                        : (tx.getPetugas() == null ? "Petugas parkir" : tx.getPetugas()))
        );

        HBox total = new HBox();
        total.setAlignment(Pos.CENTER_LEFT);
        total.getStyleClass().add("receipt-total");
        Label totalLabel = new Label("TOTAL BAYAR");
        totalLabel.getStyleClass().add("receipt-total-label");
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        Label totalValue = new Label(tx.getTotalBiayaFormatted());
        totalValue.getStyleClass().add("receipt-total-value");
        total.getChildren().addAll(totalLabel, totalSpacer, totalValue);

        Label thankYou = new Label("Terima kasih. Hati-hati di perjalanan.");
        thankYou.getStyleClass().add("receipt-thanks");
        receipt.getChildren().addAll(titleBar, rows, total, thankYou);
        dialogBody.getChildren().addAll(modalHeader, receipt);
        dialog.getDialogPane().setContent(dialogBody);

        ButtonType printButton = new ButtonType("CETAK STRUK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(printButton);
        Button print = (Button) dialog.getDialogPane().lookupButton(printButton);
        print.getStyleClass().add("btn-primary");

        dialog.showAndWait().ifPresent(result -> {
            if (result == printButton) printReceipt(tx.cetakStruk());
        });
    }

    private HBox receiptRow(String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label left = new Label(label);
        left.getStyleClass().add("receipt-row-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label right = new Label(value == null || value.isBlank() ? "—" : value);
        right.getStyleClass().add("receipt-row-value");
        row.getChildren().addAll(left, spacer, right);
        return row;
    }

    private void printReceipt(String receiptText) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(null)) return;
        TextArea page = new TextArea(receiptText);
        page.setEditable(false);
        page.setWrapText(true);
        page.setStyle("-fx-font-family: 'IBM Plex Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        page.setPrefColumnCount(38);
        page.setPrefRowCount(28);
        if (job.printPage(page)) job.endJob();
        else job.cancelJob();
    }

    @FXML
    public void resetFormTransaksi() {
        if (txtPlatNomor != null) txtPlatNomor.clear();
        if (txtMerkKendaraan != null) txtMerkKendaraan.clear();
        if (cmbJenisKendaraan != null) cmbJenisKendaraan.setValue("Roda2");
        if (rbUmum != null) rbUmum.setSelected(true);
        if (rbMember != null) rbMember.setSelected(false);
        if (chkMember != null) chkMember.setSelected(false);
        if (chkTitipHelm != null) chkTitipHelm.setSelected(false);
        if (dpWaktuMasuk != null) dpWaktuMasuk.setValue(LocalDate.now());
        if (txtJamMasuk != null) txtJamMasuk.setText(LocalTime.now().format(TIME_INPUT_FORMAT));
        if (btnSimpanTransaksi != null) btnSimpanTransaksi.setText("SIMPAN DATA");
        if (lblFormTitle != null) lblFormTitle.setText("Kendaraan Masuk");
        this.selectedTransaksi = null;
        updateSlotComboBox("Roda2");
    }

    // ==========================================
    // OPERASI CRUD SLOT PARKIR
    // ==========================================

    @FXML
    public void simpanSlot() {
        String zona = cmbLantaiZona != null ? cmbLantaiZona.getValue() : null;
        if (zona == null) {
            tampilkanNotice("Pilih zona parkir.");
            return;
        }

        try {
            String kodeBaru = SlotParkir.kodeDariNomor(zona, txtKodeSlot == null ? null : txtKodeSlot.getText());
            SlotParkir duplicate = parkirService.slot(kodeBaru);
            if (duplicate != null && (selectedSlot == null || !duplicate.getKodeSlot().equalsIgnoreCase(selectedSlot.getKodeSlot()))) {
                tampilkanNotice("Nomor slot " + kodeBaru + " sudah digunakan.");
                return;
            }
            if (selectedSlot != null) {
                String kodeLama = selectedSlot.getKodeSlot();
                SlotParkir current = parkirService.slot(kodeLama);
                if (current == null) {
                    tampilkanNotice("Slot yang akan diubah tidak lagi tersedia.");
                    return;
                }
                SlotParkir baru = new SlotParkir(kodeBaru, zona, SlotParkir.jenisUntukZona(zona), current.getStatus());
                parkirService.updateSlot(kodeLama, baru);
                tampilkanNotice("Data slot " + kodeBaru + " berhasil diperbarui.");
            } else {
                SlotParkir baru = new SlotParkir(kodeBaru, zona, SlotParkir.jenisUntukZona(zona), "TERSEDIA");
                parkirService.createSlot(baru);
                tampilkanNotice("Data slot baru " + baru.getKodeSlot() + " berhasil disimpan.");
            }

            resetFormSlot();
            muatSemuaData();
        } catch (IllegalArgumentException | IllegalStateException e) {
            tampilkanNotice(e.getMessage());
        } catch (SQLException e) {
            tampilkanNotice("Database Error: " + e.getMessage());
        }
    }

    @FXML
    public void resetFormSlot() {
        selectedSlot = null;
        if (cmbLantaiZona != null) cmbLantaiZona.setValue("Zona A");
        if (txtKodeSlot != null) txtKodeSlot.clear();
        updateSlotZoneFields(cmbLantaiZona != null ? cmbLantaiZona.getValue() : "Zona A");
        if (lblSlotFormTitle != null) lblSlotFormTitle.setText("Tambah Slot");
    }

    private void updateSlotZoneFields(String zona) {
        if (zona == null) return;
        if (txtJenisSlot != null) txtJenisSlot.setText(SlotParkir.jenisUntukZona(zona));
    }

    @FXML
    public void hapusSlotDipilih() {
        SlotParkir rowSelection = tblSlot == null ? null : tblSlot.getSelectionModel().getSelectedItem();
        SlotParkir slot = rowSelection != null ? rowSelection : selectedSlot;
        if (slot == null) {
            tampilkanNotice("Pilih slot yang ingin dihapus.");
            return;
        }

        SlotParkir target = slot;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Hapus slot " + target.getKodeSlot() + "? Tindakan ini tidak dapat dibatalkan.", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Konfirmasi Hapus");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    parkirService.deleteSlot(target.getKodeSlot());
                    tampilkanNotice("Data slot berhasil dihapus.");
                    resetFormSlot();
                    muatSemuaData();
                } catch (SQLException e) {
                    tampilkanNotice("Gagal menghapus slot: " + e.getMessage());
                } catch (IllegalStateException e) {
                    tampilkanNotice(e.getMessage());
                }
            }
        });
    }

    @FXML
    public void filterSlot() {
        String filter = (cmbFilterSlot != null && cmbFilterSlot.getValue() != null) ? cmbFilterSlot.getValue() : "Semua";
        try {
            List<SlotParkir> all = parkirService.slots();
            if (!"Semua".equalsIgnoreCase(filter)) {
                all = all.stream().filter(s -> s.getJenisKendaraan().equalsIgnoreCase(filter)).toList();
            }
            slotList.setAll(all);
            renderSlotCards();
        } catch (SQLException e) {
            tampilkanNotice("Gagal memfilter slot: " + e.getMessage());
        }
    }

    // ==========================================
    // LAPORAN & EKSPOR
    // ==========================================

    @FXML
    public void terapkanFilterLaporan() {
        String status = (cmbLaporanStatus != null && cmbLaporanStatus.getValue() != null) ? cmbLaporanStatus.getValue() : "Semua";
        LocalDate dari = (dpLaporanDari != null) ? dpLaporanDari.getValue() : null;
        LocalDate sampai = (dpLaporanSampai != null) ? dpLaporanSampai.getValue() : null;

        try {
            List<TransaksiParkir> all = parkirService.transactions();

            List<TransaksiParkir> filtered = all.stream().filter(t -> {
                if ("Sedang Parkir".equalsIgnoreCase(status) && "SELESAI".equalsIgnoreCase(t.getStatus())) return false;
                if ("Selesai".equalsIgnoreCase(status) && !"SELESAI".equalsIgnoreCase(t.getStatus())) return false;

                if (dari != null && t.getWaktuMasuk().toLocalDate().isBefore(dari)) return false;
                if (sampai != null && t.getWaktuMasuk().toLocalDate().isAfter(sampai)) return false;

                return true;
            }).toList();

            laporanList.setAll(filtered);

            // Update KPI Laporan
            int total = filtered.size();
            long aktif = filtered.stream().filter(t -> !"SELESAI".equalsIgnoreCase(t.getStatus())).count();
            long selesai = filtered.stream().filter(t -> "SELESAI".equalsIgnoreCase(t.getStatus())).count();
            double pendapatan = filtered.stream().mapToDouble(TransaksiParkir::getTotalBiaya).sum();

            if (lblLapTotalTx != null) lblLapTotalTx.setText(String.format("%02d", total));
            if (lblLapAktif != null) lblLapAktif.setText(String.format("%02d", aktif));
            if (lblLapSelesai != null) lblLapSelesai.setText(String.format("%02d", selesai));
            if (lblLapPendapatan != null) lblLapPendapatan.setText(new TransaksiParkir().formatRupiah(pendapatan));

        } catch (SQLException e) {
            tampilkanNotice("Gagal memuat laporan: " + e.getMessage());
        }
    }

    @FXML
    public void eksporCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Simpan Laporan CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File (*.csv)", "*.csv"));
        fileChooser.setInitialFileName("laporan-parkir.csv");

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("ID;Plat;Jenis;Slot;Masuk;Status;Pendapatan\n");
                for (TransaksiParkir t : laporanList) {
                    writer.write(String.format("%s;%s;%s;%s;%s;%s;%.0f\n",
                            t.getIdTransaksi(), t.getPlatNomor(), t.getJenisKendaraan(),
                            t.getKodeSlot(), t.getWaktuMasukFormatted(),
                            "SELESAI".equalsIgnoreCase(t.getStatus()) ? "Selesai" : "Sedang Parkir",
                            t.getTotalBiaya()
                    ));
                }
                tampilkanNotice("Laporan berhasil diekspor ke: " + file.getName());
            } catch (IOException e) {
                tampilkanNotice("Gagal mengekspor CSV: " + e.getMessage());
            }
        }
    }

    @FXML
    public void cetakLaporan() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================\n");
        sb.append("                       LAPORAN TRANSAKSI PARKIR KAMPUS                   \n");
        sb.append("========================================================================\n");
        sb.append(String.format("%-10s %-12s %-8s %-6s %-18s %-10s %-12s\n",
                "ID", "PLAT", "JENIS", "SLOT", "MASUK", "STATUS", "BIAYA"));
        sb.append("------------------------------------------------------------------------\n");

        for (TransaksiParkir t : laporanList) {
            sb.append(String.format("%-10s %-12s %-8s %-6s %-18s %-10s %-12s\n",
                    t.getIdTransaksi(), t.getPlatNomor(), t.getJenisKendaraan(),
                    t.getKodeSlot(), t.getWaktuMasukFormatted(), t.getStatus(),
                    t.getTotalBiayaFormatted()));
        }
        sb.append("========================================================================\n");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cetak Laporan");
        alert.setHeaderText("Preview Cetak Laporan");

        TextArea textArea = new TextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'IBM Plex Mono', 'Courier New', monospace; -fx-font-size: 12px;");
        textArea.setPrefWidth(640);
        textArea.setPrefHeight(400);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    // ==========================================
    // REFRESH & SINKRONISASI DATA
    // ==========================================

    @FXML
    public void cariTransaksi() {
        String keyword = (txtCari != null) ? txtCari.getText() : "";
        String status = (cmbFilterStatus != null && cmbFilterStatus.getValue() != null) ? cmbFilterStatus.getValue() : "Semua";

        try {
            List<TransaksiParkir> hasil = parkirService.searchTransactions(keyword, status);
            transaksiList.setAll(hasil);
            if (lblJumlahTransaksi != null) {
                lblJumlahTransaksi.setText(hasil.size() + " transaksi ditemukan");
            }
        } catch (SQLException e) {
            System.err.println("Gagal mencari transaksi: " + e.getMessage());
        }
    }

    public void muatSemuaData() {
        try {
            // 1. Muat Transaksi & Dashboard List
            cariTransaksi();
            List<TransaksiParkir> allTx = parkirService.transactions();
            dashboardList.setAll(allTx.subList(0, Math.min(5, allTx.size())));

            // 2. Muat Slot Parkir
            List<SlotParkir> allSlots = parkirService.slots();
            slotList.setAll(allSlots);
            renderSlotCards();

            // 3. Update KPI Statistik Dashboard
            updateStatistik(allTx, allSlots);

            // 4. Update Slot ComboBox Ketersediaan
            String jenisAktif = (cmbJenisKendaraan != null && cmbJenisKendaraan.getValue() != null) ? cmbJenisKendaraan.getValue() : "Roda2";
            updateSlotComboBox(jenisAktif);

        } catch (SQLException e) {
            System.err.println("Gagal memuat data: " + e.getMessage());
        }
    }

    private void updateSlotComboBox(String jenisKendaraan) {
        if (cmbSlotParkir == null) return;
        try {
            List<SlotParkir> tersedia = parkirService.availableSlots(jenisKendaraan);
            ObservableList<String> items = FXCollections.observableArrayList();
            for (SlotParkir s : tersedia) {
                items.add(s.getKodeSlot());
            }
            cmbSlotParkir.setItems(items);
            if (!items.isEmpty()) {
                cmbSlotParkir.setValue(items.get(0));
            } else {
                cmbSlotParkir.setValue(null);
                cmbSlotParkir.setPromptText("Slot Penuh");
            }
        } catch (SQLException e) {
            System.err.println("Gagal memuat slot tersedia: " + e.getMessage());
        }
    }

    private void renderSlotCards() {
        if (fpSlotGrid == null) return;
        fpSlotGrid.getChildren().clear();
        try {
            List<TransaksiParkir> activeTransactions = parkirService.transactions().stream()
                    .filter(tx -> !"SELESAI".equalsIgnoreCase(tx.getStatus())).toList();
            for (SlotParkir slot : slotList) {
                TransaksiParkir occupiedBy = activeTransactions.stream()
                        .filter(tx -> slot.getKodeSlot().equalsIgnoreCase(tx.getKodeSlot())).findFirst().orElse(null);
                boolean occupied = occupiedBy != null || !slot.isTersedia();

                VBox card = new VBox(0);
                card.getStyleClass().add("slot-card");
                if (occupied) card.getStyleClass().add("slot-card-occupied");
                card.setPrefWidth(164);
                card.setMinWidth(148);
                card.setMinHeight(166);

                HBox titleRow = new HBox();
                titleRow.setAlignment(Pos.CENTER_LEFT);
                Label code = new Label(slot.getKodeSlot());
                code.getStyleClass().add("slot-card-code");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                SVGPath mark = new SVGPath();
                mark.setContent("M1 1H11V11H1Z");
                mark.setStyle(occupied
                        ? "-fx-fill: white; -fx-stroke: white; -fx-stroke-width: 1;"
                        : "-fx-fill: transparent; -fx-stroke: #777777; -fx-stroke-width: 1;");
                mark.getStyleClass().add("slot-card-mark");
                titleRow.getChildren().addAll(code, spacer, mark);

                Label zone = new Label(slot.getZona() + " · " + labelJenis(slot.getJenisKendaraan()));
                zone.getStyleClass().add("slot-card-meta");
                Label vehicle = new Label(occupiedBy != null ? occupiedBy.getPlatNomor() : occupied ? "TERISI" : "TERSEDIA");
                vehicle.getStyleClass().add("slot-card-state");
                vehicle.setWrapText(false);
                vehicle.setMaxWidth(Double.MAX_VALUE);
                VBox details = new VBox(8, titleRow, zone, vehicle);
                details.setStyle("-fx-padding: 12;");

                HBox actions = new HBox(4);
                actions.getStyleClass().add("slot-card-actions");
                Button edit = new Button("UBAH");
                edit.getStyleClass().add("slot-card-action");
                HBox.setHgrow(edit, Priority.ALWAYS);
                edit.setMaxWidth(Double.MAX_VALUE);
                edit.setOnAction(event -> {
                    selectedSlot = slot;
                    if (cmbLantaiZona != null) cmbLantaiZona.setValue(slot.getZona());
                    if (txtKodeSlot != null) txtKodeSlot.setText(slot.getNomorSlot());
                    updateSlotZoneFields(slot.getLantaiZona());
                    if (lblSlotFormTitle != null) lblSlotFormTitle.setText("Ubah Slot " + slot.getKodeSlot());
                });
                Button delete = new Button("HAPUS");
                delete.getStyleClass().add("slot-card-action");
                delete.setOnAction(event -> {
                    selectedSlot = slot;
                    hapusSlotDipilih();
                });
                actions.getChildren().addAll(edit, delete);
                card.getChildren().addAll(details, actions);
                fpSlotGrid.getChildren().add(card);
            }
        } catch (SQLException e) {
            System.err.println("Gagal merender kartu slot: " + e.getMessage());
        }
    }

    private void updateStatistik(List<TransaksiParkir> allTx, List<SlotParkir> allSlots) {
        int totalMasuk = allTx.size();
        long aktif = allTx.stream().filter(t -> !"SELESAI".equalsIgnoreCase(t.getStatus())).count();
        long selesai = allTx.stream().filter(t -> "SELESAI".equalsIgnoreCase(t.getStatus())).count();
        int totalSlot = allSlots.size();
        long slotTersedia = allSlots.stream().filter(SlotParkir::isTersedia).count();
        long slotTerisi = totalSlot - slotTersedia;
        double revenue = allTx.stream().filter(t -> "SELESAI".equalsIgnoreCase(t.getStatus())).mapToDouble(TransaksiParkir::getTotalBiaya).sum();

        // Dashboard KPI
        if (lblStatMasuk != null) lblStatMasuk.setText(String.format("%02d", totalMasuk));
        if (lblStatAktif != null) lblStatAktif.setText(String.format("%02d", aktif));
        if (lblStatSlot != null) lblStatSlot.setText(String.format("%02d", slotTersedia));
        if (lblStatSlotNote != null) lblStatSlotNote.setText("Dari " + totalSlot + " slot parkir");
        if (lblStatPendapatan != null) lblStatPendapatan.setText(new TransaksiParkir().formatRupiah(revenue));
        if (lblStatPendNote != null) lblStatPendNote.setText(selesai + " transaksi selesai");

        // Slot KPI
        if (lblTotalSlot != null) lblTotalSlot.setText(String.format("%02d", totalSlot));
        if (lblSlotTersedia != null) lblSlotTersedia.setText(String.format("%02d", slotTersedia));
        if (lblSlotTerisi != null) lblSlotTerisi.setText(String.format("%02d", slotTerisi));

        // Slot Realtime Visual Per Zona
        updateZonaVisual("Roda2", allSlots, allTx, lblSlotRoda2, pbRoda2, lblSlotInfoRoda2);
        updateZonaVisual("Roda4", allSlots, allTx, lblSlotRoda4, pbRoda4, lblSlotInfoRoda4);
        updateZonaVisual("Truk", allSlots, allTx, lblSlotTruk, pbTruk, lblSlotInfoTruk);
    }

    private void updateZonaVisual(String kind, List<SlotParkir> slots, List<TransaksiParkir> txs,
                                  Label lblCount, ProgressBar pb, Label lblInfo) {
        long total = slots.stream().filter(s -> s.getJenisKendaraan().equalsIgnoreCase(kind)).count();
        long used = txs.stream().filter(t -> !"SELESAI".equalsIgnoreCase(t.getStatus()) && t.getJenisKendaraan().equalsIgnoreCase(kind)).count();
        long free = Math.max(0, total - used);

        if (lblCount != null) {
            lblCount.setText(free + " / " + total);
        }
        if (pb != null) {
            pb.setProgress((total > 0) ? (double) used / total : 0.0);
        }
        if (lblInfo != null) {
            lblInfo.setText(used + " terisi · " + free + " tersedia");
        }
    }

    // ==========================================
    // FLOATING NOTICE TOAST (App.tsx line 120)
    // ==========================================

    private void tampilkanNotice(String pesan) {
        if (boxNotice != null && lblNoticeText != null) {
            lblNoticeText.setText(pesan);
            boxNotice.setVisible(true);
            boxNotice.setManaged(true);

            // Auto dismiss notice setelah 4.5 detik
            new Thread(() -> {
                try {
                    Thread.sleep(4500);
                    Platform.runLater(this::tutupNotice);
                } catch (InterruptedException ignored) {}
            }).start();
        } else {
            System.out.println("[NOTICE] " + pesan);
        }
    }

    @FXML
    public void tutupNotice() {
        if (boxNotice != null) {
            boxNotice.setVisible(false);
            boxNotice.setManaged(false);
        }
    }

    // ==========================================
    // SESI PETUGAS
    // ==========================================

    public void setPetugasAktif(Petugas petugas) {
        this.petugasAktif = petugas;
        if (petugas != null) {
            String initial = hitungInitial(petugas.getNama());
            if (lblPetugasInitial != null) lblPetugasInitial.setText(initial);
            if (lblPetugasNama != null) lblPetugasNama.setText(petugas.getNama());
            if (lblPetugasRole != null) lblPetugasRole.setText(petugas.getRole() != null ? petugas.getRole() : "Petugas Parkir");
            if (lblPetugasWidgetInitial != null) lblPetugasWidgetInitial.setText(initial);
            if (lblPetugasWidgetNama != null) lblPetugasWidgetNama.setText(petugas.getNama());
        }
    }

    private String hitungInitial(String nama) {
        if (nama == null || nama.trim().isEmpty()) return "AP";
        String[] parts = nama.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }

    public boolean login(String username, String password) {
        try {
            Petugas p = parkirService.authenticate(username, password);
            if (p != null) {
                setPetugasAktif(p);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Kesalahan login: " + e.getMessage());
        }
        return false;
    }

    public Petugas getPetugasAktif() {
        return petugasAktif;
    }

}
