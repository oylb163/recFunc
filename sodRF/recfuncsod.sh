#!/bin/sh
MAVEN=~/.maven

# timeout in milliseconds, use large enough number to avoid thrashing the server
JACORB_TIMEOUT=900000

JACORB_LIB=$MAVEN/repository/JacORB/jars
JACORB=$JACORB_LIB/JacORB-2.1.jar
JACORB_ANTLR=$JACORB_LIB/antlr-2.7.2.jar
JACORB_AVALON=$JACORB_LIB/avalon-framework-4.1.5.jar
JACORB_CONCURRENT=$JACORB_LIB/concurrent-1.3.2.jar
JACORB_LOGKIT=$JACORB_LIB/logkit-1.2.jar


SEEDCODEC=$MAVEN/repository/SeedCodec/jars/SeedCodec-1.0Beta.jar
FISSURESUTIL=$MAVEN/repository/fissuresUtil/jars/fissuresUtil-1.0.7beta.jar
FISSURESIMPL=$MAVEN/repository/fissuresImpl/jars/fissuresImpl-1.1.5beta.jar
FISSURESIDL=$MAVEN/repository/fissuresIDL/jars/fissuresIDL-1.0.jar
LOG4J=$MAVEN/repository/log4j/jars/log4j-1.2.8.jar
TAUP=$MAVEN/repository/TauP/jars/TauP-1.1.4.jar
XALAN=$MAVEN/repository/xalan/jars/xalan-2.5.1.jar
XERCES=$MAVEN/repository/xerces/jars/xerces-2.4.0.jar
XMLAPI=$MAVEN/repository/xml-apis/jars/xml-apis-1.0.b2.jar
JAICORE=$MAVEN/repository/jars/jai_core.jar
JAICODEC=$MAVEN/repository/jars/jai_codec.jar
HSQLDB=$MAVEN/repository/hsqldb/jars/hsqldb-20040212.jar
OPENMAP=$MAVEN/repository/openmap/jars/openmap-4.6.jar
JING=$MAVEN/repository/jing/jars/jing-20030619.jar
VELOCITY=$MAVEN/repository/velocity/jars/velocity-1.4-rc1.jar
COMMONS_COLL=$MAVEN/repository/commons-collections/jars/commons-collections-3.0.jar
VELOCITY_TOOLS=$MAVEN/repository/velocity-tools/jars/velocity-tools-generic-1.1-rc1.jar
SOD=$MAVEN/repository/sod/jars/sod-1.0Beta.jar

RECFUNC=$MAVEN/repository/recFunc/jars/recFunc-1.0beta.jar
GEE=$MAVEN/repository/gee/jars/gee-2.0.7beta.jar


java -Djava.endorsed.dirs=${JACORB_LIB}  \
    -Dorg.omg.CORBA.ORBClass=org.jacorb.orb.ORB \
    -Dorg.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton \
    -Djacorb.connection.client.pending_reply_timeout=${JACORB_TIMEOUT} \
    -Xmx528m \
    -cp ${RECFUNC}:${GEE}:${JACORB}:${VELOCITY}:${COMMONS_COLL}:${VELOCITY_TOOLS}:${JACORB_ANTLR}:${JACORB_AVALON}:${JACORB_CONCURRENT}:${JACORB_LOGKIT}:${JING}:${OPENMAP}:${SEEDCODEC}:${SOD}:${FISSURESIDL}:${FISSURESIMPL}:${FISSURESUTIL}:${XERCES}:${XMLAPI}:${XALAN}:${TAUP}:${LOG4J}:${HSQLDB}:${CLASSPATH} \
    edu.sc.seis.sod.Start $*


