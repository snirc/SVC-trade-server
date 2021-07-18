package snir.code.verticle.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import snir.code.config.AppConfig;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.db.MongoLayer;
import snir.code.services.CommonService;
import snir.code.services.MainService;
import snir.code.services.otcmarkets.StockScreener;
import snir.code.utils.MessageLog;


public final class MainTraideVerticle extends MainAbstractVerticle {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private CommonService commonService;
	private MainService mainService;
	private StockScreener stockScreener;
	
	@Override
	protected void loadRouter() throws Exception {
		System.out.println("rout to ok.....");	
		router.get("/ok").handler(mainService::getOK);
		router.get("/stock").handler(stockScreener::getStock);
		
		initSubVerticles();
	}

	@Override
	protected void loadComponents() throws Exception {
		
		MongoLayer.getInstance("mongo", instance->{
			mongoLayer = instance;
		});
		commonService = new CommonService();
		MessageLog.logMessage(MessageKey.OK,"Init completed", logger);
		
		mainService = new MainService();
		stockScreener = new StockScreener();
		
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
			AppConfig.AppParameters.put("MONGO", "mongo");
			System.out.println("Found Vertical: " + ctClass.getName());			
			AppConfig.runVerticle(verticle);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(500);
		}

	}
}