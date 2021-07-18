package snir.code.services.otcmarkets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.utils.MessageLog;

public class StockScreener {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	public static MongoLayer MONGO_API = null;
	
	public StockScreener() {
		MongoLayer.getInstance(AppConfig.AppParameters.get("APP"), instance -> {
			try {
				setMongoApi(instance);
			} catch (Exception e) {
				//TODO:  maybe fail start promise we can't get log mongo instance
//				startPromise.fail(e);
			}
		});
		
		
	}

	private void setMongoApi(MongoLayer instance) {
		MONGO_API = instance;
		MONGO_API.createCollection("OTC_Stock");		
	}

	/**
	 * 
	 * @param ctx
	 */
	public void getStock(RoutingContext ctx) {
		
		String url = "www.otcmarkets.com";
		String uri = "/research/stock-screener/api";
		WebClient client = WebClient.create(AppConfig.vertx);
		
		client
		  .get(443, url, uri)
		//  .addQueryParam("market", "10")
		  .addQueryParam("pageSize", "100000")
		  .ssl(true)
		  .send()
		  .onSuccess(response -> setStock(ctx, getStockAsJson(response.bodyAsString())))
		  .onFailure(err ->
		  	MessageLog.sendMessageCode(ctx, MessageKey.GET_STOCK_ERROR, err.getMessage()));
		
		
	}
	
	public void setStock(RoutingContext ctx, JsonObject stockBody) {
		sendStockResult(ctx, stockBody);
	}
	
	private JsonObject getStockAsJson(String stockResult) {
		stockResult = stockResult.replaceAll( "\\\\", "");
		stockResult = stockResult.replaceAll( " \"", "\"");
		stockResult = stockResult.replaceFirst( "\"", "");
		stockResult = stockResult.replaceAll( "}\"", "}");
		
		JsonObject resultObj =  new JsonObject(stockResult);
		
		int size = resultObj.getJsonArray("stocks").size();
		resultObj.put("size", size);
		
		return resultObj;
	}
	
	private void sendStockResult(RoutingContext ctx, JsonObject stockResponcs) {
		MessageLog.sendMessageObject(ctx, MessageKey.GET_STOCK_SUCCESS , stockResponcs, logger);
	}
	
}
