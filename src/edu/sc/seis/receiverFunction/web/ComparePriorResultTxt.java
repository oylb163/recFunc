package edu.sc.seis.receiverFunction.web;

import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.sc.seis.rev.RevletContext;
import edu.sc.seis.sod.ConfigurationException;


/**
 * @author crotwell
 * Created on Mar 31, 2005
 */
public class ComparePriorResultTxt extends ComparePriorResult {

    public ComparePriorResultTxt() throws SQLException, ConfigurationException,
            Exception {
        super();
        // TODO Auto-generated constructor stub
    }

    public ComparePriorResultTxt(String databaseURL, String dataloc)
            throws SQLException, ConfigurationException, Exception {
        super(databaseURL, dataloc);
        // TODO Auto-generated constructor stub
    }
    
    
    public RevletContext getContext(HttpServletRequest req,
                                    HttpServletResponse res) throws Exception {
        RevletContext rc = super.getContext(req, res);
        res.setContentType("text/plain");
        return rc; 
    }
    public String getVelocityTemplate() {
        return "comparePriorResultTxt.vm";
    }
}