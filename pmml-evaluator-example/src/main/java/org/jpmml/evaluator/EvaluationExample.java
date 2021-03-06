/*
 * Copyright (c) 2013 Villu Ruusmann
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

import java.io.Console;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.validators.PositiveInteger;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.evaluator.visitors.ExpressionOptimizer;
import org.jpmml.evaluator.visitors.FieldOptimizer;
import org.jpmml.evaluator.visitors.GeneralRegressionModelOptimizer;
import org.jpmml.evaluator.visitors.NaiveBayesModelOptimizer;
import org.jpmml.evaluator.visitors.PredicateOptimizer;
import org.jpmml.evaluator.visitors.RegressionModelOptimizer;

public class EvaluationExample extends Example {

	@Parameter (
		names = {"--model"},
		description = "Model PMML file",
		required = true
	)
	@ParameterOrder (
		value = 1
	)
	private File model = null;

	@Parameter (
		names = {"--input"},
		description = "Input CSV file",
		required = true
	)
	@ParameterOrder (
		value = 2
	)
	private File input = null;

	@Parameter (
		names = {"--output"},
		description = "Output CSV file",
		required = true
	)
	@ParameterOrder (
		value = 3
	)
	private File output = null;

	@Parameter (
		names = {"--separator"},
		description = "CSV cell separator character"
	)
	@ParameterOrder (
		value = 4
	)
	private String separator = null;

	@Parameter (
		names = {"--copy-columns"},
		description = "Copy all columns from input CSV file to output CSV file",
		arity = 1
	)
	@ParameterOrder (
		value = 5
	)
	private boolean copyColumns = true;

	@Parameter (
		names = {"--sparse"},
		description = "Permit missing active field columns",
		hidden = true
	)
	private boolean sparse = false;

	@Parameter (
		names = {"--wait-before"},
		description = "Pause before starting the work",
		hidden = true
	)
	private boolean waitBefore = false;

	@Parameter (
		names = {"--wait-after"},
		description = "Pause after completing the work",
		hidden = true
	)
	private boolean waitAfter = false;

	@Parameter (
		names = "--loop",
		description = "The number of repetitions",
		hidden = true,
		validateWith = PositiveInteger.class
	)
	private int loop = 1;

	@Parameter (
		names = "--cache-builder-spec",
		description = "CacheBuilder configuration",
		hidden = true
	)
	private String cacheBuilderSpec = null;

	@Parameter (
		names = "--optimize",
		description = "Optimize PMML class model",
		hidden = true
	)
	private boolean optimize = false;


	static
	public void main(String... args) throws Exception {
		execute(EvaluationExample.class, args);
	}

	@Override
	public void execute() throws Exception {
		MetricRegistry metricRegistry = new MetricRegistry();

		ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry)
			.convertRatesTo(TimeUnit.SECONDS)
			.convertDurationsTo(TimeUnit.MILLISECONDS)
			.build();

		CsvUtil.Table inputTable = readTable(this.input, this.separator);

		List<? extends Map<FieldName, ?>> inputRecords = BatchUtil.parseRecords(inputTable, Example.CSV_PARSER);

		if(this.waitBefore){
			waitForUserInput();
		}

		PMML pmml = readPMML(this.model);

		if(this.cacheBuilderSpec != null){
			CacheBuilderSpec cacheBuilderSpec = CacheBuilderSpec.parse(this.cacheBuilderSpec);

			CacheUtil.setCacheBuilderSpec(cacheBuilderSpec);
		} // End if

		if(this.optimize){
			List<? extends Visitor> optimizers = Arrays.asList(new ExpressionOptimizer(), new FieldOptimizer(), new PredicateOptimizer(), new GeneralRegressionModelOptimizer(), new NaiveBayesModelOptimizer(), new RegressionModelOptimizer());

			for(Visitor optimizer : optimizers){
				optimizer.applyTo(pmml);
			}
		}

		ModelEvaluatorFactory modelEvaluatorFactory = ModelEvaluatorFactory.newInstance();

		Evaluator evaluator = modelEvaluatorFactory.newModelManager(pmml);

		// Perform self-testing
		evaluator.verify();

		List<FieldName> activeFields = evaluator.getActiveFields();
		List<FieldName> groupFields = evaluator.getGroupFields();

		if(inputRecords.size() > 0){
			Map<FieldName, ?> inputRecord = inputRecords.get(0);

			Sets.SetView<FieldName> missingActiveFields = Sets.difference(new LinkedHashSet<>(activeFields), inputRecord.keySet());
			if((missingActiveFields.size() > 0) && !this.sparse){
				throw new IllegalArgumentException("Missing active field(s): " + missingActiveFields.toString());
			}

			Sets.SetView<FieldName> missingGroupFields = Sets.difference(new LinkedHashSet<>(groupFields), inputRecord.keySet());
			if(missingGroupFields.size() > 0){
				throw new IllegalArgumentException("Missing group field(s): " + missingGroupFields.toString());
			}
		} // End if

		if(groupFields.size() == 1){
			FieldName groupField = groupFields.get(0);

			inputRecords = EvaluatorUtil.groupRows(groupField, inputRecords);
		} else

		if(groupFields.size() > 1){
			throw new EvaluationException();
		}

		List<Map<FieldName, ?>> outputRecords = new ArrayList<>(inputRecords.size());

		Timer timer = new Timer(new SlidingWindowReservoir(this.loop));

		metricRegistry.register("main", timer);

		int epoch = 0;

		do {
			Timer.Context context = timer.time();

			try {
				outputRecords.clear();

				Map<FieldName, FieldValue> arguments = new LinkedHashMap<>();

				for(Map<FieldName, ?> inputRecord : inputRecords){
					arguments.clear();

					for(FieldName activeField : activeFields){
						FieldValue activeValue = EvaluatorUtil.prepare(evaluator, activeField, inputRecord.get(activeField));

						arguments.put(activeField, activeValue);
					}

					Map<FieldName, ?> result = evaluator.evaluate(arguments);

					outputRecords.add(result);
				}
			} finally {
				context.close();
			}

			epoch++;
		} while(epoch < this.loop);

		if(this.waitAfter){
			waitForUserInput();
		}

		List<FieldName> targetFields = EvaluatorUtil.getTargetFields(evaluator);
		List<FieldName> outputFields = EvaluatorUtil.getOutputFields(evaluator);

		CsvUtil.Table outputTable = new CsvUtil.Table();
		outputTable.setSeparator(inputTable.getSeparator());

		List<FieldName> resultFields = Lists.newArrayList(Iterables.concat(targetFields, outputFields));

		outputTable.addAll(BatchUtil.formatRecords(outputRecords, resultFields, Example.CSV_FORMATTER));

		if((inputTable.size() == outputTable.size()) && this.copyColumns){

			for(int i = 0; i < inputTable.size(); i++){
				List<String> inputRow = inputTable.get(i);
				List<String> outputRow = outputTable.get(i);

				outputRow.addAll(0, inputRow);
			}
		}

		writeTable(outputTable, this.output);

		if(this.loop > 1){
			reporter.report();
		}

		reporter.close();
	}

	static
	private void waitForUserInput(){
		Console console = System.console();
		if(console == null){
			throw new IllegalStateException();
		}

		console.readLine("Press ENTER to continue");
	}
}