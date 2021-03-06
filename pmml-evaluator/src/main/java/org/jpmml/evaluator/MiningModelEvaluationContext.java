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
import java.util.Map;

import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.OutputField;

public class MiningModelEvaluationContext extends ModelEvaluationContext {

	private Map<String, SegmentResult> results = new HashMap<>();

	private Map<FieldName, OutputField> outputFields = null;


	MiningModelEvaluationContext(MiningModelEvaluator modelEvaluator){
		this(null, modelEvaluator);
	}

	MiningModelEvaluationContext(MiningModelEvaluationContext parent, MiningModelEvaluator modelEvaluator){
		super(parent, modelEvaluator);
	}

	@Override
	void reset(){
		super.reset();

		this.results.clear();

		if(this.outputFields != null){
			this.outputFields.clear();
		}
	}

	@Override
	public MiningModelEvaluator getModelEvaluator(){
		return (MiningModelEvaluator)super.getModelEvaluator();
	}

	SegmentResult getResult(String id){
		return this.results.get(id);
	}

	void putResult(String id, SegmentResult result){
		this.results.put(id, result);
	}

	OutputField getOutputField(FieldName name){

		if(this.outputFields == null){
			return null;
		}

		return this.outputFields.get(name);
	}

	void putOutputField(OutputField outputField){
		putOutputField(outputField.getName(), outputField);
	}

	void putOutputField(FieldName name, OutputField outputField){

		if(this.outputFields == null){
			this.outputFields = new HashMap<>();
		}

		this.outputFields.put(name, outputField);
	}

	DerivedField getLocalDerivedField(FieldName name){
		ModelEvaluator<?> modelEvaluator = getModelEvaluator();

		return modelEvaluator.getLocalDerivedField(name);
	}
}