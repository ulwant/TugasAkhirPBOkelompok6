import controller.ParkirController;
import dao.ParkirDAO;
import database.DatabaseHelper;
import model.*;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestParkirApp {

    private static ParkirDAO dao;

    @BeforeAll
    public static void setup() {
        // Hapus file db lama jika ada agar pengujian bersih
        File dbFile = new File("parkir.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }

        // Inisialisasi DatabaseHelper
        DatabaseHelper.initializeDatabase();
        dao = new ParkirDAO();
    }

    @Test
    @Order(1)
    public void testDatabaseCreationAndSeeding() throws Exception {
        // Cek file parkir.db ada
        File dbFile = new File("parkir.db");
        assertTrue(dbFile.exists(), "File parkir.db harus otomatis dibuat!");

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement()) {

            // Cek akun petugas default: username admin password admin
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM petugas WHERE username = 'admin'")) {
                assertTrue(rs.next(), "Tabel petugas harus memiliki akun admin");
                assertEquals("admin", rs.getString("password"), "Password akun admin harus 'admin'");
                assertEquals("Admin", rs.getString("role"));
            }

            // Cek 5 slot parkir awal
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM slot_parkir")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "Tabel slot_parkir harus memiliki 5 slot awal");
            }

            // Cek slot parkir spesifik
            try (ResultSet rs = stmt.executeQuery("SELECT * FROM slot_parkir WHERE kode_slot = 'A-01'")) {
                assertTrue(rs.next());
                assertEquals("Lantai 1", rs.getString("lantai_zona"));
                assertEquals("Roda2", rs.getString("jenis_kendaraan"));
                assertEquals("TERSEDIA", rs.getString("status"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM slot_parkir WHERE kode_slot = 'C-01'")) {
                assertTrue(rs.next());
                assertEquals("Area Luar", rs.getString("lantai_zona"));
                assertEquals("Truk", rs.getString("jenis_kendaraan"));
                assertEquals("TERSEDIA", rs.getString("status"));
            }
        }
    }

    @Test
    @Order(2)
    public void testModelPerhitunganBiayaKendaraan() {
        // Roda 2
        Roda2 motor = new Roda2("B 1234 ABC", "Honda Beat", false);
        assertEquals(2000.0, motor.getTarifPerJam());
        // 2 jam non-member, tanpa helm: 2 * 2000 = 4000
        assertEquals(4000.0, motor.hitungBiaya(2, false));
        // 2 jam non-member, titip helm: (2 * 2000) + 2000 = 6000
        motor.setTitipHelm(true);
        assertEquals(6000.0, motor.hitungBiaya(2, false));
        // 2 jam member, titip helm: 6000 * 0.9 = 5400
        assertEquals(5400.0, motor.hitungBiaya(2, true));

        // Roda 4
        Roda4 mobil = new Roda4("B 5678 DEF", "Toyota Avanza");
        assertEquals(5000.0, mobil.getTarifPerJam());
        // 3 jam non-member: 3 * 5000 = 15000
        assertEquals(15000.0, mobil.hitungBiaya(3, false));
        // 3 jam member: 15000 * 0.9 = 13500
        assertEquals(13500.0, mobil.hitungBiaya(3, true));

        // Truk
        Truk truk = new Truk("B 9999 TRK", "Mitsubishi Fuso");
        assertEquals(10000.0, truk.getTarifPerJam());
        // 4 jam non-member: 4 * 10000 = 40000
        assertEquals(40000.0, truk.hitungBiaya(4, false));
        // 4 jam member: 40000 * 0.9 = 36000
        assertEquals(36000.0, truk.hitungBiaya(4, true));
    }

    @Test
    @Order(3)
    public void testPetugasAuthentication() throws Exception {
        Petugas p = dao.login("admin", "admin");
        assertNotNull(p, "Login admin/admin harus berhasil!");
        assertEquals("Administrator Parkir", p.getNama());

        Petugas salah = dao.login("admin", "wrongpassword");
        assertNull(salah, "Login dengan password salah harus gagal!");
    }

    @Test
    @Order(4)
    public void testTransaksiMasukDanStatusSlot() throws Exception {
        // Catat kendaraan masuk 1
        TransaksiParkir tx1 = new TransaksiParkir(
            dao.generateNextIdTransaksi(),
            "B 1111 AAA",
            "Honda Vario",
            "Roda2",
            "A-01",
            false,
            true, // titip helm
            LocalDateTime.now().minusHours(2),
            "admin"
        );
        dao.tambahTransaksi(tx1);

        // Verifikasi status slot A-01 menjadi TERISI
        SlotParkir s1 = dao.getSlotByKode("A-01");
        assertNotNull(s1);
        assertEquals("TERISI", s1.getStatus(), "Slot A-01 harus berubah menjadi TERISI!");

        // Cek bahwa transaksi tersimpan di database
        TransaksiParkir cekTx = dao.getTransaksiById(tx1.getIdTransaksi());
        assertNotNull(cekTx);
        assertEquals("B 1111 AAA", cekTx.getPlatNomor());
        assertEquals("PARKIR", cekTx.getStatus());

        // Catat kendaraan masuk 2 pada slot A-02
        TransaksiParkir tx2 = new TransaksiParkir(
            dao.generateNextIdTransaksi(),
            "B 2222 BBB",
            "Yamaha NMax",
            "Roda2",
            "A-02",
            true, // member
            false,
            LocalDateTime.now().minusHours(3),
            "admin"
        );
        dao.tambahTransaksi(tx2);

        SlotParkir s2 = dao.getSlotByKode("A-02");
        assertEquals("TERISI", s2.getStatus(), "Slot A-02 harus berubah menjadi TERISI!");
    }

    @Test
    @Order(5)
    public void testSlotPenuhException() {
        // Slot Roda2 hanya ada A-01 dan A-02, keduanya sekarang sudah terisi!
        // Jika mencoba menambahkan kendaraan Roda2 lagi tanpa slot atau pada slot yang terisi:
        TransaksiParkir tx3 = new TransaksiParkir(
            "PK-9999",
            "B 3333 CCC",
            "Suzuki Nex",
            "Roda2",
            null, // auto assign
            false,
            false,
            LocalDateTime.now(),
            "admin"
        );

        assertThrows(SlotPenuhException.class, () -> {
            dao.tambahTransaksi(tx3);
        }, "Harus melempar SlotPenuhException jika seluruh slot Roda2 sudah penuh!");
    }

    @Test
    @Order(6)
    public void testProsesKendaraanKeluarDanCetakStruk() throws Exception {
        // Proses checkout tx1
        TransaksiParkir tx1 = dao.getTransaksiAktif().stream()
            .filter(t -> t.getPlatNomor().equals("B 1111 AAA"))
            .findFirst()
            .orElseThrow();

        LocalDateTime waktuKeluar = LocalDateTime.now();
        tx1.selesaikanTransaksi(waktuKeluar);

        // Biaya: 2 jam Roda2 (2000/jam = 4000) + titip helm 2000 = 6000
        double expectedBiaya = 6000.0;
        assertEquals(expectedBiaya, tx1.getTotalBiaya(), 0.01);

        dao.prosesKendaraanKeluar(tx1.getIdTransaksi(), waktuKeluar, tx1.getTotalBiaya());

        // Cek status transaksi berubah jadi SELESAI
        TransaksiParkir updatedTx = dao.getTransaksiById(tx1.getIdTransaksi());
        assertEquals("SELESAI", updatedTx.getStatus());
        assertEquals(6000.0, updatedTx.getTotalBiaya(), 0.01);

        // Cek slot A-01 kembali TERSEDIA
        SlotParkir slotA1 = dao.getSlotByKode("A-01");
        assertEquals("TERSEDIA", slotA1.getStatus(), "Slot A-01 harus kembali TERSEDIA setelah kendaraan keluar!");

        // Cek struk tercetak rapi
        String struk = updatedTx.cetakStruk();
        assertNotNull(struk);
        assertTrue(struk.contains("PARKIR KAMPUS"));
        assertTrue(struk.contains("B 1111 AAA"));
        assertTrue(struk.contains("TOTAL BIAYA"));
        System.out.println("\n--- Hasil Cetak Struk Transaksi ---");
        System.out.println(struk);
    }

    @Test
    @Order(7)
    public void testSlotCRUD() throws Exception {
        // Tambah slot baru
        SlotParkir slotBaru = new SlotParkir("D-01", "Area Khusus", "Roda4", "TERSEDIA");
        dao.tambahSlot(slotBaru);

        SlotParkir cek = dao.getSlotByKode("D-01");
        assertNotNull(cek);
        assertEquals("Area Khusus", cek.getLantaiZona());

        // Update slot
        cek.setLantaiZona("Area VIP");
        dao.updateSlot(cek);
        assertEquals("Area VIP", dao.getSlotByKode("D-01").getLantaiZona());

        // Hapus slot
        dao.hapusSlot("D-01");
        assertNull(dao.getSlotByKode("D-01"), "Slot D-01 harus terhapus");
    }

    @Test
    @Order(8)
    public void testControllerInitialization() {
        ParkirController controller = new ParkirController(dao);
        assertNotNull(controller.getParkirDAO());
        boolean loginResult = controller.login("admin", "admin");
        assertTrue(loginResult, "Login controller admin/admin harus berhasil");
        assertNotNull(controller.getPetugasAktif());
        assertEquals("Administrator Parkir", controller.getPetugasAktif().getNama());
    }
}
