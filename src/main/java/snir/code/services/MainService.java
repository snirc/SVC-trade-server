package snir.code.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import snir.code.config.MessageConfig.MessageKey;
import snir.code.utils.MessageLog;

public class MainService {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public void getOK(RoutingContext ctx) {
		try {
			
			MessageLog.sendMessageObject(ctx, MessageKey.OK, "Gooood!", logger);
			
		} catch (Exception e) {
			MessageLog.sendMessageCode(ctx, MessageKey.GENERAL_ERROR, e.getMessage());
		}
	}
}
