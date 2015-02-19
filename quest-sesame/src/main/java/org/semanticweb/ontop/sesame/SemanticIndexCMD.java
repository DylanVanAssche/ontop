package org.semanticweb.ontop.sesame;

/*
 * #%L
 * ontop-quest-sesame
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.util.Properties;

import org.semanticweb.ontop.injection.OBDAProperties;
import org.semanticweb.ontop.io.QueryIOManager;
import org.semanticweb.ontop.model.OBDADataFactory;
import org.semanticweb.ontop.model.OBDADataSource;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.model.impl.RDBMSourceParameterConstants;
import org.semanticweb.ontop.owlrefplatform.core.QuestConstants;
import org.semanticweb.ontop.owlrefplatform.core.QuestPreferences;
import org.semanticweb.ontop.owlrefplatform.owlapi3.QuestOWL;
import org.semanticweb.ontop.owlrefplatform.owlapi3.QuestOWLConnection;
import org.semanticweb.ontop.owlrefplatform.owlapi3.QuestOWLFactory;
import org.semanticweb.ontop.owlrefplatform.owlapi3.QuestOWLResultSet;
import org.semanticweb.ontop.owlrefplatform.owlapi3.QuestOWLStatement;
import org.semanticweb.ontop.querymanager.QueryController;
import org.semanticweb.ontop.querymanager.QueryControllerEntity;
import org.semanticweb.ontop.querymanager.QueryControllerQuery;
import org.semanticweb.ontop.sql.JDBCConnectionManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/***
 * Tests if QuestOWL can be initialized on top of an existing semantic index
 * created by the SemanticIndexManager.
 * 
 * @author mariano
 * 
 */
public class SemanticIndexCMD {

	String driver = "com.mysql.jdbc.Driver";
	String url = "jdbc:mysql://localhost/lubmex2050?sessionVariables=sql_mode='ANSI'";
	String username = "root";
	String password = "";

	String owlfile = "src/test/resources/test/lubm-ex-20-uni1/merge.owl";

	OBDADataFactory fac = OBDADataFactoryImpl.getInstance();
	private OWLOntology ontology;
	private OWLOntologyManager manager;
	private OBDADataSource source;
	
	Logger log = LoggerFactory.getLogger(this.getClass());

	public SemanticIndexCMD(String configFile) throws Exception {
		
		
		
		manager = OWLManager.createOWLOntologyManager();
		ontology = manager.loadOntologyFromOntologyDocument(new File(owlfile));

		source = fac.getDataSource(URI.create("http://www.obda.org/ABOXDUMP1testx1"));
		source.setParameter(RDBMSourceParameterConstants.DATABASE_DRIVER, driver);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_PASSWORD, password);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_URL, url);
		source.setParameter(RDBMSourceParameterConstants.DATABASE_USERNAME, username);
		source.setParameter(RDBMSourceParameterConstants.IS_IN_MEMORY, "false");
		source.setParameter(RDBMSourceParameterConstants.USE_DATASOURCE_FOR_ABOXDUMP, "true");

	}

	public void setupRepo() throws Exception {
		log.info("Creating a new repository");
		Connection conn = null;
		try {
			conn = JDBCConnectionManager.getJDBCConnectionManager().createConnection(source);

			SemanticIndexManager simanager = new SemanticIndexManager(ontology, conn);

			simanager.setupRepository(true);

		} catch (Exception e) {
			throw e;
		} finally {
			if (conn != null)
				conn.close();
		}
		log.info("Done.");
	}

	public void inserData() throws Exception {
		log.debug("Inserting data");
		Connection conn = null;
		
		ontology = manager.loadOntologyFromOntologyDocument(new File(owlfile));
		
		try {
			conn = JDBCConnectionManager.getJDBCConnectionManager().createConnection(source);

			SemanticIndexManager simanager = new SemanticIndexManager(ontology, conn);

			simanager.restoreRepository();

			int inserts = simanager.insertData(ontology, 20000, 5000);
			
			simanager.updateMetadata();

			log.info("Done. Inserted {} triples.", inserts);
			
			
//			assertEquals(30033, inserts);
		} catch (Exception e) {
			throw e;
		} finally {
			if (conn != null)
				conn.close();
		}
		
		

	}

	public void test3InitializingQuest() throws Exception {
		Properties p = new Properties();
        p.setProperty(QuestPreferences.DBTYPE, QuestConstants.SEMANTIC_INDEX);
        p.setProperty(QuestPreferences.ABOX_MODE, QuestConstants.CLASSIC);
        p.setProperty(QuestPreferences.STORAGE_LOCATION, QuestConstants.JDBC);
        p.setProperty(QuestPreferences.OBTAIN_FROM_ONTOLOGY, "false");
        p.setProperty(OBDAProperties.JDBC_DRIVER, driver);
        p.setProperty(OBDAProperties.JDBC_URL, url);
        p.setProperty(OBDAProperties.DB_USER, username);
        p.setProperty(OBDAProperties.DB_PASSWORD, password);
		QuestPreferences pref = new QuestPreferences(p);

		QuestOWLFactory fac = new QuestOWLFactory(pref);

		QuestOWL quest = fac.createReasoner(ontology);

		QuestOWLConnection qconn = quest.getConnection();

		QuestOWLStatement st = qconn.createStatement();

		QueryController qc = new QueryController();
		QueryIOManager qman = new QueryIOManager(qc);
		qman.load("src/test/resources/test/treewitness/LUBM-ex-20.q");

		for (QueryControllerEntity e : qc.getElements()) {
			if (!(e instanceof QueryControllerQuery)) {
				continue;
			}
			QueryControllerQuery query = (QueryControllerQuery) e;
			log.debug("Executing query: {}", query.getID() );
			log.debug("Query: \n{}", query.getQuery());
			
			long start = System.nanoTime();
			QuestOWLResultSet res = st.executeTuple(query.getQuery());
			long end = System.nanoTime();
			
			double time = (end - start) / 1000; 
			
			int count = 0;
			while (res.nextRow()) {
				count += 1;
			}
			log.debug("Total result: {}", count );
			log.debug("Elapsed time: {} ms", time);
		}
	}

}
