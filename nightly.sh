#!/bin/bash

echo "cvs updating to the newest"
cvs update

ant clean; ant

#export CLASSPATH=/x/unifi/unifi/ext/bcel-5.2.jar:/x/unifi/unifi/ext/jgrapht-jdk1.6.jar:/x/unifi/unifi/classes/unifi.jar:/x/unifi/java-jars/rt.jar
export DEFAULT_CLASSPATH=$CLASSPATH

cd ../java-jars/

echo "\nGenerating GUI golden units"
java -Xss16M -Xms1G -Dunifi.write=java.gui.units unifi.drivers.Analyze   java.awt.jar java.swing.jar  > log.gui 2>&1

echo "\nGenerating Java.net golden units"
java -Xss16M -Xms1G -Dunifi.write=java.net.units unifi.drivers.Analyze   java.net.jar  > log.net 2>&1

echo "\nGenerating Java.lang golden units"
java -Xss16M -Xms1G -Dunifi.write=java.lang.units unifi.drivers.Analyze   java.lang.jar > log.lang 2>&1

echo "\nGenerating Java.security golden units"
java -Xss16M -Xms1G -Dunifi.write=java.security.units unifi.drivers.Analyze   java.security.jar  > log.security 2>&1

echo "\nGenerating Java.util golden units"
java -Xss16M -Xms1G -Dunifi.write=java.util.units unifi.drivers.Analyze   java.util.jar > log.util 2>&1

echo "\nJavaCalTools"
cd ../javacaltools
echo "\nApplying GUI golden units to javacaltools"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.gui.units -Dunifi.write=jcal.gui.units unifi.drivers.Analyze alljars/k5n-ical-0.4.7.jar > log.jcal.gui 2>&1

echo "\nApplying Java.util golden units to javacaltools"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.util.units -Dunifi.write=jcal.util.units unifi.drivers.Analyze alljars/k5n-ical-0.4.7.jar > log.jcal.util 2>&1

echo "\nApplying Java.lang golden units to javacaltools"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.lang.units -Dunifi.write=jcal.lang.units unifi.drivers.Analyze alljars/k5n-ical-0.4.7.jar > log.jcal.lang 2>&1


echo "\nPRPL Infrastructure"
cd ../prpl-infrastructure

export CLASSPATH=$DEFAULT_CLASSPATH:./all-jars/xwork-2.1.2.jar:./all-jars/xstream-1.3.1.jar:./all-jars/xpp3_min-1.1.4c.jar:./all-jars/xmlsec-1.3.0.jar:./all-jars/xmlrpc-server-3.1.jar:./all-jars/xmlrpc-common-3.1.jar:./all-jars/xmlrpc-client-3.1.jar:./all-jars/xmlParserAPIs-2.0.2.jar:./all-jars/xml-apis-1.3.03.jar:./all-jars/xml-apis-1.0.b2.jar:./all-jars/xercesImpl-2.8.1.jar:./all-jars/xalan-2.7.0.jar:./all-jars/wstx-asl-3.0.0.jar:./all-jars/ws-commons-util-1.0.2.jar:./all-jars/struts2-core-2.1.6.jar:./all-jars/stax-api-1.0.jar:./all-jars/SQLite-unknown.jar:./all-jars/sqlite-jdbc-3.6.11.jar:./all-jars/spring-test-2.5.6.jar:./all-jars/slf4j-jcl-1.0.1.jar:./all-jars/servlet-api-2.5-6.1.14.jar:./all-jars/sdb-1.3.0.jar:./all-jars/rome-0.9.jar:./all-jars/prpl-rdfstore-0.2.2-SNAPSHOT.jar:./all-jars/prpl-pcb-0.2.2-SNAPSHOT.jar:./all-jars/prpl-launcher-0.2.2-SNAPSHOT.jar:./all-jars/prpl-directory-server-0.2.2-SNAPSHOT.jar:./all-jars/prpl-directory-client-0.2.2-SNAPSHOT.jar:./all-jars/prpl-datalog-0.2.2-SNAPSHOT.jar:./all-jars/prpl-common-server-0.2.2-SNAPSHOT.jar:./all-jars/prpl-common-datalog-legacy-0.2.2-SNAPSHOT.jar:./all-jars/prpl-common-client-0.2.2-SNAPSHOT.jar:./all-jars/prpl-common-0.2.2-SNAPSHOT.jar:./all-jars/prpl-client-android-dep-0.2.2-SNAPSHOT.jar:./all-jars/prpl-client-android-0.2.2-SNAPSHOT.jar:./all-jars/prpl-client-0.2.2-SNAPSHOT.jar:./all-jars/prpl-blobserver-0.2.2-SNAPSHOT.jar:./all-jars/prpl-applications-common-0.2-SNAPSHOT.jar:./all-jars/prpl-applications-apps-0.2-SNAPSHOT.jar:./all-jars/prefuse-beta-20060220.jar:./all-jars/openxri-syntax-1.2.0.jar:./all-jars/openxri-client-1.2.0.jar:./all-jars/openid4java-0.9.5.jar:./all-jars/ognl-2.6.11.jar:./all-jars/NekoHTML-1.9.12.jar:./all-jars/metadata-extractor-2.4.0-beta-1.jar:./all-jars/maven-download-plugin-1.0.0.0.jar:./all-jars/mail-1.4.2.jar:./all-jars/lucene-core-2.3.1.jar:./all-jars/log4j-1.2.15.jar:./all-jars/junit-3.8.1.jar:./all-jars/jug-1.1.2.jar:./all-jars/jsr311-api-1.1.1.jar:./all-jars/jsp-api-2.1-6.1.14.jar:./all-jars/jsp-2.1-6.1.14.jar:./all-jars/json_simple-unknown.jar:./all-jars/json-jena-1.0.jar:./all-jars/json-1.0.jar:./all-jars/joseki-unknown.jar:./all-jars/jmxtools-1.2.1.jar:./all-jars/jmxri-1.2.1.jar:./all-jars/jms-1.1.jar:./all-jars/jid3lib-0.5.4.jar:./all-jars/jgrapht-jdk1.5-0.7.3.jar:./all-jars/jetty-util-6.1.24.jar:./all-jars/jetty-6.1.24.jar:./all-jars/jersey-server-1.1.4.1.jar:./all-jars/jersey-core-1.1.4.1.jar:./all-jars/jena-2.5.7.jar:./all-jars/jdom-1.1.jar:./all-jars/jdbm-1.0.jar:./all-jars/iris-jena-unknown.jar:./all-jars/iri-0.7.jar:./all-jars/icu4j-3.4.4.jar:./all-jars/ical4j-1.0-beta4.jar:./all-jars/htmlparser-1.6.jar:./all-jars/harvester-0.2-SNAPSHOT.jar:./all-jars/h2-1.2.125.jar:./all-jars/gdata-photos-meta-1.0.jar:./all-jars/gdata-photos-1.0.jar:./all-jars/gdata-media-1.0.jar:./all-jars/gdata-core-1.0.jar:./all-jars/gdata-contacts-meta-2.0.jar:./all-jars/gdata-contacts-2.0.jar:./all-jars/gdata-client-meta-1.0.jar:./all-jars/gdata-client-1.0.jar:./all-jars/fuse-0.2-SNAPSHOT.jar:./all-jars/fastmd5-unknown.jar:./all-jars/facebook-java-api-1.8-final.jar:./all-jars/email-monitor-0.2-SNAPSHOT.jar:./all-jars/ejalbert-2.1.3.jar:./all-jars/eaut4java-0.0.5.jar:./all-jars/derbynet-10.5.3.0_1.jar:./all-jars/derby-10.5.3.0_1.jar:./all-jars/demos-beta-20060220.jar:./all-jars/core-3.1.1.jar:./all-jars/concurrent-jena-1.3.2.jar:./all-jars/commons-logging-1.1.1.jar:./all-jars/commons-lang-2.4.jar:./all-jars/commons-io-1.4.jar:./all-jars/commons-io-1.3.2.jar:./all-jars/commons-httpclient-3.1.jar:./all-jars/commons-fileupload-1.2.1.jar:./all-jars/commons-codec-1.3.jar:./all-jars/commons-codec-1.2.jar:./all-jars/commons-cli-1.2.jar:./all-jars/browserMiner-0.2-SNAPSHOT.jar:./all-jars/asm-3.1.jar:./all-jars/arq-extra-2.6.0.jar:./all-jars/arq-2.6.0.jar:./all-jars/antlr-2.7.5.jar:./all-jars/ant-1.6.5.jar:./all-jars/alljars:./all-jars/activation-1.1.jar

echo "\nApplying Java.net golden units to prpl-infrastructure"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.net.units -Dunifi.write=prpl.net.units unifi.drivers.Analyze all-jars/prpl-*.jar  > log.prpl.net 2>&1

echo "\nApplying Java.util golden units to prpl-infrastructure"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.util.units -Dunifi.write=prpl.util.units unifi.drivers.Analyze all-jars/prpl-*.jar > log.prpl.util 2>&1

echo "\nApplying Java.lang golden units to prpl-infrastructure"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.lang.units -Dunifi.write=prpl.lang.units unifi.drivers.Analyze all-jars/prpl-*.jar > log.prpl.lang 2>&1


echo "\nPrefuse"

cd ../prefuse-beta/

export CLASSPATH=./alljars/ant.jar:./alljars/demos.jar:./alljars/lucene-1.4.3.jar:./alljars/mysql-connector-java-3.1.12-bin.jar:./alljars/prefuse.jar:./alljars/prefuse.src.jar:$DEFAULT_CLASSPATH

echo "\nApplying GUI golden units to prefuse"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.gui.units -Dunifi.write=prefuse.gui.units unifi.drivers.Analyze alljars/prefuse.jar > log.prefuse.gui 2>&1

echo "\nApplying Java.lang golden units to prefuse"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.lang.units -Dunifi.write=prefuse.lang.units unifi.drivers.Analyze alljars/prefuse.jar > log.prefuse.lang 2>&1

echo "\nApplying Java.util golden units to prefuse"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.util.units -Dunifi.write=prefuse.util.units unifi.drivers.Analyze alljars/prefuse.jar > log.prefuse.util 2>&1


echo "\nGUESS"
cd ../guess/

export CLASSPATH=lib/colt.jar:lib/commons-collections.jar:lib/forms.jar:lib/freehep-all.jar:lib/guess.jar:lib/hsqldb.jar:lib/jcommon.jar:lib/jfreechart.jar:lib/jide-common.jar:lib/jide-components.jar:lib/jung.jar:lib/looks.jar:lib/mascoptLib.jar:lib/piccolo.jar:lib/piccolox.jar:lib/prefuse.jar:lib/TGGraphLayout.jar:$DEFAULT_CLASSPATH

echo "\nApplying GUI golden units to GUESS"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.gui.units -Dunifi.write=guess.gui.units unifi.drivers.Analyze lib/guess.jar > log.guess.gui 2>&1

echo "\nApplying Java.lang golden units to GUESS"
java -Xss16M -Xms1G -Dunifi.read=../java-jars/java.lang.units -Dunifi.write=guess.lang.units unifi.drivers.Analyze lib/guess.jar > log.guess.lang 2>&1
