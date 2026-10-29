package com.empmgmt.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One paper receipt is often paid part cash, part net banking and written as two entries under the same
 * voucher number. These tests pin down what counts as such a SPLIT (same party, same day, every entry in
 * a different payment mode) and what must still be reported as a DUPLICATE.
 * Pure logic - no Spring context or database.
 */
class VoucherSplitLogicTest {

    private static final String PARTY = "M/S SURAJ B/M (RAM BUJH DRIVER) 358_09AIEPY7761L1ZB";
    private static final LocalDate D1 = LocalDate.of(2026, 8, 7);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 8);

    private static long nextId = 1;

    private static Object row(String party, LocalDate date, String mode, String amount) throws Exception {
        Class<?> rowClass = Class.forName("com.empmgmt.service.VoucherAuditService$Row");
        Constructor<?> c = rowClass.getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(nextId++, "2142", date, new BigDecimal(amount), party, mode);
    }

    private static boolean isSplitReceipt(List<Object> group) throws Exception {
        Method m = VoucherAuditService.class.getDeclaredMethod("isSplitReceipt", List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, group);
    }

    private static boolean isSplitWith(List<Object> existing, String party, LocalDate date, String mode) throws Exception {
        Method m = VoucherAuditService.class.getDeclaredMethod("isSplitWith", List.class, String.class, LocalDate.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, existing, party, date, mode);
    }

    private static List<Object> rows(Object... r) { return new ArrayList<>(List.of(r)); }

    // ---- saved entries: is the whole group a split receipt? ----

    @Test
    void cashPlusBankSameDaySamePartyIsASplit() throws Exception {
        assertEquals(true, isSplitReceipt(rows(row(PARTY, D1, "BANK_TRANSFER", "25000"), row(PARTY, D1, "CASH", "25000"))));
    }

    @Test
    void cashPlusChequeIsASplitToo() throws Exception {
        assertEquals(true, isSplitReceipt(rows(row(PARTY, D1, "CASH", "5000"), row(PARTY, D1, "CHEQUE", "70000"))));
    }

    @Test
    void sameModeTwiceIsStillADuplicate() throws Exception {
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, "CASH", "25000"), row(PARTY, D1, "CASH", "25000"))));
    }

    @Test
    void threeEntriesWithARepeatedModeIsADuplicate() throws Exception {
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, "CASH", "1"), row(PARTY, D1, "BANK_TRANSFER", "1"), row(PARTY, D1, "CASH", "1"))));
    }

    @Test
    void differentPartiesIsADuplicate() throws Exception {
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, "CASH", "1"), row("INDAN ARON STOR (GOSAIGANJ) 196", D1, "BANK_TRANSFER", "1"))));
    }

    @Test
    void sameVoucherOnDifferentDaysIsADuplicate() throws Exception {
        // the real 2147 case before it was corrected: the 7-Aug pair plus the 8-Aug receipt
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, "BANK_TRANSFER", "25000"), row(PARTY, D1, "CASH", "25000"), row(PARTY, D2, "BANK_TRANSFER", "50000"))));
    }

    @Test
    void unknownModeIsNeverTreatedAsASplit() throws Exception {
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, null, "1"), row(PARTY, D1, "CASH", "1"))));
    }

    @Test
    void aSingleEntryIsNotASplit() throws Exception {
        assertEquals(false, isSplitReceipt(rows(row(PARTY, D1, "CASH", "1"))));
    }

    // ---- entry time: would ONE MORE entry make it a split? ----

    @Test
    void secondHalfInADifferentModeIsASplit() throws Exception {
        assertEquals(true, isSplitWith(rows(row(PARTY, D1, "CASH", "20000")), PARTY, D1, "BANK_TRANSFER"));
    }

    @Test
    void thirdModeOnTopOfCashAndBankIsStillASplit() throws Exception {
        assertEquals(true, isSplitWith(rows(row(PARTY, D1, "CASH", "1"), row(PARTY, D1, "BANK_TRANSFER", "1")), PARTY, D1, "CHEQUE"));
    }

    @Test
    void repeatingTheSameModeIsADuplicate() throws Exception {
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "20000")), PARTY, D1, "CASH"));
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1"), row(PARTY, D1, "BANK_TRANSFER", "1")), PARTY, D1, "BANK_TRANSFER"));
    }

    @Test
    void anotherDayOrAnotherPartyIsADuplicate() throws Exception {
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1")), PARTY, D2, "BANK_TRANSFER"));
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1")), "SOME OTHER PARTY", D1, "BANK_TRANSFER"));
    }

    @Test
    void missingFormDetailsFallBackToDuplicate() throws Exception {
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1")), null, D1, "BANK_TRANSFER"));
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1")), PARTY, null, "BANK_TRANSFER"));
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1")), PARTY, D1, null));
    }

    @Test
    void ifAnyExistingEntryBreaksThePatternItIsADuplicate() throws Exception {
        // same voucher also used on another day -> not a clean split
        assertEquals(false, isSplitWith(rows(row(PARTY, D1, "CASH", "1"), row(PARTY, D2, "BANK_TRANSFER", "1")), PARTY, D1, "CHEQUE"));
    }
}
