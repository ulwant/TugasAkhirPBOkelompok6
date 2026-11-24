package model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model Transaksi Parkir yang mengelola siklus parkir kendaraan,
 * penghitungan tarif, durasi, serta mengimplementasikan interface CetakStruk.
 */
public class TransaksiParkir implements CetakStruk {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String idTransaksi;
    private String platNomor;
    private String merkKendaraan;
    private String jenisKendaraan;
    private String kodeSlot;
    private boolean member;
    private boolean titipHelm;
    private LocalDateTime waktuMasuk;
    private LocalDateTime waktuKeluar;
    private double totalBiaya;
    private String status; // "PARKIR" atau "SELESAI"
    private String petugas;

    // Objek polimorfisme Kendaraan
    private Kendaraan kendaraan;

    public TransaksiParkir() {
        this.waktuMasuk = LocalDateTime.now();
        this.status = "PARKIR";
        this.totalBiaya = 0.0;
        this.jenisKendaraan = "Roda2";
    }

    public TransaksiParkir(String idTransaksi, String platNomor, String merkKendaraan, 
                           String jenisKendaraan, String kodeSlot, boolean member, 
                           boolean titipHelm, LocalDateTime waktuMasuk, String petugas) {
        this.idTransaksi = idTransaksi;
        setPlatNomor(platNomor);
        setMerkKendaraan(merkKendaraan);
        setJenisKendaraan(jenisKendaraan);
        setKodeSlot(kodeSlot);
        this.member = member;
        setTitipHelm(titipHelm);
        setWaktuMasuk(waktuMasuk == null ? LocalDateTime.now() : waktuMasuk);
        this.status = "PARKIR";
        this.totalBiaya = 0.0;
        this.petugas = petugas;
        syncKendaraan();
    }

    /**
     * Memastikan objek kendaraan (Roda2, Roda4, Truk) sinkron dengan atribut transaksi.
     */
    public void syncKendaraan() {
        if (platNomor == null || platNomor.isBlank() || merkKendaraan == null || merkKendaraan.isBlank()
                || jenisKendaraan == null || jenisKendaraan.isBlank()) {
            this.kendaraan = null;
            return;
        }
        this.kendaraan = Kendaraan.createKendaraan(platNomor, merkKendaraan, jenisKendaraan, titipHelm);
    }

    /**
     * Menghitung durasi parkir dalam satuan jam.
     * Menggunakan pembulatan ke atas (ceiling), durasi minimal adalah 1 jam.
     */
    public long hitungDurasiJam() {
        if (waktuMasuk == null) {
            return 1;
        }
        LocalDateTime waktuAkhir = (waktuKeluar != null) ? waktuKeluar : LocalDateTime.now();
        if (waktuAkhir.isBefore(waktuMasuk)) throw new IllegalStateException("Waktu keluar tidak boleh sebelum waktu masuk.");
        long menit = Duration.between(waktuMasuk, waktuAkhir).toMinutes();
        if (menit == 0) {
            return 1;
        }
        return (long) Math.ceil(menit / 60.0);
    }

    /**
     * Menghitung total estimasi atau final biaya parkir.
     */
    public double hitungBiayaSekarang() {
        if (kendaraan == null) {
            syncKendaraan();
        }
        if (kendaraan == null) throw new IllegalStateException("Data kendaraan transaksi belum lengkap.");
        long durasiJam = hitungDurasiJam();
        return kendaraan.hitungBiaya(durasiJam, member);
    }

    /**
     * Menyelesaikan transaksi parkir: mencatat waktu keluar dan menghitung total biaya.
     */
    public void selesaikanTransaksi(LocalDateTime waktuKeluar) {
        if ("SELESAI".equals(status)) throw new IllegalStateException("Transaksi sudah selesai.");
        setWaktuKeluar((waktuKeluar != null) ? waktuKeluar : LocalDateTime.now());
        this.totalBiaya = hitungBiayaSekarang();
        this.status = "SELESAI";
    }

    @Override
    public String cetakStruk() {
        LocalDateTime akhir = (waktuKeluar != null) ? waktuKeluar : LocalDateTime.now();
        long durasi = hitungDurasiJam();
        double biaya = (totalBiaya > 0) ? totalBiaya : hitungBiayaSekarang();

        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("             PARKIR KAMPUS              \n");
        sb.append("        BUKTI PEMBAYARAN PARKIR         \n");
        sb.append("========================================\n");
        sb.append(String.format("ID Transaksi   : %s\n", idTransaksi));
        sb.append(String.format("Plat Nomor     : %s\n", platNomor));
        sb.append(String.format("Kendaraan      : %s (%s)\n", merkKendaraan, jenisKendaraan));
        sb.append(String.format("Slot Parkir    : %s\n", kodeSlot));
        sb.append(String.format("Waktu Masuk    : %s\n", waktuMasuk != null ? waktuMasuk.format(DISPLAY_FORMATTER) : "-"));
        sb.append(String.format("Waktu Keluar   : %s\n", waktuKeluar != null ? akhir.format(DISPLAY_FORMATTER) : "Sedang Parkir"));
        sb.append(String.format("Durasi Parkir  : %d Jam\n", durasi));
        sb.append(String.format("Status Member  : %s\n", member ? "Member (Diskon 10%)" : "Umum / Regular"));
        if ("Roda2".equalsIgnoreCase(jenisKendaraan)) {
            sb.append(String.format("Titip Helm     : %s\n", titipHelm ? "Ya (+Rp 2.000)" : "Tidak"));
        }
        sb.append(String.format("Petugas        : %s\n", petugas != null ? petugas : "Admin"));
        sb.append("----------------------------------------\n");
        sb.append(String.format("TOTAL BIAYA    : %s\n", formatRupiah(biaya)));
        sb.append("========================================\n");
        sb.append("   Terima kasih atas kunjungan Anda!    \n");
        sb.append("         Hati-hati di jalan!            \n");
        sb.append("========================================\n");
        return sb.toString();
    }

    // Getters and Setters

    public String getIdTransaksi() {
        return idTransaksi;
    }

    public void setIdTransaksi(String idTransaksi) {
        this.idTransaksi = idTransaksi;
    }

    public String getPlatNomor() {
        return platNomor;
    }

    public void setPlatNomor(String platNomor) {
        String value = platNomor == null ? "" : platNomor.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Plat nomor wajib diisi.");
        this.platNomor = value;
        syncKendaraan();
    }

    public String getMerkKendaraan() {
        return merkKendaraan;
    }

    public void setMerkKendaraan(String merkKendaraan) {
        String value = merkKendaraan == null ? "" : merkKendaraan.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Merk kendaraan wajib diisi.");
        this.merkKendaraan = value;
        syncKendaraan();
    }

    public String getJenisKendaraan() {
        return jenisKendaraan;
    }

    public void setJenisKendaraan(String jenisKendaraan) {
        if (jenisKendaraan == null) throw new IllegalArgumentException("Jenis kendaraan wajib dipilih.");
        String normalized = switch (jenisKendaraan.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "roda2" -> "Roda2";
            case "roda4" -> "Roda4";
            case "truk" -> "Truk";
            default -> throw new IllegalArgumentException("Jenis kendaraan hanya Roda2, Roda4, atau Truk.");
        };
        this.jenisKendaraan = normalized;
        if (!"Roda2".equals(normalized)) this.titipHelm = false;
        syncKendaraan();
    }

    public String getKodeSlot() {
        return kodeSlot;
    }

    public void setKodeSlot(String kodeSlot) {
        // Empty is allowed only while the check-in form is awaiting DAO allocation.
        this.kodeSlot = kodeSlot == null ? "" : kodeSlot.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public boolean isMember() {
        return member;
    }

    public void setMember(boolean member) {
        this.member = member;
    }

    public boolean isTitipHelm() {
        return titipHelm;
    }

    public void setTitipHelm(boolean titipHelm) {
        if (titipHelm && !"Roda2".equalsIgnoreCase(jenisKendaraan))
            throw new IllegalArgumentException("Titip helm hanya tersedia untuk kendaraan Roda2.");
        this.titipHelm = titipHelm;
        syncKendaraan();
    }

    public LocalDateTime getWaktuMasuk() {
        return waktuMasuk;
    }

    public void setWaktuMasuk(LocalDateTime waktuMasuk) {
        if (waktuMasuk == null) throw new IllegalArgumentException("Waktu masuk wajib diisi.");
        if (waktuKeluar != null && waktuKeluar.isBefore(waktuMasuk))
            throw new IllegalArgumentException("Waktu masuk tidak boleh setelah waktu keluar.");
        this.waktuMasuk = waktuMasuk;
    }

    public LocalDateTime getWaktuKeluar() {
        return waktuKeluar;
    }

    public void setWaktuKeluar(LocalDateTime waktuKeluar) {
        if (waktuKeluar == null && "SELESAI".equals(status))
            throw new IllegalArgumentException("Transaksi selesai harus memiliki waktu keluar.");
        if (waktuKeluar != null && waktuMasuk != null && waktuKeluar.isBefore(waktuMasuk))
            throw new IllegalArgumentException("Waktu keluar tidak boleh sebelum waktu masuk.");
        this.waktuKeluar = waktuKeluar;
    }

    public double getTotalBiaya() {
        return totalBiaya;
    }

    public void setTotalBiaya(double totalBiaya) {
        if (!Double.isFinite(totalBiaya) || totalBiaya < 0)
            throw new IllegalArgumentException("Total biaya harus berupa angka valid dan tidak negatif.");
        this.totalBiaya = totalBiaya;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null) throw new IllegalArgumentException("Status transaksi wajib diisi.");
        String value = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("PARKIR") && !value.equals("SELESAI"))
            throw new IllegalArgumentException("Status transaksi hanya PARKIR atau SELESAI.");
        if (value.equals("SELESAI") && waktuKeluar == null)
            throw new IllegalArgumentException("Transaksi selesai harus memiliki waktu keluar.");
        if (value.equals("PARKIR") && waktuKeluar != null)
            throw new IllegalArgumentException("Transaksi aktif tidak boleh memiliki waktu keluar.");
        this.status = value;
    }

    public String getPetugas() {
        return petugas;
    }

    public void setPetugas(String petugas) {
        this.petugas = petugas;
    }

    public Kendaraan getKendaraan() {
        if (kendaraan == null) {
            syncKendaraan();
        }
        return kendaraan;
    }

    public String getWaktuMasukFormatted() {
        return (waktuMasuk != null) ? waktuMasuk.format(DISPLAY_FORMATTER) : "-";
    }

    public String getWaktuKeluarFormatted() {
        return (waktuKeluar != null) ? waktuKeluar.format(DISPLAY_FORMATTER) : "-";
    }

    public String getTotalBiayaFormatted() {
        return formatRupiah(totalBiaya);
    }
}
