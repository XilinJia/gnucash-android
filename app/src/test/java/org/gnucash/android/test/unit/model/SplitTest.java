package org.gnucash.android.test.unit.model;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for Splits
 *
 * @author Ngewi
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class SplitTest {
    @Test
    public void amounts_shouldBeStoredUnsigned() {
        Split split = new Split(new Money("-1", "USD"), new Money("-2", "EUR"), "account-UID");
        assertThat(split.getMValue().isNegative()).isFalse();
        assertThat(split.getMQuantity().isNegative()).isFalse();

        split.setMValue(new Money("-3", "USD"));
        split.setMQuantity(new Money("-4", "EUR"));
        assertThat(split.getMValue().isNegative()).isFalse();
        assertThat(split.getMQuantity().isNegative()).isFalse();
    }

    @Test
    public void testAddingSplitToTransaction(){
        Split split = new Split(Money.getSDefaultZero(), "Test");
        assertThat(split.getMTransactionUID()).isEmpty();

        Transaction transaction = new Transaction("Random");
        transaction.addSplit(split);

        assertThat(transaction.getMUID()).isEqualTo(split.getMTransactionUID());

    }

    @Test
    public void testCloning(){
        Split split = new Split(new Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account");
        split.setMTransactionUID("terminator-trx");
        split.setMSplitType(TransactionType.CREDIT);

        Split clone1 = new Split(split, false);
        assertThat(clone1).isEqualTo(split);

        Split clone2 = new Split(split, true);
        assertThat(clone2.getMUID()).isNotEqualTo(split.getMUID());
        assertThat(split.isEquivalentTo(clone2)).isTrue();
    }

    /**
     * Tests that a split pair has the inverse transaction type as the origin split.
     * Everything else should be the same
     */
    @Test
    public void shouldCreateInversePair(){
        Split split = new Split(new Money("2", "USD"), "dummy");
        split.setMSplitType(TransactionType.CREDIT);
        split.setMTransactionUID("random-trx");
        Split pair = split.createPair("test");

        assertThat(pair.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(pair.getMValue()).isEqualTo(split.getMValue());
        assertThat(pair.getMMemo()).isEqualTo(split.getMMemo());
        assertThat(pair.getMTransactionUID()).isEqualTo(split.getMTransactionUID());
    }

    @Test
    public void shouldGenerateValidCsv(){
        Split split = new Split(new Money(BigDecimal.TEN, Commodity.getInstance("EUR")), "random-account");
        split.setMTransactionUID("terminator-trx");
        split.setMSplitType(TransactionType.CREDIT);

        assertThat(split.toCsv()).isEqualTo(split.getMUID() + ";1000;100;EUR;1000;100;EUR;terminator-trx;random-account;CREDIT");
    }

    @Test
    public void shouldParseCsv(){
        String csv = "test-split-uid;490;100;USD;490;100;USD;trx-action;test-account;DEBIT;Didn't you get the memo?";
        Split split = Split.parseSplit(csv);
        assertThat(split.getMValue().numerator()).isEqualTo(new Money("4.90", "USD").numerator());
        assertThat(split.getMTransactionUID()).isEqualTo("trx-action");
        assertThat(split.getMAccountUID()).isEqualTo("test-account");
        assertThat(split.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split.getMMemo()).isEqualTo("Didn't you get the memo?");
    }
}
