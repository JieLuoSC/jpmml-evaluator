/*
 * Copyright (c) 2014 Villu Ruusmann
 *
 * This file is part of JPMML-Evaluator
 *
 * JPMML-Evaluator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Evaluator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Evaluator.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.evaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.FieldName;

public class CustomAssociationSchemaTest extends AssociationSchemaTest {

	@Override
	public Map<FieldName, ?> createItemArguments(List<String> items){
		Map<FieldName, String> result = new HashMap<>();

		String[] fields = {"Banana", "Coke", "Cracker", "Nachos", "Pear", "Water"};
		for(String field : fields){
			result.put(FieldName.create(field), items.contains(field) ? "T" : "F");
		}

		return result;
	}
}