package edu.sc.seis.receiverFunction.web;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.SQLException;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.sc.seis.fissuresUtil.database.ConnMgr;
import edu.sc.seis.fissuresUtil.database.NotFound;
import edu.sc.seis.fissuresUtil.database.event.JDBCEventAccess;
import edu.sc.seis.fissuresUtil.database.network.JDBCChannel;
import edu.sc.seis.receiverFunction.HKStack;
import edu.sc.seis.receiverFunction.SumHKStack;
import edu.sc.seis.receiverFunction.server.JDBCHKStack;
import edu.sc.seis.receiverFunction.server.JDBCRecFunc;
import edu.sc.seis.receiverFunction.server.JDBCSodConfig;
import edu.sc.seis.receiverFunction.server.RecFuncCacheImpl;
import edu.sc.seis.rev.StationLocator;
import edu.sc.seis.rev.velocity.VelocityNetwork;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.database.waveform.JDBCEventChannelStatus;


/**
 * @author crotwell
 * Created on Feb 23, 2005
 */
public class SummaryHKStackImageServlet extends HttpServlet {

    /**
     * @throws SQLException
     * @throws Exception
     * @throws ConfigurationException
     *
     */
    public SummaryHKStackImageServlet() throws SQLException, ConfigurationException, Exception {
        Connection conn = ConnMgr.createConnection();
        jdbcEvent = new JDBCEventAccess(conn);
        jdbcECStatus = new JDBCEventChannelStatus(conn);
        
        JDBCChannel jdbcChannel  = new JDBCChannel(conn);
        JDBCSodConfig jdbcSodConfig = new JDBCSodConfig(conn);
        JDBCRecFunc jdbcRecFunc = new JDBCRecFunc(conn, jdbcEvent, jdbcChannel, jdbcSodConfig, RecFuncCacheImpl.getDataLoc());
        jdbcHKStack = new JDBCHKStack(conn, jdbcEvent, jdbcChannel, jdbcSodConfig, jdbcRecFunc);
    }
    
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        try {
            logger.debug("doGet called");
            int netDbId = new Integer(req.getParameter("netdbid")).intValue();
            VelocityNetwork net = new VelocityNetwork(jdbcHKStack.getJDBCChannel().getStationTable().getNetTable().get(netDbId), netDbId);
            
            // possible that there are multiple stations with the same code
            String staCode = req.getParameter("stacode");
            
            if(req.getParameter("minPercentMatch") == null) { throw new Exception("minPercentMatch param not set"); }
            float minPercentMatch = new Float(req.getParameter("minPercentMatch")).floatValue();

            if(req.getParameter("smallestH") == null) { throw new Exception("smallestH param not set"); }
            float smallestH = new Float(req.getParameter("smallestH")).floatValue();
            
            SumHKStack sumStack = jdbcHKStack.sum(net.getCode(),
                                                  staCode,
                                                  minPercentMatch,
                                                  smallestH);
            logger.debug("finish calc stack summary");
            OutputStream out = res.getOutputStream();
            if (sumStack == null) {
                logger.warn("summary stack is null for "+net.getCode()+"."+staCode);
                return;
            }
            BufferedImage image = sumStack.createStackImage();
            logger.debug("finish create image");
            res.setContentType("image/png");
            ImageIO.write(image, "png", out);
            out.close();
        } catch(NotFound e) {
            OutputStreamWriter writer = new OutputStreamWriter(res.getOutputStream());
            System.out.println("No HKStack found for "+req.getParameter("staCode"));
            writer.write("<html><body><p>No HK stack foundfor  "+req.getParameter("staCode")+"</p></body></html>");
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    

    private StationLocator staLoc;
    
    private JDBCEventAccess jdbcEvent;

    private JDBCEventChannelStatus jdbcECStatus;
    
    private JDBCHKStack jdbcHKStack;
    
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SummaryHKStackImageServlet.class);
    
}