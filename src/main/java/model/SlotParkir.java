package model;

/**
 * Model untuk merepresentasikan Slot / Tempat Parkir.
 */
public class SlotParkir {

    private String kodeSlot;
    private String lantaiZona;
    private String jenisKendaraan;
    private String status; // "TERSEDIA" atau "TERISI"

    public SlotParkir() {
        this("", "Lantai 1", "Roda2", "TERSEDIA");
    }

    public SlotParkir(String kodeSlot, String lantaiZona, String jenisKendaraan) {
        this(kodeSlot, lantaiZona, jenisKendaraan, "TERSEDIA");
    }

    public SlotParkir(String kodeSlot, String lantaiZona, String jenisKendaraan, String status) {
        this.lantaiZona = normalisasiZona(lantaiZona);
        this.jenisKendaraan = jenisUntukZona(this.lantaiZona);
        if (jenisKendaraan != null && !jenisKendaraan.isBlank()) setJenisKendaraan(jenisKendaraan);
        setKodeSlot(kodeSlot);
        setStatus(status == null || status.isBlank() ? "TERSEDIA" : status);
    }

    public static String normalisasiZona(String zona) {
        if (zona == null) throw new IllegalArgumentException("Zona parkir wajib dipilih.");
        String value = zona.trim();
        if (value.equalsIgnoreCase("Zona A") || value.equalsIgnoreCase("Lantai 1")) return "Lantai 1";
        if (value.equalsIgnoreCase("Zona B") || value.equalsIgnoreCase("Lantai 2")) return "Lantai 2";
        if (value.equalsIgnoreCase("Zona C") || value.equalsIgnoreCase("Area Luar")) return "Area Luar";
        throw new IllegalArgumentException("Zona hanya boleh Zona A, Zona B, atau Zona C.");
    }

    public static String namaZona(String zona) {
        return switch (normalisasiZona(zona)) {
            case "Lantai 1" -> "Zona A";
            case "Lantai 2" -> "Zona B";
            default -> "Zona C";
        };
    }

    public static String kodeDariNomor(String zona, String nomorSlot) {
        if (nomorSlot == null || !nomorSlot.trim().matches("\\d+"))
            throw new IllegalArgumentException("Nomor slot wajib berupa angka positif.");
        try {
            int nomor = Integer.parseInt(nomorSlot.trim());
            if (nomor <= 0) throw new IllegalArgumentException("Nomor slot harus lebih besar dari 0.");
            return prefixUntukZona(zona) + "-" + String.format("%02d", nomor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nomor slot terlalu besar.", e);
        }
    }

    public static String jenisUntukZona(String zona) {
        return switch (normalisasiZona(zona)) {
            case "Lantai 1" -> "Roda2";
            case "Lantai 2" -> "Roda4";
            default -> "Truk";
        };
    }

    public static String prefixUntukZona(String zona) {
        return switch (normalisasiZona(zona)) {
            case "Lantai 1" -> "A";
            case "Lantai 2" -> "B";
            default -> "C";
        };
    }

    public boolean isTersedia() {
        return "TERSEDIA".equalsIgnoreCase(this.status);
    }

    public String getKodeSlot() {
        return kodeSlot;
    }

    public String getNomorSlot() {
        return kodeSlot == null || kodeSlot.length() < 3 ? "" : kodeSlot.substring(2);
    }

    public String getZona() { return namaZona(lantaiZona); }

    public void setKodeSlot(String kodeSlot) {
        String code = kodeSlot == null ? "" : kodeSlot.trim().toUpperCase();
        if (!code.isEmpty() && !code.matches(prefixUntukZona(lantaiZona) + "-\\d{2,}")) {
            throw new IllegalArgumentException("Kode slot untuk " + lantaiZona + " harus diawali " + prefixUntukZona(lantaiZona) + "-.");
        }
        this.kodeSlot = code;
    }

    public String getLantaiZona() {
        return lantaiZona;
    }

    public void setLantaiZona(String lantaiZona) {
        this.lantaiZona = normalisasiZona(lantaiZona);
        this.jenisKendaraan = jenisUntukZona(this.lantaiZona);
        if (kodeSlot != null && !kodeSlot.isEmpty()
                && !kodeSlot.matches(prefixUntukZona(this.lantaiZona) + "-\\d{2,}")) {
            this.kodeSlot = "";
        }
    }

    public String getJenisKendaraan() {
        return jenisKendaraan;
    }

    public void setJenisKendaraan(String jenisKendaraan) {
        String expected = jenisUntukZona(lantaiZona);
        if (jenisKendaraan == null || !expected.equalsIgnoreCase(jenisKendaraan.trim())) {
            throw new IllegalArgumentException("Jenis kendaraan untuk " + lantaiZona + " harus " + expected + ".");
        }
        this.jenisKendaraan = expected;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null) throw new IllegalArgumentException("Status slot wajib diisi.");
        String value = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("TERSEDIA") && !value.equals("TERISI"))
            throw new IllegalArgumentException("Status slot hanya boleh TERSEDIA atau TERISI.");
        this.status = value;
    }

    @Override
    public String toString() {
        return kodeSlot + " (" + lantaiZona + " - " + jenisKendaraan + ") [" + status + "]";
    }
}
