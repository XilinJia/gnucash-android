/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.test.unit.model;

import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Money;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for budgets
 */
public class BudgetTest {
    
    @Test
    public void addingBudgetAmount_shouldSetBudgetUID(){
        Budget budget = new Budget("Test");

        assertThat(budget.getMBudgetAmounts()).isNotNull();
        BudgetAmount budgetAmount = new BudgetAmount(Money.getSDefaultZero(), "test");
        budget.addBudgetAmount(budgetAmount);

        assertThat(budget.getMBudgetAmounts()).hasSize(1);
        assertThat(budgetAmount.getMBudgetUID()).isEqualTo(budget.getMUID());

        //setting a whole list should also set the budget UIDs
        List<BudgetAmount> budgetAmounts = new ArrayList<>();
        budgetAmounts.add(new BudgetAmount(Money.getSDefaultZero(),"test"));
        budgetAmounts.add(new BudgetAmount(Money.getSDefaultZero(), "second"));

        budget.setMBudgetAmounts(budgetAmounts);

        assertThat(budget.getMBudgetAmounts()).extracting("mBudgetUID")
                .contains(budget.getMUID());
    }

    @Test
    public void shouldComputeAbsoluteAmountSum(){
        Budget budget = new Budget("Test");
        Money accountAmount = new Money("-20", "USD");
        BudgetAmount budgetAmount = new BudgetAmount(accountAmount, "account1");
        BudgetAmount budgetAmount1 = new BudgetAmount(new Money("10", "USD"), "account2");

        budget.addBudgetAmount(budgetAmount);
        budget.addBudgetAmount(budgetAmount1);

        assertThat(budget.amount("account1")).isEqualTo(accountAmount.abs());
        assertThat(budget.amountSum()).isEqualTo(new Money("30", "USD"));
    }

    /**
     * Tests that the method {@link Budget#compactedBudgetAmounts()} does not aggregate
     * {@link BudgetAmount}s which have different money amounts
     */
    @Test
    public void shouldNotCompactBudgetAmountsWithDifferentAmounts(){
        Budget budget = new Budget("Test");
        budget.setMNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "test");
        budgetAmount.setMPeriodNum(1);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("15", "USD"), "test");
        budgetAmount.setMPeriodNum(2);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("5", "USD"), "secondAccount");
        budgetAmount.setMPeriodNum(5);
        budget.addBudgetAmount(budgetAmount);

        List<BudgetAmount> compactedBudgetAmounts = budget.compactedBudgetAmounts();
        assertThat(compactedBudgetAmounts).hasSize(3);
        assertThat(compactedBudgetAmounts).extracting("mAccountUID")
                .contains("test", "secondAccount");

        assertThat(compactedBudgetAmounts).extracting("mPeriodNum")
                .contains(1L, 2L, 5L).doesNotContain(-1L);
    }

    /**
     * Tests that the method {@link Budget#compactedBudgetAmounts()} aggregates {@link BudgetAmount}s
     * with the same amount but leaves others untouched
     */
    @Test
    public void addingSameAmounts_shouldCompactOnRetrieval(){
        Budget budget = new Budget("Test");
        budget.setMNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(1);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(2);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(5);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "EUR"), "second");
        budgetAmount.setMPeriodNum(4);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("13", "EUR"), "third");
        budgetAmount.setMPeriodNum(-1);
        budget.addBudgetAmount(budgetAmount);

        List<BudgetAmount> compactedBudgetAmounts = budget.compactedBudgetAmounts();

        assertThat(compactedBudgetAmounts).hasSize(3);
        assertThat(compactedBudgetAmounts).extracting("mPeriodNum").hasSize(3)
                .contains(-1L, 4L).doesNotContain(1L, 2L, 3L);

        assertThat(compactedBudgetAmounts).extracting("mAccountUID").hasSize(3)
                .contains("first", "second", "third");

    }

    /**
     * Test that when we set a periodNumber of -1 to a budget amount, the method {@link Budget#expandedBudgetAmounts()}
     * should create new budget amounts for each of the periods in the budgeting period
     */
    @Test
    public void addingNegativePeriodNum_shouldExpandOnRetrieval(){
        Budget budget = new Budget("Test");
        budget.setMNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(-1);
        budget.addBudgetAmount(budgetAmount);

        List<BudgetAmount> expandedBudgetAmount = budget.expandedBudgetAmounts();

        assertThat(expandedBudgetAmount).hasSize(6);

        assertThat(expandedBudgetAmount).extracting("mPeriodNum").hasSize(6)
                .contains(0L,1L,2L,3L,4L,5L).doesNotContain(-1L);

        assertThat(expandedBudgetAmount).extracting("mAccountUID").hasSize(6);
    }

    @Test
    public void testGetNumberOfAccounts(){
        Budget budget = new Budget("Test");
        budget.setMNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(1);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(2);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setMPeriodNum(5);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "EUR"), "second");
        budgetAmount.setMPeriodNum(4);
        budget.addBudgetAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("13", "EUR"), "third");
        budgetAmount.setMPeriodNum(-1);
        budget.addBudgetAmount(budgetAmount);

        assertThat(budget.numberOfAccounts()).isEqualTo(3);
    }
}
