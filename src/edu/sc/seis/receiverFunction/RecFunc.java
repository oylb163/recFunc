/**
 * RecFunc.java
 *
 * @author Created by Omnicore CodeGuide
 */

package edu.sc.seis.receiverFunction;

import org.apache.log4j.Logger;
import edu.iris.Fissures.FissuresException;
import edu.iris.Fissures.Location;
import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfEvent.NoPreferredOrigin;
import edu.iris.Fissures.IfEvent.Origin;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.SamplingImpl;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.fissuresUtil.bag.IncompatibleSeismograms;
import edu.sc.seis.fissuresUtil.bag.Rotate;
import edu.sc.seis.fissuresUtil.bag.TauPUtil;

public class RecFunc {

    /** uses a default shift of 10 seconds. */
    RecFunc(TauPUtil timeCalc, IterDecon decon) {
        this(timeCalc, decon, new TimeInterval(10, UnitImpl.SECOND));

    }

    RecFunc(TauPUtil timeCalc, IterDecon decon, TimeInterval shift) {
        this(timeCalc, decon, shift, new TimeInterval(100, UnitImpl.SECOND));
    }

    RecFunc(TauPUtil timeCalc, IterDecon decon, TimeInterval shift, TimeInterval pad) {
        this.timeCalc = timeCalc;
        this.decon = decon;
        this.shift = shift;
        this.pad = pad;
    }

    public IterDeconResult[] process(EventAccessOperations event,
                                     Channel[] channel,
                                     LocalSeismogramImpl[] localSeis)
        throws NoPreferredOrigin,
        TauModelException,
        IncompatibleSeismograms,
        FissuresException {

        LocalSeismogramImpl n = null, e = null, z = null;
        for (int i=0; i<localSeis.length; i++) {
            if (localSeis[i].channel_id.channel_code.endsWith("N")) {
                n = localSeis[i];
            } else if (localSeis[i].channel_id.channel_code.endsWith("E")) {
                e = localSeis[i];
            }if (localSeis[i].channel_id.channel_code.endsWith("Z")) {
                z = localSeis[i];
            }
        }
        if (n == null || e == null || z == null) {
            logger.error("problem one seismogram component is null ");
            throw new NullPointerException("problem one seismogram component is null "+
                                               (n != null)+" "+(e != null)+" "+(z != null));
        }


        Channel chan = channel[0];
        Location staLoc = chan.my_site.my_station.my_location;
        Origin origin = event.get_preferred_origin();
        Location evtLoc = origin.my_location;

        float[][] rotated = Rotate.rotateGCP(e, n, staLoc, evtLoc);


        // check lengths, trim if needed???
        float[] zdata = z.get_as_floats();
        if (rotated[0].length != zdata.length) {
            logger.error("data is not of same length "+
                             rotated[0].length+" "+zdata.length);
            throw new IncompatibleSeismograms("data is not of same length "+
                                                  rotated[0].length+" "+zdata.length);
        }
        if (zdata.length == 0) {
            throw new IncompatibleSeismograms("data is of zero length ");
        }
        SamplingImpl samp = SamplingImpl.createSamplingImpl(z.sampling_info);
        double period = samp.getPeriod().convertTo(UnitImpl.SECOND).getValue();

        zdata = decon.makePowerTwo(zdata);
        rotated[0] = decon.makePowerTwo(rotated[0]);
        rotated[1] = decon.makePowerTwo(rotated[1]);

        IterDeconResult ansRadial = processComponent(rotated[1],
                                                     zdata,
                                                         (float)period,
                                                     staLoc,
                                                     origin);

        IterDeconResult ansTangential = processComponent(rotated[0],
                                                         zdata,
                                                             (float)period,
                                                         staLoc,
                                                         origin);
        IterDeconResult[] ans = new IterDeconResult[2];
        ans[0] = ansRadial;
        ans[1] = ansTangential;
        return ans;
    }

    protected IterDeconResult processComponent(float[] component,
                                               float[] zdata,
                                               float period,
                                               Location staLoc,
                                               Origin origin)
        throws TauModelException {
        IterDeconResult ans = decon.process(component,
                                            zdata,
                                            period);
        float[] predicted = ans.getPredicted();

        Arrival[] pPhases =
            timeCalc.calcTravelTimes(staLoc, origin, new String[] {"ttp"});

        MicroSecondDate firstP = new MicroSecondDate(origin.origin_time);
        logger.debug("origin "+firstP);
        firstP = firstP.add(new TimeInterval(pPhases[0].getTime(), UnitImpl.SECOND));
        logger.debug("firstP "+firstP);
        //TimeInterval shift = firstP.subtract(z.getBeginTime());
        shift = (TimeInterval)shift.convertTo(UnitImpl.SECOND);
        if (shift.getValue() != 0) {
            logger.debug("shifting by "+shift+"  before 0="+predicted[0]);
            predicted = decon.phaseShift(predicted,
                                             (float)shift.getValue(),
                                             period);

            logger.debug("shifting by "+shift);
        }

        logger.info("Finished with receiver function processing");
        logger.debug("rec func begin "+firstP.subtract(shift));
        ans.predicted = predicted;
        ans.setAlignShift(shift);
        return ans;
    }

    public IterDecon getIterDecon() {
        return decon;
    }

    public TauPUtil getTimeCalc() {
        return timeCalc;
    }

    public TimeInterval getShift() {
        return shift;
    }

    TauPUtil timeCalc;

    IterDecon decon;

    TimeInterval shift;

    TimeInterval pad;

    static Logger logger = Logger.getLogger(RecFunc.class);

}

