import database.DatabaseHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

/**
 * Kelas utama aplikasi JavaFX Sistem Manajemen Parkir.
 * Menginisialisasi database SQLite (parkir.db) dan menampilkan antarmuka login.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load the two typefaces used by the React reference before CSS is applied.
            loadBundledFont("/fonts/DMSans-Variable.ttf");
            loadBundledFont("/fonts/IBMPlexMono-Regular.ttf");
            loadBundledFont("/fonts/IBMPlexMono-Medium.ttf");

            // 1. Inisialisasi Database SQLite parkir.db & data awal
            System.out.println("Memulai inisialisasi database SQLite...");
            DatabaseHelper.initializeDatabase();

            // 2. Load LoginView.fxml sebagai tampilan awal
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoginView.fxml"));
            Parent root = loader.load();

            // 3. Tampilkan antarmuka JavaFX
            Scene scene = new Scene(root, 1200, 760);
            primaryStage.initStyle(StageStyle.DECORATED);
            primaryStage.setResizable(true);
            primaryStage.setFullScreen(false);
            primaryStage.setTitle("Sistem Manajemen Parkir Kampus — Login");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();
            primaryStage.setMaximized(true);

            System.out.println("Aplikasi Sistem Manajemen Parkir berhasil dijalankan.");
        } catch (IOException e) {
            System.err.println("Gagal memuat file FXML antarmuka: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Terjadi kesalahan saat memulai aplikasi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadBundledFont(String resource) throws IOException {
        try (var stream = getClass().getResourceAsStream(resource)) {
            if (stream == null || Font.loadFont(stream, 13) == null) {
                throw new IOException("Font tidak dapat dimuat: " + resource);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
