package dao;

import database.DatabaseHelper;
import model.Kendaraan;
import model.Petugas;
import model.SlotParkir;
import model.SlotPenuhException;
import model.TransaksiParkir;
import security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) untuk menangani operasi CRUD parkir,
 * manajemen slot, transaksi kendaraan masuk & keluar, serta autentikasi petugas.
 */
public class ParkirDAO {

    // ==========================================
    // OPERASI CRUD TRANSAKSI PARKIR
    // ==========================================

    /**
     * Mencatat kendaraan masuk (Create Transaksi).
     * Memeriksa ketersediaan slot, mengubah status slot menjadi 'TERISI',
     * dan menyimpan data transaksi ke database.
     *
     * @param transaksi objek TransaksiParkir
     * @throws SQLException jika terjadi kesalahan database
     * @throws SlotPenuhException jika tidak ada slot yang tersedia
     */
    public void tambahTransaksi(TransaksiParkir transaksi) throws SQLException, SlotPenuhException {
        if (transaksi == null) throw new IllegalArgumentException("Data transaksi wajib diisi.");
        if (!"PARKIR".equals(transaksi.getStatus()) || transaksi.getWaktuKeluar() != null)
            throw new IllegalArgumentException("Transaksi masuk baru harus berstatus PARKIR dan belum memiliki waktu keluar.");
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                if (isPlatSedangParkir(conn, transaksi.getPlatNomor(), null))
                    throw new IllegalArgumentException("Kendaraan dengan plat nomor " + transaksi.getPlatNomor() + " sudah terdaftar sedang parkir.");

                String requestedCode = transaksi.getKodeSlot();
                SlotParkir slot = requestedCode == null || requestedCode.isBlank()
                        ? firstAvailableSlot(conn, transaksi.getJenisKendaraan())
                        : getSlotByKode(conn, requestedCode);
                if (slot == null || !slot.isTersedia() || !slot.getJenisKendaraan().equals(transaksi.getJenisKendaraan()))
                    throw new SlotPenuhException(transaksi.getJenisKendaraan(), "Slot pilihan tidak tersedia atau jenisnya tidak cocok");

                try (PreparedStatement occupy = conn.prepareStatement(
                        "UPDATE slot_parkir SET status='TERISI' WHERE kode_slot=? AND status='TERSEDIA' AND jenis_kendaraan=?")) {
                    occupy.setString(1, slot.getKodeSlot());
                    occupy.setString(2, transaksi.getJenisKendaraan());
                    if (occupy.executeUpdate() != 1) throw new SlotPenuhException(transaksi.getJenisKendaraan(), slot.getLantaiZona());
                }

                if (transaksi.getIdTransaksi() == null || transaksi.getIdTransaksi().isBlank())
                    transaksi.setIdTransaksi(generateNextIdTransaksi(conn));
                transaksi.setKodeSlot(slot.getKodeSlot());
                String sql = "INSERT INTO transaksi_parkir (id_transaksi, plat_nomor, plat_normalized, merk_kendaraan, jenis_kendaraan, kode_slot, is_member, titip_helm, waktu_masuk, waktu_keluar, total_biaya, status, petugas) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0, 'PARKIR', ?)";
                try (PreparedStatement insert = conn.prepareStatement(sql)) {
                    insert.setString(1, transaksi.getIdTransaksi());
                    insert.setString(2, transaksi.getPlatNomor());
                    insert.setString(3, DatabaseHelper.normalizePlate(transaksi.getPlatNomor()));
                    insert.setString(4, transaksi.getMerkKendaraan());
                    insert.setString(5, transaksi.getJenisKendaraan());
                    insert.setString(6, slot.getKodeSlot());
                    insert.setInt(7, transaksi.isMember() ? 1 : 0);
                    insert.setInt(8, transaksi.isTitipHelm() ? 1 : 0);
                    insert.setString(9, transaksi.getWaktuMasuk().format(TransaksiParkir.FORMATTER));
                    insert.setString(10, transaksi.getPetugas() == null ? "" : transaksi.getPetugas());
                    insert.executeUpdate();
                }
                conn.commit();
            } catch (SlotPenuhException | SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof SQLException sql && isActivePlateConstraint(sql))
                    throw new IllegalArgumentException("Plat nomor ini sudah memiliki transaksi aktif.", sql);
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Memperbarui informasi transaksi yang sedang berjalan (Update Transaksi).
     */
    public void updateTransaksi(TransaksiParkir transaksi) throws SQLException {
        if (transaksi == null || transaksi.getIdTransaksi() == null || transaksi.getIdTransaksi().isBlank())
            throw new IllegalArgumentException("Transaksi yang akan diubah tidak valid.");
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                TransaksiParkir old = getTransaksiById(conn, transaksi.getIdTransaksi());
                if (old == null || !"PARKIR".equals(old.getStatus()))
                    throw new IllegalStateException("Hanya transaksi yang masih parkir yang dapat diubah.");
                if (isPlatSedangParkir(conn, transaksi.getPlatNomor(), transaksi.getIdTransaksi()))
                    throw new IllegalArgumentException("Plat nomor " + transaksi.getPlatNomor() + " sudah digunakan pada transaksi aktif lainnya.");

                SlotParkir target = getSlotByKode(conn, transaksi.getKodeSlot());
                if (target == null || !target.getJenisKendaraan().equals(transaksi.getJenisKendaraan()))
                    throw new IllegalArgumentException("Jenis kendaraan tidak cocok dengan slot tujuan.");
                boolean pindahSlot = !old.getKodeSlot().equals(transaksi.getKodeSlot());
                if (pindahSlot) {
                    try (PreparedStatement occupy = conn.prepareStatement(
                            "UPDATE slot_parkir SET status='TERISI' WHERE kode_slot=? AND status='TERSEDIA' AND jenis_kendaraan=?")) {
                        occupy.setString(1, target.getKodeSlot());
                        occupy.setString(2, transaksi.getJenisKendaraan());
                        if (occupy.executeUpdate() != 1) throw new IllegalStateException("Slot tujuan sudah terisi atau tidak tersedia.");
                    }
                    if (old.getKodeSlot() != null && !old.getKodeSlot().isBlank()) {
                        try (PreparedStatement release = conn.prepareStatement(
                                "UPDATE slot_parkir SET status='TERSEDIA' WHERE kode_slot=? AND status='TERISI'")) {
                            release.setString(1, old.getKodeSlot());
                            if (release.executeUpdate() != 1) throw new IllegalStateException("Status slot lama tidak konsisten; perubahan dibatalkan.");
                        }
                    }
                } else {
                    try (PreparedStatement check = conn.prepareStatement(
                            "SELECT COUNT(*) FROM slot_parkir s JOIN transaksi_parkir t ON t.kode_slot=s.kode_slot WHERE s.kode_slot=? AND s.status='TERISI' AND t.id_transaksi=? AND t.status='PARKIR'")) {
                        check.setString(1, target.getKodeSlot());
                        check.setString(2, transaksi.getIdTransaksi());
                        try (ResultSet rs = check.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("Slot transaksi tidak konsisten.");
                        }
                    }
                }

                String sql = "UPDATE transaksi_parkir SET plat_nomor=?, plat_normalized=?, merk_kendaraan=?, jenis_kendaraan=?, kode_slot=?, is_member=?, titip_helm=?, waktu_masuk=? WHERE id_transaksi=? AND status='PARKIR'";
                try (PreparedStatement update = conn.prepareStatement(sql)) {
                    update.setString(1, transaksi.getPlatNomor());
                    update.setString(2, DatabaseHelper.normalizePlate(transaksi.getPlatNomor()));
                    update.setString(3, transaksi.getMerkKendaraan());
                    update.setString(4, transaksi.getJenisKendaraan());
                    update.setString(5, transaksi.getKodeSlot());
                    update.setInt(6, transaksi.isMember() ? 1 : 0);
                    update.setInt(7, transaksi.isTitipHelm() ? 1 : 0);
                    update.setString(8, transaksi.getWaktuMasuk().format(TransaksiParkir.FORMATTER));
                    update.setString(9, transaksi.getIdTransaksi());
                    if (update.executeUpdate() != 1) throw new IllegalStateException("Transaksi tidak lagi aktif.");
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof SQLException sql && isActivePlateConstraint(sql))
                    throw new IllegalArgumentException("Plat nomor ini sudah memiliki transaksi aktif.", sql);
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Memproses checkout kendaraan keluar:
     * - Mengisi waktu_keluar
     * - Mengisi total_biaya
     * - Mengubah status transaksi menjadi 'SELESAI'
     * - Mengubah status slot_parkir kembali menjadi 'TERSEDIA'
     */
    public TransaksiParkir prosesKendaraanKeluar(String idTransaksi, LocalDateTime waktuKeluar) throws SQLException {
        if (waktuKeluar == null) throw new IllegalArgumentException("Waktu keluar wajib diisi.");
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                TransaksiParkir tx = getTransaksiById(conn, idTransaksi);
                if (tx == null) throw new IllegalArgumentException("Transaksi dengan ID " + idTransaksi + " tidak ditemukan.");
                if (!"PARKIR".equals(tx.getStatus())) throw new IllegalStateException("Transaksi ini sudah selesai.");
                tx.selesaikanTransaksi(waktuKeluar);

                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE transaksi_parkir SET waktu_keluar=?, total_biaya=?, status='SELESAI' WHERE id_transaksi=? AND status='PARKIR'")) {
                    update.setString(1, waktuKeluar.format(TransaksiParkir.FORMATTER));
                    update.setDouble(2, tx.getTotalBiaya());
                    update.setString(3, idTransaksi);
                    if (update.executeUpdate() != 1) throw new IllegalStateException("Transaksi tidak lagi aktif.");
                }
                if (tx.getKodeSlot() != null && !tx.getKodeSlot().isBlank()) {
                    try (PreparedStatement free = conn.prepareStatement(
                            "UPDATE slot_parkir SET status='TERSEDIA' WHERE kode_slot=? AND status='TERISI'")) {
                        free.setString(1, tx.getKodeSlot());
                        if (free.executeUpdate() != 1) throw new IllegalStateException("Status slot tidak konsisten; checkout dibatalkan.");
                    }
                }
                conn.commit();
                return tx;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Menghapus transaksi berdasarkan ID (Delete Transaksi).
     * Jika transaksi yang dihapus masih berstatus 'PARKIR', slot yang bersangkutan dibebaskan.
     */
    public void hapusTransaksi(String idTransaksi) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                TransaksiParkir tx = getTransaksiById(conn, idTransaksi);
                if (tx != null && "PARKIR".equals(tx.getStatus()) && tx.getKodeSlot() != null && !tx.getKodeSlot().isBlank()) {
                    try (PreparedStatement slot = conn.prepareStatement("UPDATE slot_parkir SET status='TERSEDIA' WHERE kode_slot=? AND status='TERISI'")) {
                        slot.setString(1, tx.getKodeSlot());
                        if (slot.executeUpdate() != 1) throw new IllegalStateException("Status slot tidak konsisten; transaksi tidak dihapus.");
                    }
                }
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM transaksi_parkir WHERE id_transaksi=?")) {
                    delete.setString(1, idTransaksi);
                    delete.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Mengambil seluruh daftar transaksi (Read Transaksi).
     */
    public List<TransaksiParkir> getAllTransaksi() throws SQLException {
        List<TransaksiParkir> list = new ArrayList<>();
        String sql = "SELECT * FROM transaksi_parkir ORDER BY rowid DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToTransaksi(rs));
            }
        }
        return list;
    }

    /**
     * Mengambil daftar transaksi yang sedang aktif (status = 'PARKIR').
     */
    public List<TransaksiParkir> getTransaksiAktif() throws SQLException {
        List<TransaksiParkir> list = new ArrayList<>();
        String sql = "SELECT * FROM transaksi_parkir WHERE status = 'PARKIR' ORDER BY waktu_masuk DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToTransaksi(rs));
            }
        }
        return list;
    }

    /**
     * Mencari transaksi berdasarkan ID.
     */
    public TransaksiParkir getTransaksiById(String idTransaksi) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            return getTransaksiById(conn, idTransaksi);
        }
    }

    private TransaksiParkir getTransaksiById(Connection conn, String idTransaksi) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM transaksi_parkir WHERE id_transaksi = ?")) {
            stmt.setString(1, idTransaksi);
            try (ResultSet rs = stmt.executeQuery()) { return rs.next() ? mapResultSetToTransaksi(rs) : null; }
        }
    }

    /**
     * Memeriksa apakah plat nomor tertentu sedang parkir.
     */
    public boolean isPlatSedangParkir(String platNomor, String excludeId) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            return isPlatSedangParkir(conn, platNomor, excludeId);
        }
    }

    private boolean isPlatSedangParkir(Connection conn, String platNomor, String excludeId) throws SQLException {
        String cleanPlat = DatabaseHelper.normalizePlate(platNomor);
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id_transaksi, plat_nomor FROM transaksi_parkir WHERE status='PARKIR' AND id_transaksi<>COALESCE(?, '')")) {
            stmt.setString(1, excludeId);
            try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String existingPlat = DatabaseHelper.normalizePlate(rs.getString("plat_nomor"));
                if (cleanPlat.equals(existingPlat)) {
                    return true;
                }
            }
            }
        }
        return false;
    }

    /**
     * Filter dan pencarian data transaksi berdasarkan kata kunci dan status.
     */
    public List<TransaksiParkir> cariTransaksi(String keyword, String statusFilter) throws SQLException {
        List<TransaksiParkir> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transaksi_parkir WHERE 1=1 ");

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (plat_nomor LIKE ? OR merk_kendaraan LIKE ? OR id_transaksi LIKE ?) ");
        }
        if (statusFilter != null && !statusFilter.equalsIgnoreCase("Semua")) {
            if ("Sedang Parkir".equalsIgnoreCase(statusFilter) || "PARKIR".equalsIgnoreCase(statusFilter)) {
                sql.append("AND status = 'PARKIR' ");
            } else if ("Selesai".equalsIgnoreCase(statusFilter) || "SELESAI".equalsIgnoreCase(statusFilter)) {
                sql.append("AND status = 'SELESAI' ");
            }
        }
        sql.append("ORDER BY rowid DESC");

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim() + "%";
                pstmt.setString(paramIndex++, pattern);
                pstmt.setString(paramIndex++, pattern);
                pstmt.setString(paramIndex++, pattern);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaksi(rs));
                }
            }
        }
        return list;
    }

    /**
     * Menghasilkan ID Transaksi berikutnya secara berurutan, misal: PK-0001, PK-0002.
     */
    public synchronized String generateNextIdTransaksi() throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            return generateNextIdTransaksi(conn);
        }
    }

    private String generateNextIdTransaksi(Connection conn) throws SQLException {
        String sql = "SELECT id_transaksi FROM transaksi_parkir";
        int maxId = 0;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id_transaksi");
                if (id != null && id.startsWith("PK-")) {
                    try {
                        int num = Integer.parseInt(id.substring(3));
                        if (num > maxId) {
                            maxId = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return String.format("PK-%04d", maxId + 1);
    }

    // ==========================================
    // OPERASI CRUD SLOT PARKIR
    // ==========================================

    /**
     * Mengambil seluruh data slot parkir.
     */
    public List<SlotParkir> getAllSlots() throws SQLException {
        List<SlotParkir> list = new ArrayList<>();
        String sql = "SELECT * FROM slot_parkir ORDER BY kode_slot ASC";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new SlotParkir(
                    rs.getString("kode_slot"),
                    rs.getString("lantai_zona"),
                    rs.getString("jenis_kendaraan"),
                    rs.getString("status")
                ));
            }
        }
        return list;
    }

    /**
     * Mengambil slot parkir yang berstatus 'TERSEDIA' untuk jenis kendaraan tertentu.
     */
    public List<SlotParkir> getSlotTersedia(String jenisKendaraan) throws SQLException {
        List<SlotParkir> list = new ArrayList<>();
        String sql = "SELECT * FROM slot_parkir WHERE status = 'TERSEDIA' AND jenis_kendaraan = ? ORDER BY kode_slot ASC";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, jenisKendaraan);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new SlotParkir(
                        rs.getString("kode_slot"),
                        rs.getString("lantai_zona"),
                        rs.getString("jenis_kendaraan"),
                        rs.getString("status")
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Mengambil data slot berdasarkan kode slot.
     */
    public SlotParkir getSlotByKode(String kodeSlot) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            return getSlotByKode(conn, kodeSlot);
        }
    }

    private SlotParkir getSlotByKode(Connection conn, String kodeSlot) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM slot_parkir WHERE kode_slot = ?")) {
            stmt.setString(1, kodeSlot);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? new SlotParkir(rs.getString("kode_slot"), rs.getString("lantai_zona"),
                        rs.getString("jenis_kendaraan"), rs.getString("status")) : null;
            }
        }
    }

    private SlotParkir firstAvailableSlot(Connection conn, String jenis) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM slot_parkir WHERE status='TERSEDIA' AND jenis_kendaraan=? ORDER BY kode_slot LIMIT 1")) {
            stmt.setString(1, jenis);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? new SlotParkir(rs.getString("kode_slot"), rs.getString("lantai_zona"),
                        rs.getString("jenis_kendaraan"), rs.getString("status")) : null;
            }
        }
    }

    private boolean isActivePlateConstraint(SQLException error) {
        return error.getMessage() != null && (error.getMessage().contains("ux_transaksi_active_plate")
                || error.getMessage().contains("plat_normalized"));
    }

    private boolean isSlotCodeConstraint(SQLException error) {
        return error.getMessage() != null && (error.getMessage().contains("slot_parkir.kode_slot")
                || error.getMessage().contains("UNIQUE constraint failed"));
    }

    /**
     * Menambahkan slot parkir baru.
     */
    public void tambahSlot(SlotParkir slot) throws SQLException {
        if (slot == null || slot.getKodeSlot() == null || slot.getKodeSlot().isBlank())
            throw new IllegalArgumentException("Nomor slot wajib diisi.");
        String zona = SlotParkir.normalisasiZona(slot.getLantaiZona());
        String jenis = SlotParkir.jenisUntukZona(zona);
        if (!slot.getKodeSlot().matches(SlotParkir.prefixUntukZona(zona) + "-\\d{2,}"))
            throw new IllegalArgumentException("Kode slot tidak sesuai dengan zona yang dipilih.");
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                slot.setLantaiZona(zona);
                slot.setJenisKendaraan(jenis);
                String sql = "INSERT INTO slot_parkir (kode_slot, lantai_zona, jenis_kendaraan, status) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, slot.getKodeSlot());
                    pstmt.setString(2, slot.getLantaiZona());
                    pstmt.setString(3, slot.getJenisKendaraan());
                    pstmt.setString(4, slot.getStatus());
                    pstmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof SQLException sql && isSlotCodeConstraint(sql))
                    throw new IllegalArgumentException("Nomor slot " + slot.getKodeSlot() + " sudah digunakan.", sql);
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Memperbarui informasi slot parkir.
     */
    public void updateSlot(SlotParkir slot) throws SQLException {
        updateSlot(slot, slot.getKodeSlot());
    }

    public void updateSlot(SlotParkir slot, String previousCode) throws SQLException {
        updateSlot(previousCode, slot);
    }

    public void updateSlot(String kodeSlotLama, SlotParkir slotBaru) throws SQLException {
        if (slotBaru == null || slotBaru.getKodeSlot() == null || slotBaru.getKodeSlot().isBlank())
            throw new IllegalArgumentException("Nomor slot wajib diisi.");
        String oldCode = kodeSlotLama;
        SlotParkir slot = slotBaru;
        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                SlotParkir current = getSlotByKode(conn, oldCode);
                if (current == null) throw new SQLException("Slot " + oldCode + " tidak ditemukan.");
                String zona = SlotParkir.normalisasiZona(slot.getLantaiZona());
                String jenis = SlotParkir.jenisUntukZona(zona);
                if (!current.isTersedia() && !zona.equals(current.getLantaiZona()))
                    throw new IllegalStateException("Zona slot hanya dapat diubah saat slot kosong.");
                String newCode = slot.getKodeSlot();
                if (!newCode.matches(SlotParkir.prefixUntukZona(zona) + "-\\d{2,}"))
                    throw new IllegalArgumentException("Kode slot tidak sesuai dengan zona yang dipilih.");
                slot.setLantaiZona(zona);
                slot.setJenisKendaraan(jenis);
                slot.setKodeSlot(newCode);

                String sql = "UPDATE slot_parkir SET kode_slot = ?, lantai_zona = ?, jenis_kendaraan = ?, status = ? WHERE kode_slot = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, newCode);
                    pstmt.setString(2, zona);
                    pstmt.setString(3, jenis);
                    pstmt.setString(4, slot.getStatus());
                    pstmt.setString(5, oldCode);
                    if (pstmt.executeUpdate() != 1) throw new IllegalStateException("Slot tidak lagi tersedia untuk diubah.");
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                if (e instanceof SQLException sql && isSlotCodeConstraint(sql))
                    throw new IllegalArgumentException("Nomor slot " + slot.getKodeSlot() + " sudah digunakan.", sql);
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Memperbarui status slot parkir (TERSEDIA / TERISI).
     */
    public void updateStatusSlot(String kodeSlot, String statusBaru) throws SQLException {
        if (!"TERSEDIA".equalsIgnoreCase(statusBaru) && !"TERISI".equalsIgnoreCase(statusBaru))
            throw new IllegalArgumentException("Status slot hanya boleh TERSEDIA atau TERISI.");
        String sql = "UPDATE slot_parkir SET status = ? WHERE kode_slot = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, statusBaru);
            pstmt.setString(2, kodeSlot);
            pstmt.executeUpdate();
        }
    }

    /** Menghapus slot tanpa menghapus transaksi; FK mengosongkan referensi slot di riwayat. */
    public void hapusSlot(String kodeSlot) throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection()) {
            String sql = "DELETE FROM slot_parkir WHERE kode_slot = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, kodeSlot);
                if (pstmt.executeUpdate() != 1) throw new IllegalStateException("Slot " + kodeSlot + " tidak ditemukan.");
            }
        }
    }

    // ==========================================
    // AUTENTIKASI PETUGAS
    // ==========================================

    /**
     * Memverifikasi login petugas dengan username dan password.
     */
    public Petugas login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) return null;
        String sql = "SELECT * FROM petugas WHERE username = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && PasswordHasher.verify(password, rs.getString("password"))) {
                    return new Petugas(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("nama"),
                        rs.getString("role")
                    );
                }
            }
        }
        return null;
    }

    public boolean hasAnyPetugas() throws SQLException {
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT EXISTS(SELECT 1 FROM petugas)")) {
            return rs.next() && rs.getInt(1) != 0;
        }
    }

    /** Atomic first-account setup; succeeds only while the operator table is empty. */
    public void createInitialPetugas(String username, String password, String nama) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanName = nama == null ? "" : nama.trim();
        if (cleanUsername.length() < 3) throw new IllegalArgumentException("Username minimal 3 karakter.");
        if (cleanName.isEmpty()) throw new IllegalArgumentException("Nama petugas wajib diisi.");
        String passwordHash = PasswordHasher.hash(password);

        try (Connection conn = DatabaseHelper.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (Statement claim = conn.createStatement()) {
                    if (claim.executeUpdate("UPDATE app_setup SET complete = 1 WHERE id = 1 AND complete = 0") != 1) {
                        throw new IllegalStateException("Setup awal sudah digunakan atau akun petugas sudah tersedia.");
                    }
                }
                try (Statement check = conn.createStatement(); ResultSet rs = check.executeQuery("SELECT EXISTS(SELECT 1 FROM petugas)")) {
                    if (rs.next() && rs.getInt(1) != 0)
                        throw new IllegalStateException("Akun petugas sudah tersedia. Setup awal hanya dapat dilakukan sekali.");
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO petugas (username, password, nama, role) VALUES (?, ?, ?, 'Admin')")) {
                    insert.setString(1, cleanUsername);
                    insert.setString(2, passwordHash);
                    insert.setString(3, cleanName);
                    insert.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    /**
     * Mengambil data petugas berdasarkan username.
     */
    public Petugas getPetugasByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM petugas WHERE username = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Petugas(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("nama"),
                        rs.getString("role")
                    );
                }
            }
        }
        return null;
    }

    // ==========================================
    // STATISTIK DASHBOARD
    // ==========================================

    public int getTotalKendaraanMasuk() throws SQLException {
        String sql = "SELECT COUNT(*) FROM transaksi_parkir";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getTotalKendaraanAktif() throws SQLException {
        String sql = "SELECT COUNT(*) FROM transaksi_parkir WHERE status = 'PARKIR'";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getTotalSlotTersedia() throws SQLException {
        String sql = "SELECT COUNT(*) FROM slot_parkir WHERE status = 'TERSEDIA'";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public double getTotalPendapatan() throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_biaya), 0.0) FROM transaksi_parkir WHERE status = 'SELESAI'";
        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    // ==========================================
    // HELPER MAPPING
    // ==========================================

    private TransaksiParkir mapResultSetToTransaksi(ResultSet rs) throws SQLException {
        TransaksiParkir t = new TransaksiParkir();
        t.setIdTransaksi(rs.getString("id_transaksi"));
        t.setPlatNomor(rs.getString("plat_nomor"));
        t.setMerkKendaraan(rs.getString("merk_kendaraan"));
        t.setJenisKendaraan(rs.getString("jenis_kendaraan"));
        t.setKodeSlot(rs.getString("kode_slot"));
        t.setMember(rs.getInt("is_member") == 1);
        t.setTitipHelm(rs.getInt("titip_helm") == 1);

        String masukStr = rs.getString("waktu_masuk");
        if (masukStr != null && !masukStr.isEmpty()) {
            try {
                t.setWaktuMasuk(LocalDateTime.parse(masukStr, TransaksiParkir.FORMATTER));
            } catch (Exception e) {
                t.setWaktuMasuk(LocalDateTime.now());
            }
        }

        String keluarStr = rs.getString("waktu_keluar");
        if (keluarStr != null && !keluarStr.isEmpty()) {
            try {
                t.setWaktuKeluar(LocalDateTime.parse(keluarStr, TransaksiParkir.FORMATTER));
            } catch (Exception e) {
                t.setWaktuKeluar(null);
            }
        }

        t.setTotalBiaya(rs.getDouble("total_biaya"));
        t.setStatus(rs.getString("status"));
        t.setPetugas(rs.getString("petugas"));
        t.syncKendaraan();
        return t;
    }
}
