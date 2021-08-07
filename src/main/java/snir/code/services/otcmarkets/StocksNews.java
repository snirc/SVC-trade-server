package snir.code.services.otcmarkets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import rx.Single;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.utils.DateUtils;
import snir.code.utils.MessageLog;

public class StocksNews {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	public static MongoLayer mongoLayer = null;
	public static String OTC_NEWS_COLLECTION = "OTC_Stock_News";
	public static String OTC_STOCK_FINANCIAL_REPORTS = "OTC_Stock_Report";
	public static String OTC_STOCK_SEC_FILINGS = "OTC_Stock_Sec_Filing";
	private static long PERIODIC = 60000 * 10;
	private String STOCK_LIST_TAG_NAME = "records";

	public StocksNews() {

		MongoLayer.getInstance(AppConfig.AppParameters.get("APP"), instance -> {
			try {
				mongoLayer = instance;
				runScunPeriodic();
			} catch (Exception e) {
				// TODO: maybe fail start promise we can't get log mongo instance
//				startPromise.fail(e);
			}
		});

	}

	private void runScunPeriodic() {
		
		mongoLayer.createCollection(OTC_NEWS_COLLECTION);
		mongoLayer.createCollection(OTC_STOCK_FINANCIAL_REPORTS);
		mongoLayer.createCollection(OTC_STOCK_SEC_FILINGS);
			
		AppConfig.vertx.setPeriodic(PERIODIC, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run News stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/dns/tier/news", OTC_NEWS_COLLECTION);

			}

		});

		AppConfig.vertx.setPeriodic(PERIODIC + 2000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run sec-filings stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/sec-filings", OTC_STOCK_SEC_FILINGS);

			}

		});

		AppConfig.vertx.setPeriodic(PERIODIC + 4000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run financial-report stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/financial-report", OTC_STOCK_FINANCIAL_REPORTS);

			}

		});

	}

	private void scanNewsStocks(String activity, String dbCollection) {
		String url = "backend.otcmarkets.com";
		String uri = activity;
		WebClient client = WebClient.create(AppConfig.vertx);

		client.get(443, url, uri).addQueryParam("page", "1").addQueryParam("pageSize", "50").ssl(true).send()
				.onSuccess(response -> setStock(new JsonObject(response.bodyAsString()), dbCollection))
				.onFailure(err -> MessageLog.logError(MessageKey.GET_STOCK_ERROR, err.getMessage(), logger));

	}

	private void setStock(JsonObject newsData, String dbCollection) {
		newsData.getJsonArray(STOCK_LIST_TAG_NAME).forEach(item -> {
			
			JsonObject stock = (JsonObject) item;
			String stockId = stock.getString("symbol");
			stock.put("_id", stockId);
			stock.put("updated", DateUtils.getLastDateTime());
			stock.put("updatedMill", System.currentTimeMillis());
			Single<JsonObject> dataStocks = mongoLayer.findById(dbCollection, stock.getString("_id"));
			dataStocks.subscribe(dbStock -> {
				if (dbStock == null) { 
					System.out.println("There is a new stock news.... "+stockId);
					mongoLayer.insertingDocuments(dbCollection, stock);
				}
				else {
					checkStockCheng(stock, dbStock, dbCollection);
				}

			});
				
			
		});
	}

	private void checkStockCheng(JsonObject stock, JsonObject dbStock, String dbCollection) {
		
		if((stock.getString("title") != null && !stock.getString("title").equals(dbStock.getString("title"))) || 
						(stock.getString("secFileNo") != null && !stock.getString("secFileNo").equals(dbStock.getString("secFileNo")))) {
			System.out.println("There is an update stock news.... "+stock.getString("_id"));
			JsonArray history = dbStock.getJsonArray("history");
			history = history == null ? new JsonArray() : history;
			history.add(dbStock);
			stock.put("history", history);
			dbStock.remove("history");
			mongoLayer.replaceDocuments(dbCollection, stock, stock.getString("_id"));
		}
		
		
	}
}
