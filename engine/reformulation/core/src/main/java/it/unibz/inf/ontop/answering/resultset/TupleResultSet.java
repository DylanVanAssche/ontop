package it.unibz.inf.ontop.answering.resultset;

/*
 * #%L
 * ontop-obdalib-core
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

import it.unibz.inf.ontop.exception.OntopConnectionException;
import it.unibz.inf.ontop.exception.OntopResultConversionException;

import java.util.List;

public interface TupleResultSet extends IterativeOBDAResultSet<OntopBindingSet, OntopResultConversionException> {

	/*
	 * ResultSet management functions
	 */
	int getColumnCount();

	List<String> getSignature() throws OntopConnectionException;

	boolean isConnectionAlive() throws OntopConnectionException;
}
