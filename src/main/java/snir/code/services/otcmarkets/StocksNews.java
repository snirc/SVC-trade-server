package snir.code.services.otcmarkets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import rx.Single;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.utils.DateUtils;
import snir.code.utils.MessageLog;
import snir.code.utils.MongoCollections;

public class StocksNews {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	
	private static long PERIODIC = 60000 * 5;
	private String STOCK_LIST_TAG_NAME = "records";

	public StocksNews() {
		runScunPeriodic();
	}

	private void runScunPeriodic() {
		
		AppConfig.vertx.setPeriodic(PERIODIC, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run News stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/dns/tier/news", MongoCollections.OTC_NEWS_COLLECTION);

			}

		});

		AppConfig.vertx.setPeriodic(PERIODIC + 2000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run sec-filings stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/sec-filings", MongoCollections.OTC_STOCK_SEC_FILINGS);

			}

		});

		AppConfig.vertx.setPeriodic(PERIODIC + 4000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run financial-report stock scan: " + DateUtils.getLastDateTime());

				scanNewsStocks("/otcapi/company/financial-report", MongoCollections.OTC_STOCK_FINANCIAL_REPORTS);

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
			Single<JsonObject> dataStocks = MongoCollections.mongoLayer.findById(dbCollection, stock.getString("_id"));
			dataStocks.subscribe(dbStock -> {
				if (dbStock == null) { 
					System.out.println("There is a new stock news.... "+stockId);
					MongoCollections.mongoLayer.insertingDocuments(dbCollection, stock);
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
			MongoCollections.mongoLayer.replaceDocuments(dbCollection, stock, stock.getString("_id"));
			
			String newsMessage = stock.getString("title") != null ? stock.getString("title") : stock.getString("secFileNo");
			addNewsAlert(stock.getString("_id"), newsMessage );
		}
		
		
	}

	private void addNewsAlert(String id, String newsMessage) {
		JsonObject newsAlertObj = new JsonObject();
		newsAlertObj.put("_id", id);
		newsAlertObj.put("title", newsMessage);
		
		Single<JsonObject> searchResult = MongoCollections.mongoLayer.findById(MongoCollections.OTC_STOCK_COLLECTION, id);
		searchResult.subscribe(result -> {
			newsAlertObj.put("market", result.getString("market"));
			newsAlertObj.put("price", result.getDouble("price"));
			newsAlertObj.put("volume", result.getDouble("volume"));
			newsAlertObj.put("volumeChange", result.getDouble("volumeChange"));
			newsAlertObj.put("updated", DateUtils.getLastDateTime());
			//newsAlertObj.put("updatedMill", System.currentTimeMillis());
			MongoCollections.mongoLayer.insertingDocuments(MongoCollections.OTC_STOCK_NEWS_ALERTS, newsAlertObj);
		});
	}
}
