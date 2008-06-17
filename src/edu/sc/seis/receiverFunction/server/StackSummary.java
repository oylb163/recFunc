package edu.sc.seis.receiverFunction.server;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.apache.log4j.PropertyConfigurator;
import edu.iris.Fissures.FissuresException;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.IfNetwork.Network;
import edu.iris.Fissures.IfNetwork.NetworkAttr;
import edu.iris.Fissures.IfNetwork.NetworkId;
import edu.iris.Fissures.IfNetwork.Station;
import edu.iris.Fissures.IfNetwork.StationId;
import edu.iris.Fissures.model.QuantityImpl;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.ChannelIdUtil;
import edu.iris.Fissures.network.NetworkIdUtil;
import edu.iris.Fissures.network.StationIdUtil;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.fissuresUtil.database.ConnMgr;
import edu.sc.seis.fissuresUtil.database.NotFound;
import edu.sc.seis.fissuresUtil.database.event.JDBCEventAccess;
import edu.sc.seis.fissuresUtil.database.network.JDBCChannel;
import edu.sc.seis.fissuresUtil.database.network.JDBCNetwork;
import edu.sc.seis.fissuresUtil.database.network.JDBCStation;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.fissuresUtil.simple.TimeOMatic;
import edu.sc.seis.receiverFunction.HKStack;
import edu.sc.seis.receiverFunction.StackComplexity;
import edu.sc.seis.receiverFunction.SumHKStack;
import edu.sc.seis.receiverFunction.compare.StationResult;
import edu.sc.seis.receiverFunction.crust2.Crust2Profile;
import edu.sc.seis.receiverFunction.web.Start;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.status.FissuresFormatter;
import edu.sc.seis.sod.velocity.network.VelocityNetwork;

/**
 * @author crotwell Created on Oct 7, 2004
 */
public class StackSummary {

    public StackSummary(Connection conn, Properties props) throws IOException,
            SQLException, ConfigurationException, TauModelException, Exception {
        JDBCEventAccess jdbcEventAccess = new JDBCEventAccess(conn);
        JDBCChannel jdbcChannel = new JDBCChannel(conn);
        JDBCSodConfig jdbcSodConfig = new JDBCSodConfig(conn);
        JDBCRecFunc jdbcRecFunc = new JDBCRecFunc(conn,
                                                  jdbcEventAccess,
                                                  jdbcChannel,
                                                  jdbcSodConfig,
                                                  props.getProperty("cormorant.servers.ears.dataloc",
                                                                    RecFuncCacheImpl.getDataLoc()));
        jdbcHKStack = new JDBCHKStack(conn,
                                      jdbcEventAccess,
                                      jdbcChannel,
                                      jdbcSodConfig,
                                      jdbcRecFunc);
        jdbcSummary = new JDBCSummaryHKStack(jdbcHKStack);
        jdbcStackComplexity = new JDBCStackComplexity(jdbcSummary);
        jdbcRejectMax = new JDBCRejectedMaxima(conn);
    }

    public void createSummary(String net,
                              float gaussianWidth,
                              float minPercentMatch,
                              QuantityImpl smallestH,
                              boolean doBootstrap,
                              boolean usePhaseWeight) throws FissuresException,
            NotFound, IOException, SQLException, TauModelException {
        JDBCStation jdbcStation = jdbcHKStack.getJDBCChannel()
                .getSiteTable()
                .getStationTable();
        JDBCNetwork jdbcNetwork = jdbcStation.getNetTable();
        NetworkId[] netId;
        if(net.equals("-all")) {
            netId = jdbcNetwork.getAllNetworkIds();
        } else {
            netId = new NetworkId[] {Start.getNetwork(net, jdbcNetwork)
                    .get_id()};
        }
        for(int i = 0; i < netId.length; i++) {
            Station[] station = jdbcStation.getAllStations(netId[i]);
            for(int j = 0; j < station.length; j++) {
                processStation(station[j],
                               gaussianWidth,
                               minPercentMatch,
                               smallestH,
                               doBootstrap,
                               usePhaseWeight);
            }
        }
        TimeOMatic.print("Time for network: " + net);
    }

    void processStation(Station station,
                        float gaussianWidth,
                        float minPercentMatch,
                        QuantityImpl smallestH,
                        boolean doBootstrap,
                        boolean usePhaseWeight) throws FissuresException,
            NotFound, IOException, SQLException, TauModelException {
        QuantityImpl modSmallestH = HKStack.getBestSmallestH(station, smallestH);
        createSummary(station.get_id(),
                      gaussianWidth,
                      minPercentMatch,
                      modSmallestH,
                      doBootstrap,
                      usePhaseWeight);
    }

    public SumHKStack createSummary(StationId station,
                                    float gaussianWidth,
                                    float minPercentMatch,
                                    QuantityImpl smallestH,
                                    boolean doBootstrap,
                                    boolean usePhaseWeight)
            throws FissuresException, NotFound, IOException, SQLException,
            TauModelException {
        System.out.println("createSummary for "
                + StationIdUtil.toStringNoDates(station));
        SumHKStack sumStack = sum(station.network_id.network_code,
                                  station.station_code,
                                  gaussianWidth,
                                  minPercentMatch,
                                  smallestH,
                                  doBootstrap,
                                  usePhaseWeight);
        if(sumStack == null) {
            System.out.println("stack is null for "
                    + StationIdUtil.toStringNoDates(station));
        } else {
            int dbid;
            try {
                dbid = jdbcSummary.getDbIdForStation(station.network_id,
                                                     station.station_code,
                                                     gaussianWidth,
                                                     minPercentMatch);
                jdbcSummary.update(dbid, sumStack);
            } catch(NotFound e) {
                dbid = jdbcSummary.put(sumStack);
            }
            sumStack.setDbid(dbid);
            calcComplexity(sumStack);
        }
        return sumStack;
    }

    public void calcComplexity() throws SQLException, NotFound, IOException,
            FissuresException, TauModelException {
        HKSummaryIterator it = jdbcSummary.getAllIterator();
        System.out.println("in calc Complexity");
        while(it.hasNext()) {
            SumHKStack sumStack = (SumHKStack)it.next();
            calcComplexity(sumStack);
        }
        it.close();
    }

    public float calcComplexity(SumHKStack sumStack) throws FissuresException,
            TauModelException, SQLException {
        StackComplexity complexity = new StackComplexity(sumStack.getSum(),
                                                         4096,
                                                         sumStack.getSum()
                                                                 .getGaussianWidth());
        StationResult model = new StationResult(sumStack.getChannel().get_id().network_id,
                                                sumStack.getChannel().get_id().station_code,
                                                sumStack.getSum()
                                                        .getMaxValueH(sumStack.getSmallestH()),
                                                sumStack.getSum()
                                                        .getMaxValueK(sumStack.getSmallestH()),
                                                sumStack.getSum().getAlpha(),
                                                null);
        HKStack residual = sumStack.getResidual();
        float complex = sumStack.getResidualPower();
        float complex25 = sumStack.getResidualPower(.25f);
        float complex50 = sumStack.getResidualPower(.50f);
        float bestH = (float)sumStack.getSum()
                .getMaxValueH(sumStack.getSmallestH())
                .getValue(UnitImpl.KILOMETER);
        float bestHStdDev = (float)sumStack.getHStdDev()
                .getValue(UnitImpl.KILOMETER);
        float bestK = sumStack.getSum().getMaxValueK(sumStack.getSmallestH());
        float bestKStdDev = (float)sumStack.getKStdDev();
        float bestVal = sumStack.getSum().getMaxValue(sumStack.getSmallestH());
        float hkCorrelation = (float)sumStack.getMixedVariance();
        float nextH = (float)residual.getMaxValueH(sumStack.getSmallestH())
                .getValue(UnitImpl.KILOMETER);
        float nextK = residual.getMaxValueK(sumStack.getSmallestH());
        float nextVal = residual.getMaxValue(sumStack.getSmallestH());
        StationResult crust2Result = HKStack.getCrust2()
                .getStationResult(sumStack.getChannel().getSite().getStation());
        float crust2diff = bestH
                - (float)crust2Result.getH().getValue(UnitImpl.KILOMETER);
        jdbcStackComplexity.put(sumStack.getDbid(),
                                complex,
                                complex25,
                                complex50,
                                bestH,
                                bestHStdDev,
                                bestK,
                                bestKStdDev,
                                bestVal,
                                hkCorrelation,
                                nextH,
                                nextK,
                                nextVal,
                                crust2diff);
        System.out.println(NetworkIdUtil.toStringNoDates(sumStack.getChannel()
                .get_id().network_id)
                + "."
                + sumStack.getChannel().get_id().station_code
                + " Complexity: " + complex);
        return complex;
    }

    public SumHKStack sum(String netCode,
                          String staCode,
                          float gaussianWidth,
                          float percentMatch,
                          QuantityImpl smallestH,
                          boolean doBootstrap,
                          boolean usePhaseWeight) throws FissuresException,
            NotFound, IOException, SQLException {
        ArrayList individualHK = jdbcHKStack.getForStation(netCode,
                                                           staCode,
                                                           gaussianWidth,
                                                           percentMatch,
                                                           true);
        int netDbId = jdbcHKStack.getJDBCChannel()
                .getStationTable()
                .getBestNetworkDbId(netCode, staCode);
        HKBox[] rejects = jdbcRejectMax.getForStation(netDbId, staCode);
        logger.info("in sum for " + netCode + "." + staCode + " numeq="
                + individualHK.size());
        System.out.println("in sum for " + netCode + "." + staCode + " numeq="
                + individualHK.size());
        // if there is only 1 eq that matches, then we can't really do a stack
        if(individualHK.size() > 1) {
            HKStack temp = (HKStack)individualHK.get(0);
            SumHKStack sumStack = new SumHKStack((HKStack[])individualHK.toArray(new HKStack[0]),
                                                 temp.getChannel(),
                                                 percentMatch,
                                                 smallestH,
                                                 doBootstrap,
                                                 usePhaseWeight,
                                                 rejects);
            TimeOMatic.print("sum for " + netCode + "." + staCode);
            return sumStack;
        } else {
            return null;
        }
    }

    public SumHKStack sumForPhase(String netCode,
                                  String staCode,
                                  float gaussianWidth,
                                  float minPercentMatch,
                                  QuantityImpl smallestH,
                                  String phase,
                                  boolean usePhaseWeight)
            throws FissuresException, NotFound, IOException, SQLException {
        logger.info("in sum for " + netCode + "." + staCode);
        HKStackIterator it = jdbcHKStack.getIteratorForStation(netCode,
                                                               staCode,
                                                               gaussianWidth,
                                                               minPercentMatch,
                                                               false);
        SumHKStack sumStack = sumForPhase(it,
                                          minPercentMatch,
                                          smallestH,
                                          phase,
                                          usePhaseWeight);
        it.close();
        TimeOMatic.print("sum for " + netCode + "." + staCode);
        return sumStack;
    }

    public SumHKStack sumForPhase(Iterator hkstackIterator,
                                  float minPercentMatch,
                                  QuantityImpl smallestH,
                                  String phase,
                                  boolean usePhaseWeight)
            throws FissuresException, NotFound, IOException, SQLException {
        SumHKStack sumStack = SumHKStack.calculateForPhase(hkstackIterator,
                                                           smallestH,
                                                           minPercentMatch,
                                                           usePhaseWeight,
                                                           phase);
        return sumStack;
    }

    public static void saveImage(SumHKStack sumStack,
                                 StationId station,
                                 File parentDir,
                                 float minPercentMatch,
                                 float smallestH) throws IOException {
        if(sumStack == null) {
            logger.info("No hk plots for "
                    + StationIdUtil.toStringNoDates(station) + " with match > "
                    + minPercentMatch);
            return;
        }
        BufferedImage image = sumStack.createStackImage();
        parentDir.mkdirs();
        File outSumImageFile = new File(parentDir,
                                        "SumHKStack_"
                                                + minPercentMatch
                                                + "_"
                                                + FissuresFormatter.filize(ChannelIdUtil.toStringNoDates(sumStack.getChannel()
                                                        .get_id())
                                                        + ".png"));
        if(outSumImageFile.exists()) {
            outSumImageFile.delete();
        }
        javax.imageio.ImageIO.write(image, "png", outSumImageFile);
    }

    public static Properties loadProps(String[] args) {
        Properties props = System.getProperties();
        ConnMgr.addPropsLocation("edu/sc/seis/receiverFunction/server/");
        // get some defaults
        String propFilename = "rfcache.prop";
        String defaultsFilename = "edu/sc/seis/receiverFunction/server/"
                + propFilename;
        for(int i = 0; i < args.length - 1; i++) {
            if(args[i].equals("-props") || args[i].equals("-p")) {
                // override with values in local directory,
                // but still load defaults with original name
                propFilename = args[i + 1];
            }
        }
        try {
            props.load((StackSummary.class).getClassLoader()
                    .getResourceAsStream(defaultsFilename));
        } catch(IOException e) {
            System.err.println("Could not load defaults. " + e);
        }
        try {
            FileInputStream in = new FileInputStream(propFilename);
            props.load(in);
            in.close();
        } catch(FileNotFoundException f) {
            System.err.println(" file missing " + f + " using defaults");
        } catch(IOException f) {
            System.err.println(f.toString() + " using defaults");
        }
        // configure logging from properties...
        PropertyConfigurator.configure(props);
        logger.info("Logging configured");
        return props;
    }

    JDBCHKStack jdbcHKStack;

    JDBCSummaryHKStack jdbcSummary;

    private JDBCStackComplexity jdbcStackComplexity;

    JDBCRejectedMaxima jdbcRejectMax;

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(StackSummary.class);

    public static Connection initDB(Properties props) throws IOException,
            SQLException {
        ConnMgr.setDB(ConnMgr.POSTGRES);
        String dbURL = props.getProperty("cormorant.servers.ears.databaseURL");
        ConnMgr.setURL(dbURL);
        Connection conn = ConnMgr.createConnection();
        return conn;
    }

    public static void parseArgsAndRun(String[] args, StackSummary summary)
            throws FissuresException, NotFound, IOException, SQLException,
            TauModelException {
        float gaussianWidth = 2.5f;
        float minPercentMatch = 80f;
        boolean bootstrap = true;
        boolean bootstrapXY = false;
        boolean usePhaseWeight = true;
        boolean complexityOnly = false;
        boolean neededOnly = false;
        String netArg = "";
        String staArg = "";
        for(int i = 0; i < args.length; i++) {
            if(args[i].equals("--help") || args[i].equals("-h")) {
                System.out.println("Usage:");
                System.out.println(" [ -net network [-sta station]] | -all | --needRecalc");
                System.out.println("-g gaussian");
                System.out.println("--complexity");
                System.out.println("--nobootstrap");
                System.out.println("--bootstrapXY");
                System.out.println();
                return;
            }
        }
        for(int i = 0; i < args.length; i++) {
            if(args[i].equals("-net") || args[i].equals("--net")
                    || args[i].equals("-n")) {
                netArg = args[i + 1];
            } else if(args[i].equals("-sta") || args[i].equals("--sta")
                    || args[i].equals("-s")) {
                staArg = args[i + 1];
            } else if(args[i].equals("-all")) {
                netArg = args[i];
            } else if(args[i].equals("-g")) {
                gaussianWidth = Float.parseFloat(args[i + 1]);
            } else if(args[i].equals("--complexity")) {
                complexityOnly = true;
            } else if(args[i].equals("--nobootstrap")) {
                bootstrap = false;
            } else if(args[i].equals("--needRecalc")) {
                neededOnly = true;
            } else if(args[i].equals("--bootstrapXY")) {
                bootstrapXY = true;
            }
        }
        if(complexityOnly) {
            summary.calcComplexity();
            return;
        }
        if(neededOnly) {
            System.out.println("needed only");
            Station[] stations = summary.getJdbcSummary()
                    .getStationsNeedingUpdate(gaussianWidth, minPercentMatch);
            for(int i = 0; i < stations.length; i++) {
                summary.createSummary(stations[i].get_id(),
                                      gaussianWidth,
                                      minPercentMatch,
                                      HKStack.getBestSmallestH(stations[i],
                                                               HKStack.getDefaultSmallestH()),
                                      bootstrap,
                                      usePhaseWeight);
            }
        } else if(staArg.equals("")) {
            System.out.println("net arg " + netArg);
            summary.createSummary(netArg,
                                  gaussianWidth,
                                  minPercentMatch,
                                  HKStack.getDefaultSmallestH(),
                                  bootstrap,
                                  usePhaseWeight);
        } else {
            System.out.println("calc for staion " + netArg + " " + staArg);
            logger.info("calc for station");
            JDBCStation jdbcStation = summary.jdbcHKStack.getJDBCChannel()
                    .getStationTable();
            VelocityNetwork net = Start.getNetwork(netArg,
                                                   jdbcStation.getNetTable());
            int sta_dbid = -1;
            boolean foundNet = false;
            try {
                int[] tmp = jdbcStation.getDBIds(net.get_id(), staArg);
                if(tmp.length > 0) {
                    foundNet = true;
                    sta_dbid = tmp[0];
                    Station station = jdbcStation.get(sta_dbid);
                    SumHKStack sum = summary.createSummary(station.get_id(),
                                                           gaussianWidth,
                                                           minPercentMatch,
                                                           HKStack.getBestSmallestH(station,
                                                                                    HKStack.getDefaultSmallestH()),
                                                           bootstrap,
                                                           usePhaseWeight);
                    if(bootstrapXY) {
                        double[] h = sum.getHBootstrap();
                        double[] k = sum.getKBootstrap();
                        System.out.println("max h=" + sum.getBest().formatH());
                        System.out.println("max k="
                                + sum.getBest().formatVpVs());
                        System.out.println("k stddev=" + sum.getKStdDev());
                        System.out.println("h stddev=" + sum.getHStdDev());
                        for(int j = 0; j < h.length; j++) {
                            System.out.println(k[j] + " " + h[j]);
                        }
                    }
                }
            } catch(NotFound e) {
                System.out.println("NotFound for :" + net.getCodeWithYear());
                // go to next network
            }
            if(!foundNet) {
                System.out.println("Warning: didn't find net for " + netArg);
            }
        }
        System.out.println("Done.");
    }

    public static void main(String[] args) {
        if(args.length == 0) {
            System.out.println("Usage: StackSummary -net netCode [ -sta staCode ] or --needRecalc");
            return;
        }
        Connection conn = null;
        try {
            TimeOMatic.setWriter(new FileWriter("netTimes.txt"));
            TimeOMatic.start();
            Properties props = loadProps(args);
            conn = initDB(props);
            StackSummary summary = new StackSummary(conn, props);
            parseArgsAndRun(args, summary);
        } catch(Exception e) {
            GlobalExceptionHandler.handle(e);
        } finally {
            if(conn != null) {
                try {
                    conn.close();
                } catch(SQLException e) {
                    // oh well
                }
            }
        }
    }

    public JDBCHKStack getJDBCHKStack() {
        return jdbcHKStack;
    }

    public JDBCSummaryHKStack getJdbcSummary() {
        return jdbcSummary;
    }

    public Connection getConnection() {
        return jdbcHKStack.getConnection();
    }
}