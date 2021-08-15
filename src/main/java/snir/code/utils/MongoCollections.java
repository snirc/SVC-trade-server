package snir.code.utils;

import snir.code.config.AppConfig;
import snir.code.db.MongoLayer;

public class MongoCollections {

	public static String OTC_STOCK_COLLECTION = "OTC_Stock";
	public static String OTC_STOCK_MOVEMENT = "OTC_Stock_Movement";
	public static String OTC_STOCK_ACTIVITY = "OTC_Stock_Activities";

	public static String OTC_NEWS_COLLECTION = "OTC_Stock_News";
	public static String OTC_STOCK_FINANCIAL_REPORTS = "OTC_Stock_Report";
	public static String OTC_STOCK_SEC_FILINGS = "OTC_Stock_Sec_Filing";
	
	public static String OTC_STOCK_NEWS_ALERTS = "OTC_Stock_News_Alerts";

	public static MongoLayer mongoLayer = null;

	public MongoCollections(MongoLayer instance) {

		mongoLayer = instance;

		mongoLayer.createCollection(MongoCollections.OTC_NEWS_COLLECTION);
		mongoLayer.createCollection(MongoCollections.OTC_STOCK_FINANCIAL_REPORTS);
		mongoLayer.createCollection(MongoCollections.OTC_STOCK_SEC_FILINGS);

		mongoLayer.createCollection(MongoCollections.OTC_STOCK_COLLECTION);
		mongoLayer.createCollection(MongoCollections.OTC_STOCK_MOVEMENT);
		mongoLayer.createCollection(MongoCollections.OTC_STOCK_ACTIVITY);
		
		mongoLayer.createCollection(MongoCollections.OTC_STOCK_NEWS_ALERTS);
	}
}
