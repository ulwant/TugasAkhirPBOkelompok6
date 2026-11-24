# Aplikasi Sistem Manajemen Parkir Kampus

Proyek JavaFX desktop berbasis Maven (JDK 21) dengan arsitektur Model-View-Controller (MVC) dan DAO (Data Access Object) menggunakan SQLite Database.

---

## 🛠️ Spesifikasi Teknologi
- **Bahasa**: Java 21 (JDK 21)
- **Build Tool**: Apache Maven
- **UI Framework**: JavaFX 21 (Controls & FXML)
- **Database**: SQLite JDBC (`parkir.db`)
- **Testing**: JUnit 5 Jupiter

---

## 📁 Struktur Direktori & Paket Proyek
```text
d:\AplikasiManagementParkir\
├── pom.xml                               # Konfigurasi Maven, JDK 21, Dependencies
├── mvnw.cmd                              # Maven Wrapper untuk eksekusi mudah
├── parkir.db                             # File Database SQLite lokal (dibuat otomatis)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── MainApp.java              # Entry point aplikasi JavaFX
│   │   │   ├── Main.java                 # Standard launcher class
│   │   │   ├── database/
│   │   │   │   └── DatabaseHelper.java   # Koneksi & auto-create database, tabel, seed data
│   │   │   ├── model/
│   │   │   │   ├── CetakStruk.java       # Interface cetak struk pembayaran
│   │   │   │   ├── Kendaraan.java        # Abstract class dasar kendaraan (OOP)
│   │   │   │   ├── Roda2.java            # Model motor (Rp2.000/jam + titip helm + diskon)
│   │   │   │   ├── Roda4.java            # Model mobil (Rp5.000/jam + diskon)
│   │   │   │   ├── Truk.java             # Model truk/bus (Rp10.000/jam + diskon)
│   │   │   │   ├── SlotParkir.java       # Model slot parkir (kode, lantai, jenis, status)
│   │   │   │   ├── Petugas.java          # Model petugas (autentikasi & hak akses)
│   │   │   │   ├── TransaksiParkir.java  # Model transaksi (durasi, biaya, cetak struk)
│   │   │   │   └── SlotPenuhException.java # Custom exception jika slot parkir habis
│   │   │   ├── dao/
│   │   │   │   └── ParkirDAO.java        # Lapisan akses data CRUD transaksi, slot & auth
│   │   │   └── controller/
│   │   │       └── ParkirController.java # Controller JavaFX & manajemen alur CRUD parkir
│   │   └── resources/
│   │       ├── view/
│   │       │   └── MainView.fxml         # UI layout FXML JavaFX
│   │       └── css/
│   │           └── style.css             # Tema styling modern minimalis
│   └── test/
│       └── java/
│           └── TestParkirApp.java        # Unit test otomatis 8 skenario (JUnit 5)
```

---

## 🗄️ Skema Database & Data Awal (`parkir.db`)

Dikelola otomatis oleh `DatabaseHelper.java`:

### 1. Tabel `petugas`
Menyimpan akun login operator/petugas. Pada database baru, buat akun admin pertama melalui layar setup saat aplikasi dibuka. Password minimal 12 karakter dan disimpan sebagai hash PBKDF2; tidak ada akun demo/default.
```sql
CREATE TABLE IF NOT EXISTS petugas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    nama TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'Petugas'
);
```

### 2. Tabel `slot_parkir`
Menyimpan status ketersediaan ruang parkir.
- **5 Slot Awal Terdaftar**:
  1. `A-01` (Lantai 1 - Roda2 - TERSEDIA)
  2. `A-02` (Lantai 1 - Roda2 - TERSEDIA)
  3. `B-01` (Lantai 2 - Roda4 - TERSEDIA)
  4. `B-02` (Lantai 2 - Roda4 - TERSEDIA)
  5. `C-01` (Area Luar - Truk - TERSEDIA)
```sql
CREATE TABLE IF NOT EXISTS slot_parkir (
    kode_slot TEXT PRIMARY KEY,
    lantai_zona TEXT NOT NULL,
    jenis_kendaraan TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'TERSEDIA'
);
```

### 3. Tabel `transaksi_parkir`
Menyimpan riwayat dan transaksi parkir kendaraan yang sedang berjalan maupun selesai.
```sql
CREATE TABLE IF NOT EXISTS transaksi_parkir (
    id_transaksi TEXT PRIMARY KEY,
    plat_nomor TEXT NOT NULL,
    merk_kendaraan TEXT NOT NULL,
    jenis_kendaraan TEXT NOT NULL,
    kode_slot TEXT NOT NULL,
    is_member INTEGER NOT NULL DEFAULT 0,
    titip_helm INTEGER NOT NULL DEFAULT 0,
    waktu_masuk TEXT NOT NULL,
    waktu_keluar TEXT,
    total_biaya REAL NOT NULL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'PARKIR',
    petugas TEXT,
    FOREIGN KEY (kode_slot) REFERENCES slot_parkir(kode_slot) ON UPDATE CASCADE
);
```

---

## 🚗 Aturan Bisnis & Perhitungan Tarif (OOP)
1. **Roda 2 (Motor)**:
   - Tarif dasar: Rp 2.000 / jam
   - Opsi tambahan: Titip Helm (+Rp 2.000)
   - Diskon Member: Potongan 10% dari total tagihan
2. **Roda 4 (Mobil)**:
   - Tarif dasar: Rp 5.000 / jam
   - Diskon Member: Potongan 10% dari total tagihan
3. **Truk / Bus**:
   - Tarif dasar: Rp 10.000 / jam
   - Diskon Member: Potongan 10% dari total tagihan
4. **Durasi**:
   - Dihitung dalam jam dengan pembulatan ke atas (*ceiling*). Durasi minimal 1 jam.
5. **Kapasitas Slot & Exception**:
   - Jika pengguna mencoba memasukkan kendaraan ketika seluruh slot untuk jenis kendaraan tersebut sudah terisi (`TERISI`), sistem akan melempar `SlotPenuhException`.
   - Ketika kendaraan keluar (checkout), status slot otomatis dikembalikan ke `TERSEDIA`.

---

## 🚀 Cara Menjalankan Proyek

### 1. Menjalankan Pengujian Otomatis (Unit Test)
```bash
.\mvnw test
```

### 2. Menjalankan Aplikasi JavaFX
```bash
.\mvnw javafx:run
```
Atau kompilasi dan jalankan jar:
```bash
.\mvnw clean package
java -jar target/AplikasiManagementParkir-1.0.0.jar
```
