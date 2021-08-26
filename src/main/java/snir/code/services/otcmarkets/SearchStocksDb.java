package snir.code.services.otcmarkets;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import rx.Single;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.utils.MessageLog;
import snir.code.utils.MongoCollections;

public class SearchStocksDb {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	ArrayList<String> searchCollections = new ArrayList<String>();
	JsonObject searchCollectionsQueries = new JsonObject();
	
	public SearchStocksDb() {
		
		searchCollections.add(MongoCollections.OTC_STOCK_COLLECTION);
		searchCollections.add(MongoCollections.OTC_STOCK_ACTIVITY);
		searchCollections.add(MongoCollections.OTC_STOCK_MOVEMENT);
		searchCollections.add(MongoCollections.OTC_NEWS_COLLECTION);
		searchCollections.add(MongoCollections.OTC_STOCK_FINANCIAL_REPORTS);
		searchCollections.add(MongoCollections.OTC_STOCK_SEC_FILINGS);
		
		setLikeQueries();
		
		
	}
	
	
	
	private void setLikeQueries() {
		JsonObject otcstockCollection = new JsonObject();
		otcstockCollection.put("securityName", "");
		searchCollectionsQueries.put(MongoCollections.OTC_STOCK_COLLECTION, otcstockCollection);
		
		searchCollectionsQueries.put(MongoCollections.OTC_STOCK_ACTIVITY, new JsonObject().put("tierName", ""));
		
		JsonObject otcNewsCollection = new JsonObject();
		otcNewsCollection.put("title", "");
		
		searchCollectionsQueries.put(MongoCollections.OTC_NEWS_COLLECTION, otcNewsCollection);
		
		searchCollectionsQueries.put(MongoCollections.OTC_STOCK_FINANCIAL_REPORTS, new JsonObject().put("title", ""));
		searchCollectionsQueries.put(MongoCollections.OTC_STOCK_SEC_FILINGS, new JsonObject().put("companyName", ""));
	}



	public void getStocksAlerts(RoutingContext ctx) {
		
		MongoCollections.mongoLayer.findSortByParams(MongoCollections.OTC_STOCK_NEWS_ALERTS, new JsonObject(), ctx, -1, "updated", 30);
		
	}

	public void searchBySymbol(RoutingContext ctx) {
		JsonArray searchResultArr = new JsonArray();
		JsonObject searchResultObject = new JsonObject();
		searchCollections.forEach(collection -> {
			searchBySymbol(ctx, searchResultObject,searchResultArr, collection);	
		});
		
		
	}

	public void searchBySymbol(RoutingContext ctx, JsonObject searchResultObject, JsonArray searchResultArr, String collection) {
		
		Single<JsonObject> searchResult = MongoCollections.mongoLayer.findById(collection, ctx.pathParam("id"));
		searchResult.subscribe(result -> {
			JsonObject collectionResult = new JsonObject();
			collectionResult.put(collection, result);
			searchResultObject.put(collection, result);
			searchResultArr.add(searchResultObject);
			if(searchResultObject.size() == searchCollections.size()) {
				JsonObject outResult = new JsonObject();
				outResult.put("result", searchResultArr);
				MessageLog.sendMessageObject(ctx, MessageKey.STOCK_SEARCH_RESULT, outResult, logger);
			}
				
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
		Single<List<JsonObject>> results= MongoCollections.mongoLayer.find(collection, queryObject);
		results.subscribe(allResults -> {
			allResults.forEach(result->{
				result.remove("history");
			});
			searchResultObject.put(collection, allResults);
			if(searchResultObject.size() == searchCollectionsQueries.size())
				MessageLog.sendMessageObject(ctx, MessageKey.STOCK_SEARCH_RESULT, searchResultObject, logger);
		});
	}



	
	
}
