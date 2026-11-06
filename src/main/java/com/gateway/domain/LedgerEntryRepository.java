package com.gateway.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByPaymentId(UUID paymentId);

    List<LedgerEntry> findByPaymentIdAndEntryType(UUID paymentId, LedgerEntry.EntryType entryType);

    /** Net balance (debits minus credits) for one account. Derived, never stored. */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amountMinor ELSE -e.amountMinor END), 0)"
            + " FROM LedgerEntry e WHERE e.account = :account")
    long balanceForAccount(String account);
}
