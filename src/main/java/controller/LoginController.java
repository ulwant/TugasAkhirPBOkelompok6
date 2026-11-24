package controller;

import service.ParkirService;
import dao.ParkirDAO;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Petugas;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;

/**
 * Controller untuk antarmuka Login (LoginView.fxml).
 * Mengatur autentikasi petugas dan transisi ke dashboard utama.
 */
public class LoginController implements Initializable {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Button btnInitialSetup;
    @FXML private Label lblNotice;
    @FXML private Label lblLoginTitle;
    @FXML private Label lblLoginSubtitle;
    @FXML private VBox boxInitialName;
    @FXML private TextField txtInitialName;

    private final ParkirService parkirService = new ParkirService(new ParkirDAO());

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (lblNotice != null) {
            lblNotice.setText("");
        }
        if (txtUsername != null) Platform.runLater(() -> txtUsername.requestFocus());
        try {
            boolean setup = parkirService.needsInitialAccount();
            if (btnLogin != null) {
                btnLogin.setVisible(!setup);
                btnLogin.setManaged(!setup);
            }
            if (btnInitialSetup != null) {
                btnInitialSetup.setVisible(setup);
                btnInitialSetup.setManaged(setup);
            }
            if (boxInitialName != null) {
                boxInitialName.setVisible(setup);
                boxInitialName.setManaged(setup);
            }
            if (setup) {
                if (lblLoginTitle != null) lblLoginTitle.setText("Setup akun petugas");
                if (lblLoginSubtitle != null) lblLoginSubtitle.setText("Buat akun admin pertama. Password minimal 12 karakter.");
            }
        } catch (SQLException e) {
            setNotice("Database tidak dapat dibaca: " + e.getMessage());
        }
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = (txtUsername != null) ? txtUsername.getText().trim() : "";
        String password = (txtPassword != null) ? txtPassword.getText() : "";

        if (username.isEmpty() || password.isEmpty()) {
            setNotice("Harap isi username dan password.");
            return;
        }

        try {
            Petugas petugas = parkirService.authenticate(username, password);

            if (petugas != null) {
                // Berhasil login, buka MainView.fxml
                bukaMainView(event, petugas);
            } else {
                setNotice("Username atau password tidak sesuai.");
            }
        } catch (SQLException e) {
            setNotice("Kesalahan database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void handleInitialSetup(ActionEvent event) {
        String username = txtUsername == null ? "" : txtUsername.getText().trim();
        String password = txtPassword == null ? "" : txtPassword.getText();
        String name = txtInitialName == null ? "" : txtInitialName.getText().trim();
        try {
            parkirService.createInitialAccount(username, password, name);
            Petugas created = parkirService.authenticate(username, password);
            if (created == null) throw new IllegalStateException("Akun dibuat, tetapi autentikasi ulang gagal.");
            bukaMainView(event, created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            setNotice(e.getMessage());
        } catch (SQLException e) {
            setNotice("Gagal membuat akun awal: " + e.getMessage());
        }
    }

    private void bukaMainView(ActionEvent event, Petugas petugas) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/MainView.fxml"));
            Parent root = loader.load();

            // Set petugas aktif di controller utama
            Object ctrl = loader.getController();
            if (ctrl instanceof ParkirController pc) {
                pc.setPetugasAktif(petugas);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1280, 800);
            stage.setResizable(true);
            stage.setFullScreen(false);
            stage.setTitle("Sistem Manajemen Parkir Kampus");
            stage.setScene(scene);
            stage.setMinWidth(980);
            stage.setMinHeight(600);
            stage.show();
            stage.setMaximized(true);

        } catch (IOException e) {
            setNotice("Gagal memuat antarmuka utama: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setNotice(String text) {
        if (lblNotice != null) {
            lblNotice.setText(text);
        }
    }
}
