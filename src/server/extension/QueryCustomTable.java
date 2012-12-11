package server.extension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.CinnamonMethod;
import server.dao.CustomTableDAO;
import server.dao.DAOFactory;
import server.data.ResultSetToXml;
import server.data.SqlCustomConn;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.TextResponse;

public class QueryCustomTable extends BaseExtension{

	static {
		/*
		 * Required so BaseExtension can set the API-Class.
		 */
		setExtensionClass(QueryCustomTable.class);
	}
	
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	public CommandRegistry registerApi(CommandRegistry cmdReg) {
		return findAndRegisterMethods(cmdReg, this);
	}
	
	static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
	
	/**
	 * Perform arbitrary SQL statements on a database connection of your choice.<br>
	 * 
	 * <h2>Parameters in HTTP Request</h2>
	 * <ul>
	 * <li>command=querycustomtable</li>
	 * <li>connectionname=name of the registered connection</li>
	 * <li>query=query to execute</li>
	 * <li>ticket=session ticket</li>
	 * </ul>
	 * @return
	 * ?
	 * 
	 * <h2>Needed permissions</h2>
	 * QUERY_CUSTOM_TABLE 
	 *  
	 * @param cmd
	 */
//	@CinnamonMethod
//	public Response queryCustomTable(Map<String,String> cmd) throws SQLException, TransformerException {
//        String connectionName= cmd.get("connectionname");
//        Map<String, SqlCustomConn> customConns = repository.getSqlCustomConns();
//        if( customConns == null
//        		|| customConns.get(connectionName) == null){
//        	CustomTableDAO ctDao = daoFactory.getCustomTableDAO(em);
//        	repository.reloadSqlCustomConnections(ctDao);
//        	// after reload, it will at least contain an empty Map.
//        }
//        log.debug(String.format("Trying: %s %s",repository, connectionName));
//        SqlCustomConn custConn= repository.getSqlCustomConns().get(connectionName);
//        if(custConn == null){
//        	throw new RuntimeException("error.custom_connection.not_found");
//        }
//
//        log.debug("1/5 Starting validation");
//        QueryCustomTableValidator val	= new QueryCustomTableValidator(getUser());
//        val.validateQueryCustomTable(custConn.getAcl());
//
//        log.debug("2/5 Creating Statement");
//        String query	= cmd.get("query");
//        Statement st	= custConn.getConnection().createStatement();
//
//        log.debug("3/5 executing query");
//        ResultSet rs	= st.executeQuery(query);
//
//        log.debug("4/5 converting resultset to xml response");
//        TextResponse resp = new TextResponse(res, (new ResultSetToXml(rs)).geStringRepresentation());
//
//        log.debug("5/5 closing database handles");
//        rs.close();
//        st.close();
//        return resp;
//	}
	
}
