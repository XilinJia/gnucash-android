/*
 * Copyright (c) 2016 Àlex Magaz Graça <rivaldi8@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.test.unit.importer;

import android.database.sqlite.SQLiteDatabase;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.xml.GncXmlHelper;
import org.gnucash.android.importer.GncXmlHandler;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Imports GnuCash XML files and checks the objects defined in them are imported correctly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class GncXmlHandlerTest {
    private BooksDbAdapter mBooksDbAdapter;
    private TransactionsDbAdapter mTransactionsDbAdapter;
    private AccountsDbAdapter mAccountsDbAdapter;
    private ScheduledActionDbAdapter mScheduledActionDbAdapter;

    @Before
    public void setUp() throws Exception {
        mBooksDbAdapter = BooksDbAdapter.getInstance();
        mBooksDbAdapter.deleteAllRecords();
        assertThat(mBooksDbAdapter.getRecordsCount()).isZero();
    }

    private String importGnuCashXml(String filename) {
        SAXParser parser;
        GncXmlHandler handler = null;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
            XMLReader reader = parser.getXMLReader();
            handler = new GncXmlHandler();
            reader.setContentHandler(handler);
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);
            InputSource inputSource = new InputSource(new BufferedInputStream(inputStream));
            reader.parse(inputSource);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
            fail();
        }
        return handler.getBookUID();
    }

    private void setUpDbAdapters(String bookUID) {
        DatabaseHelper databaseHelper = new DatabaseHelper(GnuCashApplication.Companion.getAppContext(), bookUID);
        SQLiteDatabase mainDb = databaseHelper.getReadableDatabase();
        mTransactionsDbAdapter = new TransactionsDbAdapter(mainDb, new SplitsDbAdapter(mainDb));
        mAccountsDbAdapter = new AccountsDbAdapter(mainDb, mTransactionsDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(mainDb);
        mScheduledActionDbAdapter = new ScheduledActionDbAdapter(mainDb, recurrenceDbAdapter);
    }

    /**
     * Tests basic accounts import.
     *
     * <p>Checks hierarchy and attributes. We should have:</p>
     * <pre>
     * Root
     * |_ Assets
     * |   |_ Cash in wallet
     * |_ Expenses
     *     |_ Dining
     * </pre>
     */
    @Test
    public void accountsImport() {
        String bookUID = importGnuCashXml("accountsImport.xml");
        setUpDbAdapters(bookUID);

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(5); // 4 accounts + root

        Account rootAccount = mAccountsDbAdapter.getRecord("308ade8cf0be2b0b05c5eec3114a65fa");
        assertThat(rootAccount.getMParentAccountUID()).isNull();
        assertThat(rootAccount.getMName()).isEqualTo("Root Account");
        assertThat(rootAccount.isHidden()).isTrue();

        Account assetsAccount = mAccountsDbAdapter.getRecord("3f44d61cb1afd201e8ea5a54ec4fbbff");
        assertThat(assetsAccount.getMParentAccountUID()).isEqualTo(rootAccount.getMUID());
        assertThat(assetsAccount.getMName()).isEqualTo("Assets");
        assertThat(assetsAccount.isHidden()).isFalse();
        assertThat(assetsAccount.isPlaceholderAccount()).isTrue();
        assertThat(assetsAccount.getMAccountType()).isEqualTo(AccountType.ASSET);

        Account diningAccount = mAccountsDbAdapter.getRecord("6a7cf8267314992bdddcee56d71a3908");
        assertThat(diningAccount.getMParentAccountUID()).isEqualTo("9b607f63aecb1a175556676904432365");
        assertThat(diningAccount.getMName()).isEqualTo("Dining");
        assertThat(diningAccount.getMDescription()).isEqualTo("Dining");
        assertThat(diningAccount.isHidden()).isFalse();
        assertThat(diningAccount.isPlaceholderAccount()).isFalse();
        assertThat(diningAccount.isFavorite()).isFalse();
        assertThat(diningAccount.getMAccountType()).isEqualTo(AccountType.EXPENSE);
        assertThat(diningAccount.getMCommodity().getMMnemonic()).isEqualTo("USD");
        assertThat(diningAccount.getMColor()).isEqualTo(Account.DEFAULT_COLOR);
        assertThat(diningAccount.getMDefaultTransferAccountUID()).isNull();
    }

    /**
     * Tests importing a simple transaction with default splits.
     *
     * @throws ParseException
     */
    @Test
    public void simpleTransactionImport() throws ParseException {
        String bookUID = importGnuCashXml("simpleTransactionImport.xml");
        setUpDbAdapters(bookUID);

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("b33c8a6160494417558fd143731fc26a");

        // Check attributes
        assertThat(transaction.getMDescription()).isEqualTo("Kahuna Burger");
        assertThat(transaction.getMCommodity().getMMnemonic()).isEqualTo("USD");
        assertThat(transaction.getMNotes()).isEqualTo("");
        assertThat(transaction.getMScheduledActionUID()).isNull();
        assertThat(transaction.getMIsExported()).isTrue();
        assertThat(transaction.getMIsTemplate()).isFalse();
        assertThat(transaction.getMTimestamp()).
                isEqualTo(GncXmlHelper.parseDate("2016-08-23 00:00:00 +0200"));
        assertThat(transaction.getMCreatedTimestamp().getTime()).
                isEqualTo(GncXmlHelper.parseDate("2016-08-23 12:44:19 +0200"));

        // Check splits
        assertThat(transaction.getMSplitList().size()).isEqualTo(2);
        // FIXME: don't depend on the order
        Split split1 = transaction.getMSplitList().get(0);
        assertThat(split1.getMUID()).isEqualTo("ad2cbc774fc4e71885d17e6932448e8e");
        assertThat(split1.getMAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getMTransactionUID()).isEqualTo("b33c8a6160494417558fd143731fc26a");
        assertThat(split1.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split1.getMMemo()).isNull();
        assertThat(split1.getMValue()).isEqualTo(new Money("10", "USD"));
        assertThat(split1.getMQuantity()).isEqualTo(new Money("10", "USD"));
        assertThat(split1.getMReconcileState()).isEqualTo('n');

        Split split2 = transaction.getMSplitList().get(1);
        assertThat(split2.getMUID()).isEqualTo("61d4d604bc00a59cabff4e8875d00bee");
        assertThat(split2.getMAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getMTransactionUID()).isEqualTo("b33c8a6160494417558fd143731fc26a");
        assertThat(split2.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split2.getMMemo()).isNull();
        assertThat(split2.getMValue()).isEqualTo(new Money("10", "USD"));
        assertThat(split2.getMQuantity()).isEqualTo(new Money("10", "USD"));
        assertThat(split2.getMReconcileState()).isEqualTo('n');
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests importing a transaction with non-default splits.
     *
     * @throws ParseException
     */
    @Test
    public void transactionWithNonDefaultSplitsImport() throws ParseException {
        String bookUID = importGnuCashXml("transactionWithNonDefaultSplitsImport.xml");
        setUpDbAdapters(bookUID);

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("042ff745a80e94e6237fb0549f6d32ae");

        // Ensure it's the correct one
        assertThat(transaction.getMDescription()).isEqualTo("Tandoori Mahal");

        // Check splits
        assertThat(transaction.getMSplitList().size()).isEqualTo(3);
        // FIXME: don't depend on the order
        Split expenseSplit = transaction.getMSplitList().get(0);
        assertThat(expenseSplit.getMUID()).isEqualTo("c50cce06e2bf9085730821c82d0b36ca");
        assertThat(expenseSplit.getMAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(expenseSplit.getMTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(expenseSplit.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(expenseSplit.getMMemo()).isNull();
        assertThat(expenseSplit.getMValue()).isEqualTo(new Money("50", "USD"));
        assertThat(expenseSplit.getMQuantity()).isEqualTo(new Money("50", "USD"));

        Split assetSplit1 = transaction.getMSplitList().get(1);
        assertThat(assetSplit1.getMUID()).isEqualTo("4930f412665a705eedba39789b6c3a35");
        assertThat(assetSplit1.getMAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(assetSplit1.getMTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(assetSplit1.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(assetSplit1.getMMemo()).isEqualTo("tip");
        assertThat(assetSplit1.getMValue()).isEqualTo(new Money("5", "USD"));
        assertThat(assetSplit1.getMQuantity()).isEqualTo(new Money("5", "USD"));
        assertThat(assetSplit1.isPairOf(expenseSplit)).isFalse();

        Split assetSplit2 = transaction.getMSplitList().get(2);
        assertThat(assetSplit2.getMUID()).isEqualTo("b97cd9bbaa17f181d0a5b39b260dabda");
        assertThat(assetSplit2.getMAccountUID()).isEqualTo("ee139a5658a0d37507dc26284798e347");
        assertThat(assetSplit2.getMTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(assetSplit2.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(assetSplit2.getMMemo()).isNull();
        assertThat(assetSplit2.getMValue()).isEqualTo(new Money("45", "USD"));
        assertThat(assetSplit2.getMQuantity()).isEqualTo(new Money("45", "USD"));
        assertThat(assetSplit2.isPairOf(expenseSplit)).isFalse();
    }

    /**
     * Tests importing a transaction with multiple currencies.
     *
     * @throws ParseException
     */
    @Test
    public void multiCurrencyTransactionImport() throws ParseException {
        String bookUID = importGnuCashXml("multiCurrencyTransactionImport.xml");
        setUpDbAdapters(bookUID);

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("ded49386f8ea319ccaee043ba062b3e1");

        // Ensure it's the correct one
        assertThat(transaction.getMDescription()).isEqualTo("Salad express");
        assertThat(transaction.getMCommodity().getMMnemonic()).isEqualTo("USD");

        // Check splits
        assertThat(transaction.getMSplitList().size()).isEqualTo(2);
        // FIXME: don't depend on the order
        Split split1 = transaction.getMSplitList().get(0);
        assertThat(split1.getMUID()).isEqualTo("88bbbbac7689a8657b04427f8117a783");
        assertThat(split1.getMAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getMTransactionUID()).isEqualTo("ded49386f8ea319ccaee043ba062b3e1");
        assertThat(split1.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split1.getMValue()).isEqualTo(new Money("20", "USD"));
        assertThat(split1.getMQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = transaction.getMSplitList().get(1);
        assertThat(split2.getMUID()).isEqualTo("e0dd885065bfe3c9ef63552fe84c6d23");
        assertThat(split2.getMAccountUID()).isEqualTo("0469e915a22ba7846aca0e69f9f9b683");
        assertThat(split2.getMTransactionUID()).isEqualTo("ded49386f8ea319ccaee043ba062b3e1");
        assertThat(split2.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split2.getMValue()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.getMQuantity()).isEqualTo(new Money("17.93", "EUR"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests importing a simple scheduled transaction with default splits.
     */
    //@Test Disabled as currently amounts are only read from credit/debit-numeric
    // slots and transactions without amount are ignored.
    public void simpleScheduledTransactionImport() throws ParseException {
        String bookUID = importGnuCashXml("simpleScheduledTransactionImport.xml");
        setUpDbAdapters(bookUID);

        assertThat(mTransactionsDbAdapter.getTemplateTransactionsCount()).isEqualTo(1);

        Transaction scheduledTransaction =
                mTransactionsDbAdapter.getRecord("b645bef06d0844aece6424ceeec03983");

        // Check attributes
        assertThat(scheduledTransaction.getMDescription()).isEqualTo("Los pollos hermanos");
        assertThat(scheduledTransaction.getMCommodity().getMMnemonic()).isEqualTo("USD");
        assertThat(scheduledTransaction.getMNotes()).isEqualTo("");
        assertThat(scheduledTransaction.getMScheduledActionUID()).isNull();
        assertThat(scheduledTransaction.getMIsExported()).isTrue();
        assertThat(scheduledTransaction.getMIsTemplate()).isTrue();
        assertThat(scheduledTransaction.getMTimestamp())
                .isEqualTo(GncXmlHelper.parseDate("2016-08-24 00:00:00 +0200"));
        assertThat(scheduledTransaction.getMCreatedTimestamp().getTime())
                .isEqualTo(GncXmlHelper.parseDate("2016-08-24 19:50:15 +0200"));

        // Check splits
        assertThat(scheduledTransaction.getMSplitList().size()).isEqualTo(2);

        Split split1 = scheduledTransaction.getMSplitList().get(0);
        assertThat(split1.getMUID()).isEqualTo("f66794ef262aac3ae085ecc3030f2769");
        assertThat(split1.getMAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getMTransactionUID()).isEqualTo("b645bef06d0844aece6424ceeec03983");
        assertThat(split1.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split1.getMMemo()).isNull();
        assertThat(split1.getMValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split1.getQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = scheduledTransaction.getMSplitList().get(1);
        assertThat(split2.getMUID()).isEqualTo("57e2be6ca6b568f8f7c9b2e455e1e21f");
        assertThat(split2.getMAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getMTransactionUID()).isEqualTo("b645bef06d0844aece6424ceeec03983");
        assertThat(split2.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split2.getMMemo()).isNull();
        assertThat(split2.getMValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split2.getQuantity()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests that importing a weekly scheduled action sets the days of the
     * week of the recursion.
     */
    @Test
    public void importingScheduledAction_shouldSetByDays() throws ParseException {
        String bookUID = importGnuCashXml("importingScheduledAction_shouldSetByDays.xml");
        setUpDbAdapters(bookUID);

        ScheduledAction scheduledTransaction =
                mScheduledActionDbAdapter.getRecord("b5a13acb5a9459ebed10d06b75bbad10");

        // There are 3 byDays but, for now, getting one is enough to ensure it is executed
        assertThat(scheduledTransaction.getMRecurrence().byDays().size()).isGreaterThanOrEqualTo(1);

        // Until we implement parsing of days of the week for scheduled actions,
        // we'll just use the day of the week of the start time.
        int dayOfWeekFromByDays = scheduledTransaction.getMRecurrence().byDays().get(0);
        Date startTime = new Date(scheduledTransaction.getMStartTime());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startTime);
        int dayOfWeekFromStartTime = calendar.get(Calendar.DAY_OF_WEEK);
        assertThat(dayOfWeekFromByDays).isEqualTo(dayOfWeekFromStartTime);
    }

    /**
     * Checks for bug 562 - Scheduled transaction imported with imbalanced splits.
     *
     * <p>Happens when an scheduled transaction is defined with both credit and
     * debit slots in each split.</p>
     */
    @Test
    public void bug562_scheduledTransactionImportedWithImbalancedSplits() throws ParseException {
        String bookUID = importGnuCashXml("bug562_scheduledTransactionImportedWithImbalancedSplits.xml");
        setUpDbAdapters(bookUID);

        assertThat(mTransactionsDbAdapter.getTemplateTransactionsCount()).isEqualTo(1);

        Transaction scheduledTransaction =
                mTransactionsDbAdapter.getRecord("b645bef06d0844aece6424ceeec03983");

        // Ensure it's the correct transaction
        assertThat(scheduledTransaction.getMDescription()).isEqualTo("Los pollos hermanos");
        assertThat(scheduledTransaction.getMIsTemplate()).isTrue();

        // Check splits
        assertThat(scheduledTransaction.getMSplitList().size()).isEqualTo(2);

        Split split1 = scheduledTransaction.getMSplitList().get(0);
        assertThat(split1.getMAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getMSplitType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split1.getMValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split1.getQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = scheduledTransaction.getMSplitList().get(1);
        assertThat(split2.getMAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getMSplitType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split2.getMValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split2.getQuantity()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }
}