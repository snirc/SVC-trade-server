package snir.code.services.otcmarkets;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import snir.code.db.MongoLayer;

public class StoksNews {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	public static MongoLayer mongoLayer = null;
	public static String OTC_NEWS_COLLECTION = "OTC_Stock_News";
	public static String OTC_STOCK_FINANCIAL_REPORTS = "OTC_Stock_Report";
	public static String OTC_STOCK_SEC_FILINGS = "OTC_Stock_Sec_Filing";
	private static long PERIODIC = 60000 * 4;
	private Map<String, String> STOCK_LIST_TAG_NAME = new HashMap();
	
	
}
