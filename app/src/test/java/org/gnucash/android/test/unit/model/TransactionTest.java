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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionTest {

	@Test
	public void testCloningTransaction(){
		Transaction transaction = new Transaction("Bobba Fett");
		assertThat(transaction.getMUID()).isNotNull();
		assertThat(transaction.getMMnemonic()).isEqualTo(Commodity.DEFAULT_COMMODITY.getMMnemonic());

		Transaction clone1 = new Transaction(transaction, false);
		assertThat(transaction.getMUID()).isEqualTo(clone1.getMUID());
		assertThat(transaction).isEqualTo(clone1);

		Transaction clone2 = new Transaction(transaction, true);
		assertThat(transaction.getMUID()).isNotEqualTo(clone2.getMUID());
		assertThat(transaction.getMMnemonic()).isEqualTo(clone2.getMMnemonic());
		assertThat(transaction.getMDescription()).isEqualTo(clone2.getMDescription());
		assertThat(transaction.getMNotes()).isEqualTo(clone2.getMNotes());
		assertThat(transaction.getMTimestamp()).isEqualTo(clone2.getMTimestamp());
		//TODO: Clone the created_at and modified_at times?
	}

	/**
	 * Adding a split to a transaction should set the transaction UID of the split to the GUID of the transaction
	 */
	@Test
	public void addingSplitsShouldSetTransactionUID(){
		Transaction transaction = new Transaction("");
		assertThat(transaction.getMMnemonic()).isEqualTo(Commodity.DEFAULT_COMMODITY.getMMnemonic());

		Split split = new Split(Money.getSDefaultZero(), "test-account");
		assertThat(split.getMTransactionUID()).isEmpty();

		transaction.addSplit(split);
		assertThat(split.getMTransactionUID()).isEqualTo(transaction.getMUID());
	}

	@Test
	public void settingUID_shouldSetTransactionUidOfSplits(){
		Transaction t1 = new Transaction("Test");
		Split split1 = new Split(Money.getSDefaultZero(), "random");
		split1.setMTransactionUID("non-existent");

		Split split2 = new Split(Money.getSDefaultZero(), "account-something");
		split2.setMTransactionUID("pre-existent");

		List<Split> splits = new ArrayList<>();
		splits.add(split1);
		splits.add(split2);

		t1.setMSplitList(splits);

		assertThat(t1.getMSplitList()).extracting("mTransactionUID")
				.contains(t1.getMUID())
				.doesNotContain("non-existent")
				.doesNotContain("pre-existent");
	}

	@Test
	public void testCreateAutoBalanceSplit() {
		Transaction transactionCredit = new Transaction("Transaction with more credit");
        transactionCredit.setMCommodity(Commodity.getInstance("EUR"));
		Split creditSplit = new Split(new Money("1", "EUR"), "test-account");
		creditSplit.setMSplitType(TransactionType.CREDIT);
		transactionCredit.addSplit(creditSplit);
		Split debitBalanceSplit = transactionCredit.createAutoBalanceSplit();

		assertThat(creditSplit.getMValue().isNegative()).isFalse();
		assertThat(debitBalanceSplit.getMValue()).isEqualTo(creditSplit.getMValue());

		assertThat(creditSplit.getMQuantity().isNegative()).isFalse();
		assertThat(debitBalanceSplit.getMQuantity()).isEqualTo(creditSplit.getMQuantity());


		Transaction transactionDebit = new Transaction("Transaction with more debit");
		transactionDebit.setMCommodity(Commodity.getInstance("EUR"));
		Split debitSplit = new Split(new Money("1", "EUR"), "test-account");
		debitSplit.setMSplitType(TransactionType.DEBIT);
		transactionDebit.addSplit(debitSplit);
		Split creditBalanceSplit = transactionDebit.createAutoBalanceSplit();

		assertThat(debitSplit.getMValue().isNegative()).isFalse();
		assertThat(creditBalanceSplit.getMValue()).isEqualTo(debitSplit.getMValue());

		assertThat(debitSplit.getMQuantity().isNegative()).isFalse();
		assertThat(creditBalanceSplit.getMQuantity()).isEqualTo(debitSplit.getMQuantity());
	}
}
