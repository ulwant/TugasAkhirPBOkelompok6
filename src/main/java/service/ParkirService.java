package service;

import dao.ParkirDAO;
import model.Petugas;
import model.SlotParkir;
import model.SlotPenuhException;
import model.TransaksiParkir;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Application use cases and cross-model rules; controllers remain responsible for presentation. */
public final class ParkirService {
    private final ParkirDAO dao;

    public ParkirService(ParkirDAO dao) { this.dao = dao == null ? new ParkirDAO() : dao; }

    public Petugas authenticate(String username, String password) throws SQLException { return dao.login(username, password); }
    public boolean needsInitialAccount() throws SQLException { return !dao.hasAnyPetugas(); }
    public List<TransaksiParkir> transactions() throws SQLException { return dao.getAllTransaksi(); }
    public List<TransaksiParkir> searchTransactions(String keyword, String status) throws SQLException { return dao.cariTransaksi(keyword, status); }
    public TransaksiParkir transaction(String id) throws SQLException { return dao.getTransaksiById(id); }
    public List<SlotParkir> slots() throws SQLException { return dao.getAllSlots(); }
    public List<SlotParkir> availableSlots(String vehicleType) throws SQLException { return dao.getSlotTersedia(vehicleType); }
    public SlotParkir slot(String code) throws SQLException { return dao.getSlotByKode(code); }
    public void createInitialAccount(String username, String password, String name) throws SQLException {
        dao.createInitialPetugas(username, password, name);
    }
    public void checkIn(TransaksiParkir transaction) throws SQLException, SlotPenuhException { dao.tambahTransaksi(transaction); }
    public void updateActiveTransaction(TransaksiParkir transaction) throws SQLException { dao.updateTransaksi(transaction); }

    public TransaksiParkir previewCheckout(TransaksiParkir transaction, LocalDateTime checkoutTime) {
        if (transaction == null) throw new IllegalArgumentException("Pilih transaksi yang akan diproses.");
        TransaksiParkir preview = new TransaksiParkir(transaction.getIdTransaksi(), transaction.getPlatNomor(),
                transaction.getMerkKendaraan(), transaction.getJenisKendaraan(), transaction.getKodeSlot(),
                transaction.isMember(), transaction.isTitipHelm(), transaction.getWaktuMasuk(), transaction.getPetugas());
        preview.selesaikanTransaksi(checkoutTime);
        return preview;
    }

    public TransaksiParkir checkout(String transactionId, LocalDateTime checkoutTime) throws SQLException {
        return dao.prosesKendaraanKeluar(transactionId, checkoutTime);
    }
    public void deleteTransaction(String transactionId) throws SQLException { dao.hapusTransaksi(transactionId); }

    public void createSlot(SlotParkir slot) throws SQLException {
        if (slot == null) throw new IllegalArgumentException("Data slot wajib diisi.");
        dao.tambahSlot(slot);
    }
    public void updateSlot(String previousCode, SlotParkir slot) throws SQLException { dao.updateSlot(previousCode, slot); }
    public void updateSlot(SlotParkir slot, String previousCode) throws SQLException { dao.updateSlot(previousCode, slot); }
    public void deleteSlot(String code) throws SQLException { dao.hapusSlot(code); }
}
