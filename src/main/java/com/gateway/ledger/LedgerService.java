package com.gateway.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gateway.domain.LedgerEntry;
import com.gateway.domain.LedgerEntryRepository;
import com.gateway.domain.Payment;
import com.gateway.fx.FxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double-entry ledger (PegLedger-style: derived balances, invariant checks).
 *
 * <p>Accounts used:
 * <ul>
 *   <li>PSP_PENDING — authorized but not yet captured funds owed by the PSP.</li>
 *   <li>PSP_CLEARING — captured funds awaiting settlement (M4).</li>
 *   <li>MERCHANT_PAYABLE — funds owed to the merchant.</li>
 * </ul>
 *
 * <p>Postings:
 * <ul>
 *   <li>AUTH: Dr PSP_PENDING / Cr MERCHANT_PAYABLE</li>
 *   <li>CAPTURE: Dr PSP_CLEARING / Cr PSP_PENDING</li>
 *   <li>REFUND: Dr MERCHANT_PAYABLE / Cr PSP_CLEARING (money flows back)</li>
 *   <li>SETTLE: Dr MERCHANT_PAYABLE / Cr PSP_CLEARING (net captured amount,
 *       i.e. captured minus refunded)</li>
 * </ul>
 *
 * <p>Every entry carries the presentment currency + amount and the
 * settlement currency + converted amount with the FX rate locked at authorize
 * time (M2). Invariant: for every payment, total debits == total credits in
 * both the presentment and the settlement currency.
 */
@Service
public class LedgerService {

    public static final String PSP_PENDING = "PSP_PENDING";
    public static final String PSP_CLEARING = "PSP_CLEARING";
    public static final String MERCHANT_PAYABLE = "MERCHANT_PAYABLE";

    private final LedgerEntryRepository repository;
    private final FxService fx;

    public LedgerService(LedgerEntryRepository repository, FxService fx) {
        this.repository = repository;
        this.fx = fx;
    }

    /** Authorization: PSP owes the merchant the authorized amount. */
    @Transactional
    public void postAuthorization(Payment payment) {
        post(payment, LedgerEntry.EntryType.AUTH,
                PSP_PENDING, LedgerEntry.Direction.DEBIT, payment.getAmountMinor());
        post(payment, LedgerEntry.EntryType.AUTH,
                MERCHANT_PAYABLE, LedgerEntry.Direction.CREDIT, payment.getAmountMinor());
        assertBalanced(payment.getId());
    }

    /** Capture: move funds from pending to clearing. */
    @Transactional
    public void postCapture(Payment payment) {
        post(payment, LedgerEntry.EntryType.CAPTURE,
                PSP_CLEARING, LedgerEntry.Direction.DEBIT, payment.getAmountMinor());
        post(payment, LedgerEntry.EntryType.CAPTURE,
                PSP_PENDING, LedgerEntry.Direction.CREDIT, payment.getAmountMinor());
        assertBalanced(payment.getId());
    }

    /** Refund: reverse part (or all) of a capture back to the PSP. */
    @Transactional
    public void postRefund(Payment payment, long refundAmountMinor) {
        post(payment, LedgerEntry.EntryType.REFUND,
                MERCHANT_PAYABLE, LedgerEntry.Direction.DEBIT, refundAmountMinor);
        post(payment, LedgerEntry.EntryType.REFUND,
                PSP_CLEARING, LedgerEntry.Direction.CREDIT, refundAmountMinor);
        assertBalanced(payment.getId());
    }

    /** Settlement: pay the net captured amount (captured minus refunded) out. */
    @Transactional
    public void postSettle(Payment payment) {
        long netCaptured = payment.getAmountMinor() - refundedAmountMinor(payment.getId());
        if (netCaptured <= 0) {
            throw new IllegalStateException(
                    "Nothing to settle for payment " + payment.getId() + " (net captured=" + netCaptured + ")");
        }
        post(payment, LedgerEntry.EntryType.SETTLE,
                MERCHANT_PAYABLE, LedgerEntry.Direction.DEBIT, netCaptured);
        post(payment, LedgerEntry.EntryType.SETTLE,
                PSP_CLEARING, LedgerEntry.Direction.CREDIT, netCaptured);
        assertBalanced(payment.getId());
    }

    /** Total refunded so far for a payment (sum of REFUND debits). */
    public long refundedAmountMinor(UUID paymentId) {
        return repository.findByPaymentIdAndEntryType(paymentId, LedgerEntry.EntryType.REFUND).stream()
                .filter(e -> e.getDirection() == LedgerEntry.Direction.DEBIT)
                .mapToLong(LedgerEntry::getAmountMinor)
                .sum();
    }

    /** Derived balance for one account (debits minus credits). */
    public long balanceForAccount(String account) {
        return repository.balanceForAccount(account);
    }

    public List<LedgerEntry> entriesFor(UUID paymentId) {
        return repository.findByPaymentId(paymentId);
    }

    /**
     * Invariant check: every payment's entries must balance in both the
     * presentment and the settlement currency. Throws IllegalStateException
     * on violation — a bug, never a user error.
     */
    public void assertBalanced(UUID paymentId) {
        List<LedgerEntry> entries = repository.findByPaymentId(paymentId);
        long presentmentNet = entries.stream()
                .mapToLong(e -> e.getDirection() == LedgerEntry.Direction.DEBIT
                        ? e.getAmountMinor() : -e.getAmountMinor())
                .sum();
        if (presentmentNet != 0) {
            throw new IllegalStateException(
                    "Ledger out of balance for payment " + paymentId + " (net=" + presentmentNet + ")");
        }
        long settlementNet = entries.stream()
                .mapToLong(e -> {
                    // Pre-M2 rows have no settlement amount; treat them as 1:1.
                    long settlementAmount = e.getSettlementAmountMinor() != null
                            ? e.getSettlementAmountMinor() : e.getAmountMinor();
                    return e.getDirection() == LedgerEntry.Direction.DEBIT
                            ? settlementAmount : -settlementAmount;
                })
                .sum();
        if (settlementNet != 0) {
            throw new IllegalStateException(
                    "Ledger out of balance (settlement) for payment " + paymentId + " (net=" + settlementNet + ")");
        }
    }

    private void post(Payment payment, LedgerEntry.EntryType type, String account,
                      LedgerEntry.Direction direction, long amountMinor) {
        String settlementCurrency = payment.getSettlementCurrency() != null
                ? payment.getSettlementCurrency() : payment.getCurrency();
        long settlementAmount = fx.convert(amountMinor, payment.getCurrency(), settlementCurrency);
        repository.save(new LedgerEntry(payment.getId(), account, direction,
                amountMinor, payment.getCurrency(), settlementCurrency, settlementAmount,
                payment.getFxRate(), type, Instant.now()));
    }
}
