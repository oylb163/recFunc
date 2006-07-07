package edu.sc.seis.receiverFunction.server;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.UserException;
import org.w3c.dom.Element;
import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfNetwork.ChannelId;
import edu.sc.seis.IfReceiverFunction.IterDeconConfig;
import edu.sc.seis.IfReceiverFunction.RecFuncCacheHelper;
import edu.sc.seis.IfReceiverFunction.RecFuncCacheOperations;
import edu.sc.seis.fissuresUtil.display.configuration.DOMHelper;
import edu.sc.seis.fissuresUtil.namingService.FissuresNamingService;
import edu.sc.seis.receiverFunction.RecFuncProcessor;
import edu.sc.seis.rev.RevUtil;
import edu.sc.seis.sod.ChannelGroup;
import edu.sc.seis.sod.CommonAccess;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.subsetter.eventChannel.vector.EventVectorSubsetter;


/**
 * @author crotwell
 * Created on Sep 16, 2004
 */
public class RecFuncCacheCheck implements EventVectorSubsetter {

    public RecFuncCacheCheck(Element config) throws ConfigurationException {
        deconConfig = RecFuncProcessor.parseIterDeconConfig(config);
        String dns = DOMHelper.extractText(config, "dns", "edu/sc/seis");
        String serverName = DOMHelper.extractText(config, "name", "Ears");
        try {
            FissuresNamingService fisName = CommonAccess.getCommonAccess().getFissuresNamingService();
            cache = new NSRecFuncCache(dns, serverName, fisName);
        } catch (SystemException e) {
            throw new ConfigurationException("Problem getting cache server", e);
        }
    }
    
    /**
     * returns true if the receiver functions are already cached.
     */
    public StringTree accept(EventAccessOperations event,
                          ChannelGroup channel,
                          CookieJar cookieJar) throws Exception {
        ChannelId[] chanId = new ChannelId[channel.getChannels().length];
        for(int i = 0; i < chanId.length; i++) {
            chanId[i] = channel.getChannels()[i].get_id();
        }
        return new StringTreeLeaf(this, cache.isCached(event.get_preferred_origin(), chanId, deconConfig));
    }
    
    RecFuncCacheOperations cache;

    IterDeconConfig deconConfig;
    
}
