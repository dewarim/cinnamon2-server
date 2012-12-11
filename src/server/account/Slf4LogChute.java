package server.account;

import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom implementation of LogChute because velocity 1.6 is currently not ready for SLF4J.
 * Code taken from: https://issues.apache.org/jira/browse/VELOCITY-621
 */
public class Slf4LogChute implements LogChute {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void init(RuntimeServices runtimeServices) throws Exception {
    }

    @Override
    public void log(int level, String message) {
        switch (level) {
            case DEBUG_ID:
                logger.debug(message);
                break;
            case INFO_ID:
                logger.info(message);
                break;
            case WARN_ID:
                logger.warn(message);
                break;
            case ERROR_ID:
                logger.error(message);
                break;
        }
    }

    @Override
    public void log(int level, String message, Throwable throwable ) {
        switch (level) {
            case DEBUG_ID:
                logger.debug(message, throwable);
                break;
            case INFO_ID:
                logger.info(message, throwable);
                break;
            case WARN_ID:
                logger.warn(message, throwable);
                break;
            case ERROR_ID:
                logger.error(message, throwable);
                break;
        }
    }

    @Override
    public boolean isLevelEnabled(int level) {
        boolean result = false;
        switch (level) {
            case DEBUG_ID:
                result = logger.isDebugEnabled();
                break;
            case INFO_ID:
                result = logger.isInfoEnabled();
                break;
            case WARN_ID:
                result = logger.isWarnEnabled();
                break;
            case ERROR_ID:
                result = logger.isErrorEnabled();
                break;
        }
        return result;
    }
}
