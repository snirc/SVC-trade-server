package snir.code.services.otcmarkets;

import io.vertx.core.Handler;
import snir.code.config.AppConfig;
import snir.code.db.MongoLayer;
import snir.code.utils.DateUtils;

public class MarketActivity {

	public MarketActivity() {
		MongoLayer.getInstance(AppConfig.AppParameters.get("APP"), instance -> {
			try {
				setMongoApi(instance);
			} catch (Exception e) {
				// TODO: maybe fail start promise we can't get log mongo instance
//				startPromise.fail(e);
			}
		});
	}

	private void setMongoApi(MongoLayer instance) {
		/*
		 * mongoLayer = instance;
		 * mongoLayer.createCollection(OTC_STOCK_MARKET_ACTIVITY);
		 * 
		 * System.out.println("Scan stock market: " + DateUtils.getLastDateTime());
		 * scanStockMarket();
		 * 
		 * long timerID = AppConfig.vertx.setPeriodic(PERIODIC, new Handler<Long>() {
		 * 
		 * @Override public void handle(Long aLong) {
		 * System.out.println("Scan stock market: " + DateUtils.getLastDateTime());
		 * scanStockMarket(); } });
		 */
		
	}

	private void scanStockMarket() {
		// TODO Auto-generated method stub
		
	}
}
