package edu.sc.seis.receiverFunction.web;

import java.awt.Dimension;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import edu.iris.Fissures.AuditInfo;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.ChannelImpl;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.IfReceiverFunction.CachedResult;
import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.fissuresUtil.database.ConnMgr;
import edu.sc.seis.fissuresUtil.database.event.JDBCEventAccess;
import edu.sc.seis.fissuresUtil.database.network.JDBCChannel;
import edu.sc.seis.fissuresUtil.dataset.DataSetEventOrganizer;
import edu.sc.seis.fissuresUtil.display.BackAzimuthDisplay;
import edu.sc.seis.fissuresUtil.display.MicroSecondTimeRange;
import edu.sc.seis.fissuresUtil.display.RecordSectionDisplay;
import edu.sc.seis.fissuresUtil.display.registrar.PhaseAlignedTimeConfig;
import edu.sc.seis.fissuresUtil.xml.DataSetSeismogram;
import edu.sc.seis.fissuresUtil.xml.MemoryDataSetSeismogram;
import edu.sc.seis.receiverFunction.compare.JDBCStationResult;
import edu.sc.seis.receiverFunction.compare.JDBCStationResultRef;
import edu.sc.seis.receiverFunction.server.CachedResultPlusDbId;
import edu.sc.seis.receiverFunction.server.JDBCHKStack;
import edu.sc.seis.receiverFunction.server.JDBCRecFunc;
import edu.sc.seis.receiverFunction.server.JDBCSodConfig;
import edu.sc.seis.receiverFunction.server.JDBCSummaryHKStack;
import edu.sc.seis.rev.RevUtil;
import edu.sc.seis.rev.Revlet;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.velocity.event.VelocityEvent;
import edu.sc.seis.sod.velocity.network.VelocityNetwork;

public class RecordSectionImage extends HttpServlet {

    public RecordSectionImage() throws SQLException, ConfigurationException,
            Exception {
        DATA_LOC = Start.getDataLoc();
        Connection conn = ConnMgr.createConnection();
        jdbcEventAccess = new JDBCEventAccess(conn);
        jdbcChannel = new JDBCChannel(conn);
        jdbcSodConfig = new JDBCSodConfig(conn);
        jdbcRecFunc = new JDBCRecFunc(conn,
                                      jdbcEventAccess,
                                      jdbcChannel,
                                      jdbcSodConfig,
                                      DATA_LOC);
        jdbcHKStack = new JDBCHKStack(conn,
                                      jdbcEventAccess,
                                      jdbcChannel,
                                      jdbcSodConfig,
                                      jdbcRecFunc);
        jdbcSummaryHKStack = new JDBCSummaryHKStack(jdbcHKStack);
        jdbcStationResult = new JDBCStationResult(jdbcChannel.getNetworkTable(),
                                                  new JDBCStationResultRef(conn));
    }

    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        try {
            VelocityNetwork net = Start.getNetwork(req, jdbcHKStack.getJDBCChannel()
                                                   .getStationTable()
                                                   .getNetTable());
            int netDbId = net.getDbId();
            String staCode = RevUtil.get("stacode", req);

            float gaussianWidth = RevUtil.getFloat("gaussian", req, Start.getDefaultGaussian());
            float minPercentMatch = RevUtil.getFloat("minPercentMatch", req, Start.getDefaultMinPercentMatch());
            TimeInterval prePhase = new TimeInterval(RevUtil.getFloat("prePhase",
                                                                      req,
                                                                      10),
                                                     UnitImpl.SECOND);
            TimeInterval window = new TimeInterval(RevUtil.getFloat("window",
                                                                    req,
                                                                    120),
                                                   UnitImpl.SECOND);
            CachedResultPlusDbId[] results;
            synchronized(jdbcRecFunc.getConnection()) {
                results = jdbcRecFunc.getSuccessful(netDbId,
                                                              staCode,
                                                              gaussianWidth,
                                                              minPercentMatch);
            }
            DataSetEventOrganizer organizer = new DataSetEventOrganizer();
            DataSetSeismogram[] itrDSS = new DataSetSeismogram[results.length];
            for(int i = 0; i < itrDSS.length; i++) {
                CachedResult cr = results[i].getCachedResult();
                itrDSS[i] = new MemoryDataSetSeismogram((LocalSeismogramImpl)cr.radial);
                CacheEvent cEvent = new CacheEvent(cr.event_attr, cr.prefOrigin);
                VelocityEvent event = new VelocityEvent(cEvent);
                itrDSS[i].setName(event.getTime());
                organizer.addSeismogram(itrDSS[i], event, emptyAudit);
                Channel chan = cr.channels[2];
                chan = new ChannelImpl(cr.radial.channel_id,
                                       chan.name, chan.an_orientation, chan.sampling_info, chan.effective_time, chan.my_site);
                organizer.addChannel(chan, event, emptyAudit);
                Channel outchan = itrDSS[i].getChannel();
            }
            
            int xdim = RevUtil.getInt("xdim", req, xdimDefault);
            int ydim = RevUtil.getInt("ydim", req, ydimDefault);
            Dimension dim = new Dimension(xdim, ydim);
            res.setContentType("image/png");
            OutputStream out = res.getOutputStream();
            RecordSectionDisplay disp;
            String recordSectionType = RevUtil.get("type", req, "dist");
            if (recordSectionType.equals("dist")) {
                disp = new RecordSectionDisplay();
            } else if (recordSectionType.equals("baz")) {
                disp = new BackAzimuthDisplay();
            } else {
                throw new RuntimeException("Unknown type: "+recordSectionType);
            }
            disp.setMinSeisPixelHeight(RevUtil.getInt("minSeisHeight",req, 80));
            PhaseAlignedTimeConfig timeConfig = new PhaseAlignedTimeConfig("ttp");
            disp.setTimeConfig(timeConfig);
            disp.add(itrDSS);
            MicroSecondTimeRange mstr = disp.getTimeConfig().getTime();
            disp.getTimeConfig()
                    .shaleTime(prePhase.divideBy(mstr.getInterval())
                                       .getValue(SEC_PER_SEC),
                               1);
            disp.getTimeConfig().shaleTime(0,
                                           window.divideBy(mstr.getInterval())
                                                   .getValue(SEC_PER_SEC));
            disp.outputToPNG(out, dim);
            out.close();
        } catch(EOFException e) {
            // silently eat these as they just mean remote user has closed connection
        } catch(Throwable e) {
            Revlet.sendToGlobalExceptionHandler(req, e);
            throw new ServletException(e);
        }
    }

    public void destroy() {
        try {
            Connection conn = jdbcEventAccess.getConnection();
            if (conn != null) {conn.close();}
        } catch(SQLException e) {
            // oh well
        }
        super.destroy();
    }
    
    String DATA_LOC;

    JDBCEventAccess jdbcEventAccess;

    JDBCChannel jdbcChannel;

    JDBCHKStack jdbcHKStack;

    JDBCSummaryHKStack jdbcSummaryHKStack;

    JDBCRecFunc jdbcRecFunc;

    JDBCSodConfig jdbcSodConfig;

    JDBCStationResult jdbcStationResult;

    AuditInfo[] emptyAudit = new AuditInfo[0];

    public static int xdimDefault = 800;

    public static int ydimDefault = 800;

    private static final UnitImpl SEC_PER_SEC = UnitImpl.divide(UnitImpl.SECOND,
                                                                UnitImpl.SECOND);
    
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(RecordSectionImage.class);
}
