package edu.sc.seis.receiverFunction;

import edu.iris.Fissures.model.SamplingImpl;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.fissuresUtil.display.SimplePlotUtil;
import edu.sc.seis.fissuresUtil.sac.FissuresToSac;
import edu.sc.seis.fissuresUtil.sac.SacTimeSeries;
import edu.sc.seis.receiverFunction.IterDecon;
import java.io.DataInputStream;
import junit.framework.TestCase;
import junitx.framework.ArrayAssert;
// JUnitDoclet end import

/**
 * Generated by JUnitDoclet, a tool provided by
 * ObjectFab GmbH under LGPL.
 * Please see www.junitdoclet.org, www.gnu.org
 * and www.objectfab.de for informations about
 * the tool, the licence and the authors.
 */


public class IterDeconTest
    // JUnitDoclet begin extends_implements
    extends TestCase
    // JUnitDoclet end extends_implements
{
    // JUnitDoclet begin class
    edu.sc.seis.receiverFunction.IterDecon iterdecon = null;
    // JUnitDoclet end class

    public IterDeconTest(String name) {
        // JUnitDoclet begin method IterDeconTest
        super(name);
        // JUnitDoclet end method IterDeconTest
    }

    public edu.sc.seis.receiverFunction.IterDecon createInstance() throws Exception {
        // JUnitDoclet begin method testcase.createInstance
        return new edu.sc.seis.receiverFunction.IterDecon(100, true, .0001f, 3);
        // JUnitDoclet end method testcase.createInstance
    }

    protected void setUp() throws Exception {
        // JUnitDoclet begin method testcase.setUp
        super.setUp();
        iterdecon = createInstance();
        // JUnitDoclet end method testcase.setUp
    }

    protected void tearDown() throws Exception {
        // JUnitDoclet begin method testcase.tearDown
        iterdecon = null;
        super.tearDown();
        // JUnitDoclet end method testcase.tearDown
    }

    public void testNoGauussProcess() throws Exception {
        float[] numData   = new float[2048];
        numData[100] = 2;
        numData[200] = -1.5f;
        numData[300] = .25f;
        float[] denomData = new float[2048];
        denomData[100] = .5f;

        // without gaussian filter
        IterDecon zeroGauss = new IterDecon(3, true, 0.001f, 0.0f);
        IterDeconResult result = zeroGauss.process(numData, denomData, .05f);
        float[] pred = result.getPredicted();
        int[] s = result.getShifts();
        float[] a = result.getAmps();
        assertEquals("zeroGauss spike 0",  0, s[0]);
        assertEquals("zeroGauss amp 0",    4,    a[0], 0.0001f);
        assertEquals("zeroGauss spike 1",  100, s[1]);
        assertEquals("zeroGauss amp 1",   -3,    a[1], 0.0001f);
        assertEquals("zeroGauss spike 2 a="+a[2],  200, s[2]);
        assertEquals("zeroGauss amp 2",  .5f,    a[2], 0.0001f);
        assertEquals("zeroGauss pred 0",   4, pred[0], 0.0001f);
        assertEquals("zeroGauss pred 1",   0, pred[1], 0.0001f);
        assertEquals("zeroGauss pred 100",  -3, pred[100], 0.0001f);
        assertEquals("zeroGauss pred 101",   0, pred[3], 0.0001f);
        assertEquals("zeroGauss pred 200", .5f, pred[200], 0.0001f);
        assertEquals("zeroGauss pred 201",   0, pred[5], 0.0001f);
    }

    public void testWithGauussProcess() throws Exception {
        IterDecon withGaussIterdecon = new edu.sc.seis.receiverFunction.IterDecon(200, true, .001f, 2.5f);
        // with gaussian filter
        float[] numData   = new float[2048];

        numData[100] = 2;
        numData[200] = -1.5f;
        numData[300] = .25f;
        float[] denomData = new float[numData.length];
        denomData[100] = .5f;

        float delta = 0.05f;
        SamplingImpl sampling = new SamplingImpl(1, new TimeInterval(delta, UnitImpl.SECOND));

        LocalSeismogramImpl fakeNum = SimplePlotUtil.createTestData("num");
        fakeNum.setData(numData);
        fakeNum.channel_id.channel_code = "BHR";
        fakeNum.sampling_info = sampling;
        LocalSeismogramImpl fakeDenom = SimplePlotUtil.createTestData("denom");
        fakeDenom.setData(denomData);
        fakeDenom.channel_id.channel_code = "BHZ";
        fakeDenom.sampling_info = sampling;

        SacTimeSeries sac = FissuresToSac.getSAC(fakeNum);
        sac.write("withGauss.BHR.sac");
        sac = FissuresToSac.getSAC(fakeDenom);
        sac.write("withGauss.BHZ.sac");
        sac = null;

        IterDeconResult result = withGaussIterdecon.process(numData, denomData, delta);
        float[] pred = result.getPredicted();
        pred = withGaussIterdecon.phaseShift(pred, 5, delta);

        LocalSeismogramImpl predSeis = SimplePlotUtil.createTestData("denom");
        predSeis.setData(pred);
        predSeis.channel_id.channel_code = "OUT";
        sac = FissuresToSac.getSAC(predSeis);
        sac.write("withGauss.ITR.sac");

        DataInputStream in =
            new DataInputStream(this.getClass().getClassLoader().getResourceAsStream("edu/sc/seis/receiverFunction/withGauss.predicted.sac"));
        sac.read(in);
        float[] fortranData = sac.y;

        int[] s = result.getShifts();
        float[] a = result.getAmps();
        assertEquals("gauss spike 0",  0, s[0]);
        assertEquals("gauss amp 0",    4,    a[0], 0.0001f);
        assertEquals("gauss spike 1",  100, s[1]);
        assertEquals("gauss amp 1",   -3,    a[1], 0.0001f);
        assertEquals("gauss spike 2 a="+a[2],  200, s[2]);
        assertEquals("gauss amp 2",  .5f,    a[2], 0.0001f);

        assertEquals("position 0 "+fortranData[0]+"  "+pred[0]+"  ratio="+(fortranData[0]/pred[0]), fortranData[0], pred[0], 0.0001f);
        assertEquals("position 100 "+fortranData[100]+"  "+pred[100]+"  ratio="+(fortranData[100]/pred[100]), fortranData[100], pred[100]/delta, 0.0001f);
        assertEquals("position 200 "+fortranData[200]+"  "+pred[200]+"  ratio="+(fortranData[200]/pred[200]), fortranData[200], pred[200], 0.0001f);
        assertEquals("position 300 "+fortranData[300]+"  "+pred[300]+"  ratio="+(fortranData[300]/pred[300]), fortranData[300], pred[300], 0.0001f);
        ArrayAssert.assertEquals("data from fortran", fortranData, pred, 0.0001f);

    }

    public void testFakeCrustProcess() throws Exception {
        float delta = 0.05f;

        // with more complex demon
        float[] denomData = new float[2048];
        denomData[100] = .15f;
        denomData[101] = .5f;
        denomData[102] = .9f;
        denomData[103] =1.1f;
        denomData[104] = .8f;
        denomData[105] = .4f;
        denomData[106] = .1f;
        denomData[107] =-.3f;
        denomData[108] =-.6f;
        denomData[109] =-.4f;
        denomData[110] =-.1f;
        denomData[111] = .1f;

        // create fake crust with Vp=6 and Vs=3.5, h=30
        float alpha = 6;
        float beta = 3.5f;
        float h = 30;
        float p = 7.6f/111.19f;
        float etaP = (float) Math.sqrt(1/(alpha*alpha)-p*p);
        float etaS = (float) Math.sqrt(1/(beta*beta)-p*p);
        float timePs = h * (etaS - etaP);
        float timePpPs = h * (etaS + etaP);
        float timePsPs = h * (2 * etaS);
        System.out.println("timePs="+timePs+"  timePpPs="+timePpPs+"  timePsPs="+timePsPs);

        float[] numData   = new float[denomData.length];
        System.arraycopy(denomData, 0, numData, 0, denomData.length);
        // scale num by 1/3
        for (int i = 0; i < numData.length; i++) {
            numData[i] *= .33f;
        }
        float[] temp = new float[numData.length];
        System.arraycopy(denomData, 0, temp, 0, denomData.length);
        temp = IterDecon.phaseShift(temp, timePs, delta);
        // scale num by 1/5
        for (int i = 0; i < temp.length; i++) {
            numData[i] += .33f*.50f*temp[i];
        }
        System.arraycopy(denomData, 0, temp, 0, denomData.length);
        temp = IterDecon.phaseShift(temp, timePpPs, delta);
        // scale num
        for (int i = 0; i < temp.length; i++) {
            numData[i] += .33f*.3f*temp[i];
        }
        System.arraycopy(denomData, 0, temp, 0, denomData.length);
        temp = IterDecon.phaseShift(temp, timePsPs, delta);
        // scale num
        for (int i = 0; i < temp.length; i++) {
            numData[i] += .33f*.2f*temp[i];
        }


        IterDeconResult result = iterdecon.process(numData, denomData, delta);
        float[] pred = result.getPredicted();
        int[] s = result.getShifts();
        float[] a = result.getAmps();

        for (int i = 0; i < 5; i++) {
            System.out.println("spike "+i+" "+s[i]+"  amp="+a[i]);
        }

        assertEquals("fake data spike 0",  0, s[0]);
        assertEquals("fake data amp 0",    .33,    a[0], 0.0001f);
        assertEquals("fake data spike 1",  Math.round(timePs/delta), s[1], 0.1f);
        assertEquals("fake data amp 1",   .33f*.5f,    a[1], 0.001f);
        assertEquals("fake data spike 2 a="+a[2],  Math.round(timePpPs/delta), s[2], 0.1f);
        assertEquals("fake data amp 2",  .33f*.3f,    a[2], 0.001f);
        assertEquals("fake data spike 3 a="+a[3],  Math.round(timePsPs/delta), s[3], 0.1f);
        assertEquals("fake data amp 3",  .33f*.2f,    a[3], 0.001f);

        // JUnitDoclet end method process
    }

    public void testOnePhaseShift() throws Exception {
        // JUnitDoclet begin method phaseShift
        float[] data = new float[1024];
        data[10] = 1;
        float[] out = iterdecon.phaseShift(data, 0.05f, 0.05f);
        //float[] oldout = iterdecon.oldphaseShift(data, 1.0f, 1.0f);
        //for ( int i=0; i<data.length; i++) {
        //    System.out.println("data="+data[i]+"  out="+out[i]);
        //} // end of for ()

        //                              expected  actual
        assertEquals("9 shifts to 10",   data[9], out[10], .001);
        assertEquals("10 shifts to 11", data[10], out[11], .001);
        assertEquals("11 shifts to 12", data[11], out[12], .001);


        // JUnitDoclet end method phaseShift
    }
    public void testFivePhaseShift() throws Exception {
        // JUnitDoclet begin method phaseShift
        float[] data = new float[1024];
        data[10] = 1;
        data[11] = 2;
        data[12] = 1.1f;
        float[] out = iterdecon.phaseShift(data, 5f, 0.05f);

        //                              expected  actual
        assertEquals("9 shifts to 109",   data[9], out[109], .001);
        assertEquals("10 shifts to 110", data[10], out[110], .001);
        assertEquals("11 shifts to 111", data[11], out[111], .001);
        assertEquals("12 shifts to 112", data[11], out[111], .001);
        assertEquals("13 shifts to 113", data[11], out[111], .001);


        // JUnitDoclet end method phaseShift
    }

    public void testNextPowerTwo() throws Exception {
        // JUnitDoclet begin method phaseShift
        assertEquals(iterdecon.nextPowerTwo(3), 4);
        assertEquals(iterdecon.nextPowerTwo(4), 4);
        assertEquals(iterdecon.nextPowerTwo(1024), 1024);
        assertEquals(iterdecon.nextPowerTwo(1025), 2048);
        // JUnitDoclet end method phaseShift
    }

    /** Gaussian filter of constant should do nothing.
     */
    public void testGaussianFilter() throws Exception {
        // JUnitDoclet begin method phaseShift
        SacTimeSeries sac = new SacTimeSeries();
        DataInputStream in =
            new DataInputStream(this.getClass().getClassLoader().getResourceAsStream("edu/sc/seis/receiverFunction/gauss1024.sac"));
        sac.read(in);
        float[] data = new float[sac.npts];
        data[100] = 1/sac.delta;

        float[] out = iterdecon.gaussianFilter(data, 2.5f, sac.delta);
        float[] sacData = sac.y;

        ArrayAssert.assertEquals("gaussian filter", sacData, out, 0.001f);


        // JUnitDoclet end method phaseShift
    }

    public void testGetMinIndex() {
        float[] data = { 3, 4, -5, 0, 4, 4, 0, -5, 4, 3};
        int index = IterDecon.getMinIndex(data);
        assertEquals("min index", 2, index);
        index = IterDecon.getMaxIndex(data);
        assertEquals("max index", 1, index);
        index = IterDecon.getAbsMaxIndex(data);
        assertEquals("abs max index", 2, index);
    }

    public void testPower() {
        float[] data = { 0, 2, 3, -1, -2, 0};
        assertEquals(18f, iterdecon.power(data), 0.00001f);
    }

    public void testCorrelation() {
        float[] fData = { 0, 0, 2, 0, 0, 0, 0, 0};
        float[] gData = { 0, 1, 0, 0, 0, 0, 0, 0};
        float[] corr = iterdecon.correlate(fData, gData);
        assertEquals("lag 0", 0f, corr[0], 0.00001f);
        assertEquals("lag 1", 2f, corr[1], 0.00001f);
        assertEquals("lag 2", 0f, corr[2], 0.00001f);
        assertEquals("lag 3", 0f, corr[3], 0.00001f);
    }

    public void testConvolve() {
        float[] fData = { 0, 0, 2, 0, 0, 0, 0, 0};
        float[] gData = { 0, 1, 0, 0, 0, 0, 0, 0};
        float[] corr = iterdecon.convolve(fData, gData);
        assertEquals("lag 0", 0f, corr[0], 0.00001f);
        assertEquals("lag 1", 0f, corr[1], 0.00001f);
        assertEquals("lag 2", 0f, corr[2], 0.00001f);
        assertEquals("lag 3", 2f, corr[3], 0.00001f);
        assertEquals("lag 4", 0f, corr[4], 0.00001f);
        assertEquals("lag 5", 0f, corr[5], 0.00001f);
        assertEquals("lag 6", 0f, corr[6], 0.00001f);
        assertEquals("lag 7", 0f, corr[7], 0.00001f);
    }

    public void testIterDeconIdentity() throws Exception {
        // JUnitDoclet begin method phaseShift
        float[] data = new float[128];

        data[49] = .5f;

        IterDeconResult out = iterdecon.process(data, data, 1.0f);
        iterdecon.gaussianFilter(out.predicted, 3.0f, 1.0f);
        //for ( int i=0; i<out.predicted.length; i++) {
        //  System.out.println("predicted "+i+"  data="+data[i]+"  out="+out.predicted[i]);
        //            assertEquals("predicted "+i+"  data="+data[i]+"  out="+out.predicted[i],
        //                       data[i], out.predicted[i], 0.001);
        //} // end of for ()

        /* these values come from running New_Decon_Process on a impulse
         generated with sac's fg impulse command (100 datapoints, 1 at 49)
         The receiver function of data from itself should be unity at lag 0
         and zero elsewhere, of course the gaussian tends to smear it out.

         piglet 51>../New_Decon_Process/iterdecon_tjo

         Program iterdeconfd - Version 1.0X, 1997-98
         Chuck Ammon, Saint Louis University

         impulse100.sac
         impulse100.sac
         output
         100
         10
         .001
         3.0
         1
         0
         output

         The maximum spike delay is   64.00000

         File         Spike amplitude   Spike delay   Misfit   Improvement
         r001         0.100000012E+01       0.000      0.00%    100.0000%
         r002        -0.126299312E-06       0.000      0.00%      0.0000%

         Last Error Change =    0.0000%

         Hit the min improvement tolerance - halting.
         Number of bumps in final result:   1
         The final deconvolution reproduces  100.0% of the signal.


         */
        assertEquals( 0.9156569,   out.predicted[0], .000001);
        assertEquals( 0.04999885,  out.predicted[1], .000001);
        assertEquals(-0.01094833,  out.predicted[2], .000001);
        assertEquals( 0.004774094, out.predicted[3], .000001);
        assertEquals(-0.002670953, out.predicted[4], .000001);
        // JUnitDoclet end method phaseShift
    }

    /**
     * JUnitDoclet moves marker to this method, if there is not match
     * for them in the regenerated code and if the marker is not empty.
     * This way, no test gets lost when regenerating after renaming.
     * Method testVault is supposed to be empty.
     */
    public void testVault() throws Exception {
        // JUnitDoclet begin method testcase.testVault
        // JUnitDoclet end method testcase.testVault
    }

    public static void main(String[] args) {
        // JUnitDoclet begin method testcase.main
        junit.textui.TestRunner.run(IterDeconTest.class);
        // JUnitDoclet end method testcase.main
    }
}


