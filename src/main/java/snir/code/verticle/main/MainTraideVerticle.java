package snir.code.verticle.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.services.CommonService;
import snir.code.services.MainService;
import snir.code.services.otcmarkets.SearchStocksDb;
import snir.code.services.otcmarkets.StockScunner;
import snir.code.services.otcmarkets.StocksNews;
import snir.code.utils.MessageLog;
import snir.code.utils.MongoCollections;


public final class MainTraideVerticle extends MainAbstractVerticle {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private CommonService commonService;
	private MainService mainService;
	private StockScunner stockScreener;
	private StocksNews stocksNews;
	private SearchStocksDb searchStocks;
	
	@Override
	protected void loadRouter() throws Exception {
		System.out.println("rout to ok.....");	
		router.get("/ok").handler(mainService::getOK);
		router.get("/stock").handler(stockScreener::getStock);
		router.get("/stock/search/:id").handler(searchStocks::searchBySymbol);
		router.get("/stock/search/like/:regex").handler(searchStocks::searchByLike);
		router.get("/stock/alerts").handler(searchStocks::getStocksAlerts);
		
		initSubVerticles();
	}

	@Override
	protected void loadComponents() throws Exception {
		
		MongoLayer.getInstance(AppConfig.AppParameters.get("APP"), instance->{
			new MongoCollections(instance);
			commonService = new CommonService();
			MessageLog.logMessage(MessageKey.OK,"Init completed", logger);
			
			mainService = new MainService();
			stockScreener = new StockScunner();
			stocksNews = new StocksNews();
			searchStocks = new SearchStocksDb();
		});
		
		
	}

	@Override
	protected void addUriRoles() throws Exception {
		// set all rout in map
		MessageLog.logMessage(MessageKey.OK,"URI Roles loaded", logger);
	}

	private void initSubVerticles() throws Exception {

	}

	
	public static void main(String[] args) {
		try {
			System.setProperty("vertx.logger-delegate-factory-class-name",
					"io.vertx.core.logging.Log4j2LogDelegateFactory");
			System.out.println("Parameters______________");			
			String verticleName = AppConfig.AppParameters.getOrDefault("Verticle", MainTraideVerticle.class.getName());
			
			Class<?> ctClass = Class.forName(verticleName);
			AbstractVerticle verticle = (AbstractVerticle) ctClass.getDeclaredConstructor().newInstance();
			AppConfig.AppParameters.put("APP", "trade-server");
			AppConfig.AppParameters.put("PORT", "8181");
			//AppConfig.AppParameters.put("MONGO", "mongo-local");
			AppConfig.AppParameters.put("MONGO", "mongo-atlas");
			AppConfig.AppParameters.put("MONGO_PORT", "8087");;
			AppConfig.setArgs(args);
			System.out.println("Found Vertical: " + ctClass.getName());			
			AppConfig.runVerticle(verticle);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(500);
		}

	}
}