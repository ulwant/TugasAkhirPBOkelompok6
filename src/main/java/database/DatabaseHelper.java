package database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import security.PasswordHasher;

/**
 * Helper class untuk mengelola koneksi database SQLite (parkir.db)
 * dan otomatis menginisialisasi tabel-tabel serta data awal jika belum tersedia.
 */
public class DatabaseHelper {

    private static final String DB_FILE_NAME = "parkir.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE_NAME;
    private static boolean initialized = false;

    static {
        try {
            // Memastikan driver SQLite JDBC terdaftar
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Driver SQLite JDBC tidak ditemukan: " + e.getMessage());
        }
    }

    /**
     * Mendapatkan koneksi ke database SQLite parkir.db.
     * Otomatis memicu inisialisasi tabel dan data awal pada pemanggilan pertama.
     *
     * @return Connection objek koneksi database
     * @throws SQLException jika terjadi kegagalan koneksi
     */
    public static synchronized Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try {
            if (!initialized) {
                initializeDatabase(conn);
                initialized = true;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
            return conn;
        } catch (SQLException | RuntimeException e) {
            try { conn.close(); } catch (SQLException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
    }

    /**
     * Menginisialisasi file database dan seluruh tabel:
     * 1. petugas (akun pertama dibuat melalui setup aman di layar login)
     * 2. slot_parkir (default 5 slot awal)
     * 3. transaksi_parkir
     */
    public static synchronized void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            initializeDatabase(conn);
            initialized = true;
        } catch (SQLException e) {
            System.err.println("Gagal menginisialisasi database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inisialisasi tabel dan data default menggunakan koneksi yang diberikan.
     */
    private static void initializeDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Aktifkan foreign keys pada SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Buat Tabel petugas
            String sqlCreatePetugas = """
                CREATE TABLE IF NOT EXISTS petugas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    
                    nama TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'Petugas'
                );
            """;
            stmt.execute(sqlCreatePetugas);

            stmt.execute("CREATE TABLE IF NOT EXISTS app_setup (id INTEGER PRIMARY KEY CHECK (id = 1), complete INTEGER NOT NULL CHECK (complete IN (0,1)))");
            stmt.execute("INSERT OR IGNORE INTO app_setup (id, complete) VALUES (1, 0)");

            // 2. Buat Tabel slot_parkir
            String sqlCreateSlot = """
                CREATE TABLE IF NOT EXISTS slot_parkir (
                    kode_slot TEXT PRIMARY KEY,
                    lantai_zona TEXT NOT NULL,
                    jenis_kendaraan TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'TERSEDIA'
                );
            """;
            stmt.execute(sqlCreateSlot);

            // 3. Buat Tabel transaksi_parkir
            String sqlCreateTransaksi = """
                CREATE TABLE IF NOT EXISTS transaksi_parkir (
                    id_transaksi TEXT PRIMARY KEY,
                    plat_nomor TEXT NOT NULL,
                    merk_kendaraan TEXT NOT NULL,
                    jenis_kendaraan TEXT NOT NULL,
                    kode_slot TEXT,
                    is_member INTEGER NOT NULL DEFAULT 0,
                    titip_helm INTEGER NOT NULL DEFAULT 0,
                    waktu_masuk TEXT NOT NULL,
                    waktu_keluar TEXT,
                    total_biaya REAL NOT NULL DEFAULT 0.0,
                    status TEXT NOT NULL DEFAULT 'PARKIR',
                    petugas TEXT,
                    FOREIGN KEY (kode_slot) REFERENCES slot_parkir(kode_slot) ON UPDATE CASCADE ON DELETE SET NULL
                );
            """;
            stmt.execute(sqlCreateTransaksi);

            migratePetugasCredentials(conn);
            stmt.execute("UPDATE app_setup SET complete = 1 WHERE EXISTS (SELECT 1 FROM petugas)");
            migrateActivePlateIndex(conn);
            migrateTransactionSlotForeignKey(conn);

            // Seed 5 slot parkir awal
            seedDefaultSlotParkir(conn);

            // Terapkan prefix kode, zona, dan jenis kendaraan yang saling konsisten.
            migrateSlotRules(conn);

            System.out.println("Database SQLite parkir.db dan tabel-tabel berhasil diinisialisasi.");
        }
    }

    private static void migratePetugasCredentials(Connection conn) throws SQLException {
        // Remove only the old credentials shipped by this application; never retain known demo passwords.
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM petugas WHERE (username = 'admin' AND password = 'admin') "
                        + "OR (username = 'petugas.parkir' AND password = 'parkir123')")) {
            delete.executeUpdate();
        }

        List<String[]> credentials = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username, password FROM petugas")) {
            while (rs.next()) credentials.add(new String[]{rs.getString(1), rs.getString(2)});
        }
        try (PreparedStatement update = conn.prepareStatement("UPDATE petugas SET password = ? WHERE username = ?")) {
            for (String[] credential : credentials) {
                if (!PasswordHasher.isHashed(credential[1])) {
                    // Force weak legacy credentials to be replaced during first-run setup.
                    if (credential[1] == null || credential[1].length() < 12) {
                        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM petugas WHERE username = ?")) {
                            delete.setString(1, credential[0]);
                            delete.executeUpdate();
                        }
                        continue;
                    }
                    update.setString(1, PasswordHasher.hash(credential[1]));
                    update.setString(2, credential[0]);
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static void migrateActivePlateIndex(Connection conn) throws SQLException {
        boolean hasNormalizedColumn = false;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA table_info(transaksi_parkir)")) {
            while (rs.next()) if ("plat_normalized".equalsIgnoreCase(rs.getString("name"))) hasNormalizedColumn = true;
        }
        if (!hasNormalizedColumn) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE transaksi_parkir ADD COLUMN plat_normalized TEXT NOT NULL DEFAULT ''");
            }
        }

        List<String[]> plates = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id_transaksi, plat_nomor, status FROM transaksi_parkir ORDER BY rowid")) {
            while (rs.next()) plates.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
        }
        Set<String> activePlates = new HashSet<>();
        try (PreparedStatement update = conn.prepareStatement("UPDATE transaksi_parkir SET plat_normalized = ? WHERE id_transaksi = ?")) {
            for (String[] row : plates) {
                String normalized = normalizePlate(row[1]);
                if ("PARKIR".equalsIgnoreCase(row[2]) && !activePlates.add(normalized)) {
                    // Preserve legacy duplicate rows without blocking startup; new duplicates remain rejected.
                    normalized += "#legacy#" + row[0];
                }
                update.setString(1, normalized);
                update.setString(2, row[0]);
                update.addBatch();
            }
            update.executeBatch();
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_transaksi_active_plate "
                    + "ON transaksi_parkir(plat_normalized) WHERE status = 'PARKIR'");
        }
    }

    private static void migrateTransactionSlotForeignKey(Connection conn) throws SQLException {
        boolean slotNullable = false;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA table_info(transaksi_parkir)")) {
            while (rs.next()) {
                if ("kode_slot".equalsIgnoreCase(rs.getString("name"))) slotNullable = rs.getInt("notnull") == 0;
            }
        }
        boolean setNullOnDelete = false;
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(transaksi_parkir)")) {
            while (rs.next()) {
                if ("kode_slot".equalsIgnoreCase(rs.getString("from"))
                        && "slot_parkir".equalsIgnoreCase(rs.getString("table"))
                        && "SET NULL".equalsIgnoreCase(rs.getString("on_delete"))) setNullOnDelete = true;
            }
        }
        if (slotNullable && setNullOnDelete) return;

        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS transaksi_parkir_migrating");
            stmt.execute("""
                    CREATE TABLE transaksi_parkir_migrating (
                        id_transaksi TEXT PRIMARY KEY,
                        plat_nomor TEXT NOT NULL,
                        merk_kendaraan TEXT NOT NULL,
                        jenis_kendaraan TEXT NOT NULL,
                        kode_slot TEXT,
                        is_member INTEGER NOT NULL DEFAULT 0,
                        titip_helm INTEGER NOT NULL DEFAULT 0,
                        waktu_masuk TEXT NOT NULL,
                        waktu_keluar TEXT,
                        total_biaya REAL NOT NULL DEFAULT 0.0,
                        status TEXT NOT NULL DEFAULT 'PARKIR',
                        petugas TEXT,
                        plat_normalized TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (kode_slot) REFERENCES slot_parkir(kode_slot)
                            ON UPDATE CASCADE ON DELETE SET NULL
                    )
                    """);
            stmt.execute("""
                    INSERT INTO transaksi_parkir_migrating
                    (id_transaksi, plat_nomor, merk_kendaraan, jenis_kendaraan, kode_slot,
                     is_member, titip_helm, waktu_masuk, waktu_keluar, total_biaya, status, petugas, plat_normalized)
                    SELECT id_transaksi, plat_nomor, merk_kendaraan, jenis_kendaraan, kode_slot,
                           is_member, titip_helm, waktu_masuk, waktu_keluar, total_biaya, status, petugas, plat_normalized
                    FROM transaksi_parkir
                    """);
            stmt.execute("DROP TABLE transaksi_parkir");
            stmt.execute("ALTER TABLE transaksi_parkir_migrating RENAME TO transaksi_parkir");
            stmt.execute("CREATE UNIQUE INDEX ux_transaksi_active_plate ON transaksi_parkir(plat_normalized) WHERE status = 'PARKIR'");
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    public static String normalizePlate(String plate) {
        return plate == null ? "" : plate.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    private record SlotRow(String oldCode, String zone, String type, String status,
                           String newCode, String tempCode, String newZone, String newType) { }

    private static void migrateSlotRules(Connection conn) throws SQLException {
        List<SlotRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT kode_slot, lantai_zona, jenis_kendaraan, status FROM slot_parkir ORDER BY kode_slot")) {
            while (rs.next()) {
                String oldCode = rs.getString("kode_slot");
                String oldZone = rs.getString("lantai_zona");
                String type = rs.getString("jenis_kendaraan");
                String zone = normalizedExistingZone(oldZone, oldCode, type);
                String canonicalType = switch (zone) {
                    case "Lantai 1" -> "Roda2";
                    case "Lantai 2" -> "Roda4";
                    default -> "Truk";
                };
                rows.add(new SlotRow(oldCode, oldZone, type, rs.getString("status"), null, null, zone, canonicalType));
            }
        }

        if (rows.isEmpty()) return;
        Set<String> used = new HashSet<>();
        List<SlotRow> normalized = new ArrayList<>();
        for (SlotRow row : rows) {
            String prefix = row.newZone().equals("Lantai 1") ? "A" : row.newZone().equals("Lantai 2") ? "B" : "C";
            String code = row.oldCode() == null ? "" : row.oldCode().trim().toUpperCase();
            if (!code.matches(prefix + "-\\d{2,}") || !used.add(code)) {
                int next = 1;
                do { code = prefix + "-" + String.format("%02d", next++); } while (!used.add(code));
            }
            normalized.add(new SlotRow(row.oldCode(), row.zone(), row.type(), row.status(), code,
                    "__SLOT_MIGRATION_" + UUID.randomUUID(), row.newZone(), row.newType()));
        }

        boolean needsMigration = normalized.stream().anyMatch(row ->
                !row.oldCode().equals(row.newCode())
                        || !row.newZone().equals(row.zone())
                        || !row.newType().equals(row.type()));
        if (!needsMigration) return;

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement update = conn.prepareStatement("UPDATE slot_parkir SET kode_slot = ? WHERE kode_slot = ?")) {
                for (SlotRow row : normalized) {
                    update.setString(1, row.tempCode());
                    update.setString(2, row.oldCode());
                    update.addBatch();
                }
                update.executeBatch();
            }
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE slot_parkir SET kode_slot = ?, lantai_zona = ?, jenis_kendaraan = ?, status = ? WHERE kode_slot = ?")) {
                for (int i = 0; i < normalized.size(); i++) {
                    SlotRow row = normalized.get(i);
                    update.setString(1, row.newCode());
                    update.setString(2, row.newZone());
                    update.setString(3, row.newType());
                    update.setString(4, row.status());
                    update.setString(5, row.tempCode());
                    update.addBatch();
                }
                update.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private static String normalizedExistingZone(String zone, String code, String type) {
        if (zone != null && zone.equalsIgnoreCase("Zona A")) return "Lantai 1";
        if (zone != null && zone.equalsIgnoreCase("Zona B")) return "Lantai 2";
        if (zone != null && zone.equalsIgnoreCase("Zona C")) return "Area Luar";
        if (zone != null && zone.equalsIgnoreCase("Lantai 1")) return "Lantai 1";
        if (zone != null && zone.equalsIgnoreCase("Lantai 2")) return "Lantai 2";
        if (zone != null && zone.equalsIgnoreCase("Area Luar")) return "Area Luar";
        if (code != null && code.toUpperCase().startsWith("A-")) return "Lantai 1";
        if (code != null && code.toUpperCase().startsWith("B-")) return "Lantai 2";
        if (type != null && type.equalsIgnoreCase("Roda4")) return "Lantai 2";
        if (type != null && type.equalsIgnoreCase("Truk")) return "Area Luar";
        return "Lantai 1";
    }

    /**
     * Mengisi 5 slot parkir awal jika tabel slot_parkir masih kosong.
     * Slot:
     * 1. A-01 (Lantai 1, Roda2)
     * 2. A-02 (Lantai 1, Roda2)
     * 3. B-01 (Lantai 2, Roda4)
     * 4. B-02 (Lantai 2, Roda4)
     * 5. C-01 (Area Luar, Truk)
     */
    private static void seedDefaultSlotParkir(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM slot_parkir";
        try (Statement checkStmt = conn.createStatement();
             ResultSet rs = checkStmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String insertSql = "INSERT INTO slot_parkir (kode_slot, lantai_zona, jenis_kendaraan, status) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    String[][] initialSlots = {
                        {"A-01", "Lantai 1", "Roda2", "TERSEDIA"},
                        {"A-02", "Lantai 1", "Roda2", "TERSEDIA"},
                        {"B-01", "Lantai 2", "Roda4", "TERSEDIA"},
                        {"B-02", "Lantai 2", "Roda4", "TERSEDIA"},
                        {"C-01", "Area Luar", "Truk", "TERSEDIA"}
                    };

                    for (String[] slot : initialSlots) {
                        pstmt.setString(1, slot[0]);
                        pstmt.setString(2, slot[1]);
                        pstmt.setString(3, slot[2]);
                        pstmt.setString(4, slot[3]);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    System.out.println("-> 5 Slot parkir awal berhasil ditambahkan.");
                }
            }
        }
    }

    /**
     * Mengetes apakah file database sudah ada secara fisik di disk.
     */
    public static boolean databaseFileExists() {
        File file = new File(DB_FILE_NAME);
        return file.exists() && file.isFile();
    }

    /**
     * Mengembalikan nama file database.
     */
    public static String getDbFileName() {
        return DB_FILE_NAME;
    }
}
