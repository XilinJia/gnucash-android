package org.gnucash.android.test.unit.db;

import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Price;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Test price functions
 */
@RunWith(RobolectricTestRunner.class) //package is required so that resources can be found in dev mode
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class PriceDbAdapterTest {

    /**
     * The price table should override price for any commodity/currency pair
     * todo: maybe move this to UI testing. Not sure how Robolectric handles this
     */
    @Test
    public void shouldOnlySaveOnePricePerCommodityPair(){
        String commodityUID = CommoditiesDbAdapter.getInstance().getCommodityUID("EUR");
        String currencyUID = CommoditiesDbAdapter.getInstance().getCommodityUID("USD");
        Price price = new Price(commodityUID, currencyUID);
        price.setMValueNum(134);
        price.setMValueDenom(100);

        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        pricesDbAdapter.addRecord(price);

        price = pricesDbAdapter.getRecord(price.getMUID());
        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(price.getMValueNum()).isEqualTo(67); //the price is reduced to 57/100 before saving

        Price price1 = new Price(commodityUID, currencyUID);
        price1.setMValueNum(187);
        price1.setMValueDenom(100);
        pricesDbAdapter.addRecord(price1);

        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(1);
        Price savedPrice = pricesDbAdapter.getAllRecords().get(0);
        assertThat(savedPrice.getMUID()).isEqualTo(price1.getMUID()); //different records
        assertThat(savedPrice.getMValueNum()).isEqualTo(187);
        assertThat(savedPrice.getMValueDenom()).isEqualTo(100);


        Price price2 = new Price(currencyUID, commodityUID);
        price2.setMValueNum(190);
        price2.setMValueDenom(100);
        pricesDbAdapter.addRecord(price2);

        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(2);
    }
}
