package snir.code.services.otcmarkets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.impl.protocol.codec.DynamicConfigAddCountDownLatchConfigCodec;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import rx.Single;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.utils.DateUtils;
import snir.code.utils.MessageLog;
import snir.code.utils.MongoCollections;

public class StockScunner {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private static long PERIODIC = 60000 * 11;
	private Map<String, String> STOCK_LIST_TAG_NAME = new HashMap();

	public StockScunner() {

		STOCK_LIST_TAG_NAME.put(MongoCollections.OTC_STOCK_COLLECTION, "stocks");
		STOCK_LIST_TAG_NAME.put(MongoCollections.OTC_STOCK_ACTIVITY, "records");
		 runScunner();
	}

	private void runScunner() {
		
		/*
		 * //System.out.println("Run stock scan: " + DateUtils.getLastDateTime());
		 * System.out.println("Run Activ stock scan: " + DateUtils.getLastDateTime());
		 * //runStockScan(); scanActivityStocks("/otcapi/market-data/active/current");
		 * scanActivityStocks("/otcapi/market-data/advancers/current");
		 * scanActivityStocks("/otcapi/market-data/decliners/current");
		 */
		
		AppConfig.vertx.setPeriodic(PERIODIC, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run Activ stock scan: " + DateUtils.getLastDateTime());
				//runStockScan();
				scanActivityStocks("/otcapi/market-data/active/current");
				
			}
			
		});
		
		AppConfig.vertx.setPeriodic(PERIODIC+2000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run advancers stock scan: " + DateUtils.getLastDateTime());
				
				scanActivityStocks("/otcapi/market-data/advancers/current");
				
			}
			
		});
		
		AppConfig.vertx.setPeriodic(PERIODIC+4000, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run decliners stock scan: " + DateUtils.getLastDateTime());
				
				scanActivityStocks("/otcapi/market-data/decliners/current");
				
			}
			
		});

		AppConfig.vertx.setPeriodic(PERIODIC*4, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run stock scan: " + DateUtils.getLastDateTime());
				//runStockScan();
				runStockScan();
				
			}
			
		});
	}

	/**
	 * 
	 * @param ctx
	 */
	public void getStock(RoutingContext ctx) {

		MessageLog.sendMessageCode(ctx, MessageKey.GET_STOCK_SUCCESS, "OK");

	}

	public void runStockScan() {
		String url = "www.otcmarkets.com";
		String uri = "/research/stock-screener/api";
		WebClient client = WebClient.create(AppConfig.vertx);

		client.get(443, url, uri)
				// .addQueryParam("market", "10")
				.addQueryParam("pageSize", "100000").ssl(true).send()
				.onSuccess(response -> setStock(getStockAsJson(response.bodyAsString()), MongoCollections.OTC_STOCK_COLLECTION))
				.onFailure(err -> MessageLog.logError(MessageKey.GET_STOCK_ERROR, err.getMessage(), logger));
	}

	public void scanActivityStocks(String activity) {
		// Request URL:
		// https://backend.otcmarkets.com/otcapi/market-data/active/current?page=1&pageSize=10

		String url = "backend.otcmarkets.com";
		String uri = activity;
		WebClient client = WebClient.create(AppConfig.vertx);

		client.get(443, url, uri).addQueryParam("page", "1").addQueryParam("pageSize", "50").ssl(true).send()
				.onSuccess(response -> setStock(new JsonObject(response.bodyAsString()), MongoCollections.OTC_STOCK_ACTIVITY))
				.onFailure(err -> MessageLog.logError(MessageKey.GET_STOCK_ERROR, err.getMessage(), logger));
	}

	/**
	 * 
	 * @param stockBody
	 */
	public void setStock(JsonObject stockBody, String dbCollection) {
		Map<String, JsonObject> dbStockMap = new HashMap<String, JsonObject>();
		Single<List<JsonObject>> dbStocks = MongoCollections.mongoLayer.find(dbCollection);
		dbStocks.subscribe(dbStockList -> {
			dbStockList.forEach(dbStockItem -> {
				dbStockMap.put(dbStockItem.getString("_id"), dbStockItem);
			});
			setStock(stockBody, dbStockMap, dbCollection);
		});

	}

	/**
	 * 
	 * @param stockBody
	 * @param dbStockMap
	 */
	public void setStock(JsonObject stockBody, Map<String, JsonObject> dbStockMap, String dbCollection) {

		AtomicReference<Integer> counter = new AtomicReference<>(1);
		stockBody.getJsonArray(STOCK_LIST_TAG_NAME.get(dbCollection)).forEach(item -> {
			int delay = counter.getAndUpdate(value -> value + 10);
			AppConfig.vertx.setTimer(delay, l -> {
				JsonObject stock = (JsonObject) item;
				String stockId = stock.getString("symbol");
				stock.put("_id", stockId);
				stock.put("updated", DateUtils.getLastDateTime());
				stock.put("updatedMill", System.currentTimeMillis());
				JsonObject dbStock = dbStockMap.get(stockId);
				if (dbStock == null) {
					System.out.println("There is a new stock.... ");
					MongoCollections.mongoLayer.insertingDocuments(dbCollection, stock);
				} else
					checkStockCheng(stock, dbStock, dbCollection);

			});
		});

	}

	public void checkStockCheng(JsonObject stock, JsonObject dbStock, String dbCollection) {
		double stockPrice = stock.getDouble("price");
		double dbStockPrice = dbStock.getDouble("price");
		double diff = stockPrice - dbStockPrice;

		if (diff != 0) {
			System.out.println("There is an update for stock: "+ dbStock.getString("_id") +" - "+ diff);
			MongoCollections.mongoLayer.replaceDocuments(dbCollection, stock, stock.getString("_id"));
			if (dbCollection != MongoCollections.OTC_STOCK_COLLECTION)
				buySellStock(stock, diff);
		}

	}

	/**
	 * 
	 * @param stock
	 * @param diff
	 */
	private void buySellStock(JsonObject stock, double diff) {
		Single<JsonObject> movementStock = MongoCollections.mongoLayer.findById(MongoCollections.OTC_STOCK_MOVEMENT, stock.getString("_id"));
		movementStock.subscribe(mStock -> {
			if (mStock == null)
				updateMovmentStock(stock, diff, mStock, null);
			else if (mStock.getDouble("price") != stock.getDouble("price")) {
				String action = mStock == null ? null : mStock.getString("action");
				String newAction = null;
				if (diff > 0 && !"buy".equals(action)) {
					newAction = "buy";
					mStock.put("buyPrice", stock.getDouble("price"));
					mStock.put("action", newAction);
				} else if (diff < 0 && "buy".equals(action)) {
					newAction = "sell";
					mStock.put("action", newAction);
					mStock.put("sellPrice", stock.getDouble("price"));
				}
				updateMovmentStock(stock, diff, mStock, newAction);
			}

		});

	}

	private void updateMovmentStock(JsonObject stock, double diff, JsonObject mStock, String newAction) {
		String updateTime = DateUtils.getLastDateTime();
		Single<JsonObject> movementStock = MongoCollections.mongoLayer.findById(MongoCollections.OTC_STOCK_MOVEMENT, stock.getString("_id"));

		try {
			mStock = mStock == null ? new JsonObject() : mStock;
			mStock.put("_id", stock.getString("_id"));
			mStock.put("price", stock.getDouble("price"));
			mStock.put("volumeChange", stock.getDouble("volumeChange"));
			mStock.put("AppMovement", diff);
			mStock.put("updated", updateTime);
			mStock.put("updatedMill", System.currentTimeMillis());
			JsonArray historyUpdate = mStock.getJsonArray("history") == null ? new JsonArray()
					: mStock.getJsonArray("history");
			JsonObject updateSetock = new JsonObject();
			updateSetock.put("price", stock.getDouble("price"));
		//	updateSetock.put("volumeChange", stock.getDouble("volumeChange"));
			updateSetock.put("AppMovement", diff);
			updateSetock.put("updated", updateTime);
			updateSetock.put("updatedMill", System.currentTimeMillis());
			if (newAction != null) {
				stock.put("action", newAction);
				mStock.put("action", newAction);
				updateSetock.put("action", newAction);
			}
			historyUpdate.add(updateSetock);
			mStock.put("history", historyUpdate);
			if (historyUpdate.size() > 1)
				MongoCollections.mongoLayer.replaceDocuments(MongoCollections.OTC_STOCK_MOVEMENT, mStock, stock.getString("_id"));
			else
				MongoCollections.mongoLayer.insertingDocuments(MongoCollections.OTC_STOCK_MOVEMENT, mStock);
		} catch (Exception e) {
			MessageLog.logError(MessageKey.STOCK_MOVEMENT_ERROR, e, logger);
		}

	}

	private boolean getOwner() {

		return false;
	}

	private JsonObject getStockAsJson(String stockResult) {
		try {
			stockResult = stockResult.replaceAll("\\\\", "");
			stockResult = stockResult.replaceAll(" \"", "\"");
			stockResult = stockResult.replaceFirst("\"", "");
			stockResult = stockResult.replaceAll("}\"", "}");

			JsonObject resultObj = new JsonObject(stockResult);

			int size = resultObj.getJsonArray("stocks").size();
			resultObj.put("size", size);

			return resultObj;
		} catch (Exception e) {
			MessageLog.logError(MessageKey.GET_DB_STOCK_ERROR, stockResult, logger, e);
		}
		return new JsonObject();
	}

	private void sendStockResult(RoutingContext ctx, JsonObject stockResponcs) {
		MessageLog.sendMessageObject(ctx, MessageKey.GET_STOCK_SUCCESS, stockResponcs, logger);
	}

}
