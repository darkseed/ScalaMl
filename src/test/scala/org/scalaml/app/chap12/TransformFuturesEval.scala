/**
 * Copyright 2013, 2014, 2015  by Patrick Nicolas - Scala for Machine Learning - All rights reserved
 *
 * The source code in this file is provided by the author for the sole purpose of illustrating the 
 * concepts and algorithms presented in "Scala for Machine Learning" ISBN: 978-1-783355-874-2 Packt Publishing.
 * Unless required by applicable law or agreed to in writing, software is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * Version 0.97
 */
package org.scalaml.app.chap12

import org.apache.log4j.Logger	
import akka.pattern.ask
import akka.actor.{ActorSystem, Props}
import akka.util.Timeout

import org.scalaml.app.{Eval, TestContext}
import org.scalaml.core.XTSeries
import org.scalaml.core.XTSeries.DblSeries
import org.scalaml.core.Types.ScalaMl
import org.scalaml.scalability.akka.Partitioner
import org.scalaml.scalability.akka.message._
import org.scalaml.scalability.akka.TransformFutures
import org.scalaml.app.TestContext
import org.scalaml.filtering.DFT
import org.scalaml.util.Display


		/**
		 * <p>Specialized Akka futures for the distributed discrete Fourier transform.</p>
		 * @constructor Create a set of futures for the distributed discrete Fourier transform. [xt] time series to be processed. [partitioner] Partitioning methodology for distributing time series across a cluster of worker actors.
		 * @throws IllegalArgumentException if the time series or the partitioner are not defined.
		 * 
		 * @author Patrick Nicolas
		 * @since June 5, 2014
		 * @note Scala for Machine Learning Chapter 12 Scalable frameworks/Akka
		 */
final class DFTTransformFutures(xt: DblSeries, partitioner: Partitioner)(implicit timeout: Timeout) 
				extends TransformFutures(xt, DFT[Double], partitioner)  {
	private val SPECTRUM_WIDTH = 64
	private val logger = Logger.getLogger("DFTTransformFutures")
		/**
		 * <p>Method to aggregate (reducer) the results for the discrete Fourier transform on each worker.</p>
		 * @param data array of values (vector of Double) generated by each worker actor
		 * @return Sequence of frequencies 
		 */
	override protected def aggregate(data: Array[DblSeries]): Seq[Double] = {
		require(data != null && data.size > 0, "DFTTransformFutures.aggregate Output of one of the workers undefined")
		
		val results = data.map(_.toArray).transpose.map(_.sum).take(SPECTRUM_WIDTH)
		Display.show(s"Index  Frequencies\n${ScalaMl.toString(results, "", true)}", logger)
		results.toSeq
	}
}



		/**
		 * <p><b>Purpose</b>: Singleton to evaluate Scala/Akka futures</p>
		 * 
		 * @author Patrick Nicolas 
		 * @note Scala for Machine Learning Chapter 12 Scalable frameworks / Akka framework / futures
		 */
object TransformFuturesEval extends Eval {
	import java.util.concurrent.TimeoutException
	import scala.util.{Random, Try, Success, Failure}
	import scala.concurrent.Await
	import scala.concurrent.duration.Duration
  
		/**
		 * Name of the evaluation 
		 */
	val name: String = "TransformFuturesEval"
	/**
		 * Maximum duration allowed for the execution of the evaluation
		 */
	val maxExecutionTime: Int = 10000
	private val logger = Logger.getLogger(name)
		
	private val NUM_WORKERS = 8
	private val NUM_DATA_POINTS = 1000000
	private val h = (x:Double) =>	2.0*Math.cos(Math.PI*0.005*x) +		// simulated first harmonic
									Math.cos(Math.PI*0.05*x) +			// simulated second harmonic
									0.5*Math.cos(Math.PI*0.2*x)  +		// simulated third harmonic 
									0.2*Random.nextDouble

	private val duration = Duration(8000, "millis")
	implicit val timeout = new Timeout(duration)

		 /** <p>Execution of the scalatest for futures design with Akka framework.
		 * This method is invoked by the  actor-based test framework function, ScalaMlTest.evaluate</p>
		 * @param args array of arguments used in the test
		 * @return -1 in case error a positive or null value if the test succeeds. 
		 */
	def run(args: Array[String]): Int = {
		Display.show(s"\n\n *****  test#${Eval.testCount} $name Data transformation futures using Akka actors", logger)
		
		val xt = XTSeries[Double](Array.tabulate(NUM_DATA_POINTS)(h(_)))
		val partitioner = new Partitioner(NUM_WORKERS)
  
		val master = TestContext.actorSystem.actorOf(Props(new DFTTransformFutures(xt, partitioner)), "DFTTransform")
		Try {
			val future = master ? Start(0)
			Await.result(future, timeout.duration)
		} 
		match {
			case Success(result) => Display.show("TransformFuturesEval completed", logger)
			case Failure(e) => e match {
				case ex: TimeoutException => Display.show(s"TransformFuturesEval.run timeout", logger)
				case ex: Throwable => Display.error("TransformFuturesEval.run completed", logger, e)
			}
		}
		TestContext.shutdown
	}
}

// -----------------------------------------------  EOF ---------------------------