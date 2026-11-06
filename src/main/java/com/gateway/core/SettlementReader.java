package com.gateway.core;

import java.util.List;

/**
 * Reads PSP settlement files for reconciliation (M4). Defined in M1 so the
 * framework shape is complete; implemented when reconciliation lands.
 */
public interface SettlementReader {

    List<SettlementRecord> read();

    record SettlementRecord(String pspReference, long amountMinor, String currency, String status) {
    }
}
