package snir.code.services.otcmarkets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class StockScreener {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	public static MongoLayer mongoLayer = null;
	public static String OTC_STOCK_COLLECTION = "OTC_Stock";
	public static String OTC_STOCK_MOVEMENT = "OTC_Stock_Movement";
	private static long PERIODIC = 60000 * 1;

	public StockScreener() {
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
		mongoLayer = instance;
		mongoLayer.createCollection(OTC_STOCK_COLLECTION);
		mongoLayer.createCollection(OTC_STOCK_MOVEMENT);
		long timerID = AppConfig.vertx.setPeriodic(PERIODIC, new Handler<Long>() {

			@Override
			public void handle(Long aLong) {
				System.out.println("Run stock scan: " + DateUtils.getLastDateTime());
				runStockScan();
			}
		});
	}

	/**
	 * 
	 * @param ctx
	 */
	public void getStock(RoutingContext ctx) {

		MessageLog.sendMessageCode(ctx, MessageKey.GET_STOCK_SUCCESS, logger);

	}

	public void runStockScan() {
		String url = "www.otcmarkets.com";
		String uri = "/research/stock-screener/api";
		WebClient client = WebClient.create(AppConfig.vertx);

		client.get(443, url, uri)
				// .addQueryParam("market", "10")
				.addQueryParam("pageSize", "100000").ssl(true).send()
				.onSuccess(response -> setStock(getStockAsJson(response.bodyAsString())))
				.onFailure(err -> MessageLog.logError(MessageKey.GET_STOCK_ERROR, err.getMessage(), logger));
	}

	/**
	 * 
	 * @param stockBody
	 */
	public void setStock(JsonObject stockBody) {
		Map<String, JsonObject> dbStockMap = new HashMap<String, JsonObject>();
		Single<List<JsonObject>> dbStocks = mongoLayer.find(OTC_STOCK_COLLECTION);
		dbStocks.subscribe(dbStockList -> {
			dbStockList.forEach(dbStockItem -> {
				dbStockMap.put(dbStockItem.getString("_id"), dbStockItem);
			});
			setStock(stockBody, dbStockMap);
		});

	}

	/**
	 * 
	 * @param stockBody
	 * @param dbStockMap
	 */
	public void setStock(JsonObject stockBody, Map<String, JsonObject> dbStockMap) {

		stockBody.getJsonArray("stocks").forEach(item -> {

			JsonObject stock = (JsonObject) item;
			String stockId = stock.getString("securityName");
			stock.put("_id", stockId);
			stock.put("updated", DateUtils.getLastDateTime());
			JsonObject dbStock = dbStockMap.get(stockId);
			if (dbStock == null)
				mongoLayer.insertingDocuments(OTC_STOCK_COLLECTION, stock);
			else
				checkStockCheng(stock, dbStock);

		});
	}

	public void checkStockCheng(JsonObject stock, JsonObject dbStock) {
		double stockPrice = stock.getDouble("price");
		double dbStockPrice = dbStock.getDouble("price");
		double diff = stockPrice - dbStockPrice;
		if (diff != 0) {
			mongoLayer.replaceDocuments(OTC_STOCK_COLLECTION, stock, stock.getString("_id"));
			buySellStock(stock, diff);
		}

	}

	/**
	 * 
	 * @param stock
	 * @param diff
	 */
	private void buySellStock(JsonObject stock, double diff) {
		Single<JsonObject> movementStock = mongoLayer.findById(OTC_STOCK_MOVEMENT, stock.getString("_id"));
		movementStock.subscribe(mStock -> {
			String action = mStock == null ? null : mStock.getString("action");
			String newAction = null;
			if (diff > 0 && !"buy".equals(action)) {
				newAction = "buy";
				stock.put("action", newAction);
			} else if (diff < 0 && "buy".equals(action)) {
				newAction = "sall";
				stock.put("action", newAction);
			}
			updateMovmentStock(stock, diff, mStock, newAction);
		});

	}

	private void updateMovmentStock(JsonObject stock, double diff, JsonObject mStock, String newAction) {
		String updateTime = DateUtils.getLastDateTime();
		Single<JsonObject> movementStock = mongoLayer.findById(OTC_STOCK_MOVEMENT, stock.getString("_id"));

		try {
			mStock = mStock == null ? new JsonObject() : mStock;
			mStock.put("_id", stock.getString("_id"));
			mStock.put("price", stock.getDouble("price"));
			mStock.put("volumeChange", stock.getDouble("volumeChange"));
			mStock.put("AppMovement", diff);
			mStock.put("updated", updateTime);
			JsonArray historyUpdate = mStock.getJsonArray("history") == null ? new JsonArray()
					: mStock.getJsonArray("history");
			JsonObject updateSetock = new JsonObject();
			updateSetock.put("price", stock.getDouble("price"));
			updateSetock.put("volumeChange", stock.getDouble("volumeChange"));
			updateSetock.put("AppMovement", diff);
			updateSetock.put("updated", updateTime);
			if (newAction != null) {
				stock.put("action", newAction);
				mStock.put("action", newAction);
				updateSetock.put("action", newAction);
			}
			historyUpdate.add(updateSetock);
			mStock.put("history", historyUpdate);
			if (historyUpdate.size() > 1)
				mongoLayer.replaceDocuments(OTC_STOCK_MOVEMENT, mStock, stock.getString("_id"));
			else
				mongoLayer.insertingDocuments(OTC_STOCK_MOVEMENT, mStock);
		} catch (Exception e) {
			MessageLog.logError(MessageKey.STOCK_MOVEMENT_ERROR, e, logger);
		}

	}

	private boolean getOwner() {

		return false;
	}

	private JsonObject getStockAsJson(String stockResult) {
		stockResult = stockResult.replaceAll("\\\\", "");
		stockResult = stockResult.replaceAll(" \"", "\"");
		stockResult = stockResult.replaceFirst("\"", "");
		stockResult = stockResult.replaceAll("}\"", "}");

		JsonObject resultObj = new JsonObject(stockResult);

		int size = resultObj.getJsonArray("stocks").size();
		resultObj.put("size", size);

		return resultObj;
	}

	private void sendStockResult(RoutingContext ctx, JsonObject stockResponcs) {
		MessageLog.sendMessageObject(ctx, MessageKey.GET_STOCK_SUCCESS, stockResponcs, logger);
	}

}
