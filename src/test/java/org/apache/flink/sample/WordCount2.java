package org.apache.flink.sample;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.FlatMapFunction;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.Utils;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

/**
 * Implements the "WordCount" program that computes a simple word occurrence histogram
 * over some sample data
 *
 * <p>
 * This example shows how to:
 * <ul>
 * <li>write a simple Flink program.
 * <li>use Tuple data types.
 * <li>write and use user-defined functions.
 * </ul>
 *
 */
public class WordCount2 {

	//
	//	Program
	//
	
	static class IntHolder
	{
		int value = 0;
		
		synchronized void add(int v)
		{
			value += v;
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		ExecutionEnvironment env = createEnv();
		
		test(env, 0);
		//test(100);
		System.out.println("Start main at " + new Date());
		long begin = System.currentTimeMillis();
		int n = 5;
		final CountDownLatch latch = new CountDownLatch(n);
		final IntHolder holder = new IntHolder();
		
		
		/*
		for(int i=0;i<n;i++)
		{
			final int index = i+1;
			new Thread(new Runnable(){

				@Override
				public void run() {
					try {

						holder.value += test(createEnv(), index);
						latch.countDown();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
		*/
		
		//latch.await(30, TimeUnit.SECONDS);
		
		for(int i=0;i<n;i++)
		{
			final int index = i+1;
			test(env, index);
		}
		
		JobExecutionResult result = env.execute();
		for(int i=0;i<n+1;i++)
		{
			String id = "id" + i;
			long count = result.getAccumulatorResult(id);
			System.out.println("Result " + id + ":" + count);
		}
		int cost = (int)(System.currentTimeMillis()-begin);
		System.out.println("End main  at " + new Date() + ", cost " + cost + "ms.");
		System.out.println("Cost sum " + holder.value + ", avg " + (1.0 * cost / n));
		System.exit(0);
	}

	
	public static void main2(String[] args) throws Exception {
		
		ExecutionEnvironment env = createEnv2();
		
		test2(env, 0);
		//test(100);
		System.out.println("Start main at " + new Date());
		long begin = System.currentTimeMillis();
		int n = 10000;
		final CountDownLatch latch = new CountDownLatch(n);
		final IntHolder holder = new IntHolder();
		
		ExecutorService pool = Executors.newFixedThreadPool(50);
		
		for(int i=0;i<n;i++)
		{
			final int index = i+1;
			pool.execute(new Runnable(){

				@Override
				public void run() {
					try {

						holder.add(test2(createEnv2(), index));
						latch.countDown();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		
		
		latch.await(30, TimeUnit.SECONDS);
		
		
		int cost = (int)(System.currentTimeMillis()-begin);
		System.out.println("End main  at " + new Date() + ", cost " + cost + "ms.");
		System.out.println("Cost sum " + holder.value + ", avg " + (1.0 * holder.value / n));
		System.exit(0);
	}

	
	private static ExecutionEnvironment createEnv2()
	{
			return ExecutionEnvironment.createCollectionsEnvironment();
	}
	private static ExecutionEnvironment createEnv()
	{
		if(true)
			return ExecutionEnvironment.createCollectionsEnvironment();
		
		Configuration config = new Configuration();
		//config.setBoolean(ConfigConstants.TASK_MANAGER_MEMORY_OFF_HEAP_KEY, true);
		config.setString(ConfigConstants.AKKA_ASK_TIMEOUT, "6000 s");
		final ExecutionEnvironment env = ExecutionEnvironment.createLocalEnvironment(config);
		env.setParallelism(8);
		ExecutionEnvironment.setDefaultLocalParallelism(8);
		
		return env;
	}
	
	public static String test(ExecutionEnvironment env, int index) throws Exception {

		
		System.out.println("Start test " + index + " at " + new Date());
		long begin = System.currentTimeMillis();
		// set up the execution environment


		// get input data
		DataSet<String> text = env.fromElements(
				"To be, or not to be,--that is the question:--",
				"Whether 'tis nobler in the mind to suffer",
				"The slings and arrows of outrageous fortune",
				"Or to take arms against a sea of troubles,"
				);

		DataSet<Tuple2<String, Integer>> counts =
				// split up the lines in pairs (2-tuples) containing: (word,1)
				text.flatMap(new LineSplitter())
				// group by the tuple field "0" and sum up tuple field "1"
				.groupBy(0)
				.sum(1);

		// execute and print result
		//counts.print();
		
		final String id = "id" + index;

		counts.output(new Utils.CountHelper<Tuple2<String, Integer>>(id)).name("count()" + id);
		env.execute();
		int cost = (int)(System.currentTimeMillis()-begin);
		System.out.println("End test " + index + " at " + new Date() + ", cost " + cost + "ms.");
		
		return id;
	}

	
	public static int test2(ExecutionEnvironment env, int index) throws Exception {

		
		System.out.println("Start test " + index + " at " + new Date());
		long begin = System.currentTimeMillis();
		// set up the execution environment


		// get input data
		DataSet<String> text = env.fromElements(
				"To be, or not to be,--that is the question:--",
				"Whether 'tis nobler in the mind to suffer",
				"The slings and arrows of outrageous fortune",
				"Or to take arms against a sea of troubles,"
				);

		DataSet<Tuple2<String, Integer>> counts =
				// split up the lines in pairs (2-tuples) containing: (word,1)
				text.flatMap(new LineSplitter())
				// group by the tuple field "0" and sum up tuple field "1"
				.groupBy(0)
				.sum(1);

		// execute and print result
		counts.print();
		
		//final String id = "id" + index;

		//counts.output(new Utils.CountHelper<Tuple2<String, Integer>>(id)).name("count()");
		
		int cost = (int)(System.currentTimeMillis()-begin);
		System.out.println("End test " + index + " at " + new Date() + ", cost " + cost + "ms.");
		
		return cost;
	}
	//
	// 	User Functions
	//

	/**
	 * Implements the string tokenizer that splits sentences into words as a user-defined
	 * FlatMapFunction. The function takes a line (String) and splits it into
	 * multiple pairs in the form of "(word,1)" (Tuple2<String, Integer>).
	 */
	public static final class LineSplitter implements FlatMapFunction<String, Tuple2<String, Integer>> {

		@Override
		public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
			// normalize and split the line
			String[] tokens = value.toLowerCase().split("\\W+");

			// emit the pairs
			for (String token : tokens) {
				if (token.length() > 0) {
					out.collect(new Tuple2<String, Integer>(token, 1));
				}
			}
		}
	}
}
