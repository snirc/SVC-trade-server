package snir.code.services.otcmarkets;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import rx.Single;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.utils.MessageLog;

public class SearchStocksDb {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	public static MongoLayer mongoLayer = null;
	ArrayList<String> searchCollections = new ArrayList<String>();
	JsonObject searchCollectionsQueries = new JsonObject();
	
	public SearchStocksDb() {
		
		searchCollections.add(StockScunner.OTC_STOCK_COLLECTION);
		searchCollections.add(StockScunner.OTC_STOCK_ACTIVITY);
		searchCollections.add(StockScunner.OTC_STOCK_MOVEMENT);
		searchCollections.add(StocksNews.OTC_NEWS_COLLECTION);
		searchCollections.add(StocksNews.OTC_STOCK_FINANCIAL_REPORTS);
		searchCollections.add(StocksNews.OTC_STOCK_SEC_FILINGS);
		
		setLikeQueries();
		
		MongoLayer.getInstance(AppConfig.AppParameters.get("APP"), instance -> {
			try {
				mongoLayer = instance;
			} catch (Exception e) {
				// TODO: maybe fail start promise we can't get log mongo instance
//				startPromise.fail(e);
			}
		});
	}
	
	
	
	private void setLikeQueries() {
		JsonObject otcstockCollection = new JsonObject();
		otcstockCollection.put("securityName", "");
		searchCollectionsQueries.put(StockScunner.OTC_STOCK_COLLECTION, otcstockCollection);
		
		searchCollectionsQueries.put(StockScunner.OTC_STOCK_ACTIVITY, new JsonObject().put("tierName", ""));
		
		JsonObject otcNewsCollection = new JsonObject();
		otcNewsCollection.put("title", "");
		searchCollectionsQueries.put(StocksNews.OTC_NEWS_COLLECTION, otcNewsCollection);
		
		searchCollectionsQueries.put(StocksNews.OTC_STOCK_FINANCIAL_REPORTS, new JsonObject().put("title", ""));
		searchCollectionsQueries.put(StocksNews.OTC_STOCK_SEC_FILINGS, new JsonObject().put("companyName", ""));
	}



	public void searchBySymbol(RoutingContext ctx) {
		
		JsonObject searchResultObject = new JsonObject();
		searchCollections.forEach(collection -> {
			searchBySymbol(ctx, searchResultObject, collection);	
		});
		
		
	}



	private void searchBySymbol(RoutingContext ctx, JsonObject searchResultObject, String collection) {
		
		Single<JsonObject> searchResult = mongoLayer.findById(collection, ctx.pathParam("id"));
		searchResult.subscribe(result -> {
			searchResultObject.put(collection, result);
			if(searchResultObject.size() == searchCollections.size())
				MessageLog.sendMessageObject(ctx, MessageKey.STOCK_SEARCH_RESULT, searchResultObject, logger);
		});
		
	}
	
	
	public void searchByLike(RoutingContext ctx) {
		//{securityName:{'$regex' : 'city', '$options' : 'i'}}
		JsonObject searchResultObject = new JsonObject();
		
		searchCollectionsQueries.fieldNames().forEach(collection -> {
			JsonObject queryObject = searchCollectionsQueries.getJsonObject(collection);
			String likeQlue = ctx.pathParam("regex");
			queryObject.forEach(fieldName -> {
				JsonObject reg = new JsonObject();
				reg.put("$regex", likeQlue);
				reg.put("$options", "i");
				fieldName.setValue(reg);
				
			});
			
			searchByLike(ctx, searchResultObject, collection, queryObject);	
		});

		
	}



	private void searchByLike(RoutingContext ctx, JsonObject searchResultObject, String collection,
			JsonObject queryObject) {
		Single<List<JsonObject>> results= mongoLayer.find(collection, queryObject);
		results.subscribe(allResults -> {
			searchResultObject.put(collection, allResults);
			if(searchResultObject.size() == searchCollectionsQueries.size())
				MessageLog.sendMessageObject(ctx, MessageKey.STOCK_SEARCH_RESULT, searchResultObject, logger);
		});
	}



	
	
}
