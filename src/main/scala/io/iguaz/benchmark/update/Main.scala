package io.iguaz.benchmark.update

import java.nio.file.Paths
import java.util.Properties

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

import io.iguaz.v3io.container.DoInContainer
import io.iguaz.v3io.kv.{KeyValueOperations, OverwriteMode, SimpleRow, UpdateEntry}

object Main {

  def main(args: Array[String]): Unit = {

    val collection = Paths.get(args(0))

    val printPeriod = sys.props.get("print-period").map(_.toInt).getOrElse(10000)

    val parallelMassUpdates = sys.props.get("parallel-mass-updates").map(_.toInt).getOrElse(1)
    val maxInFlightPropName = "v3io.config.kv.update.max-in-flight"
    val maxInFlight = sys.props.get(maxInFlightPropName).map(_.toInt)

    println(s"parallelMassUpdates = $parallelMassUpdates")
    println(s"collection = $collection")
    println(s"printPeriod = $printPeriod")

    val start = System.currentTimeMillis()

    def requestIterator() = {
      var count = 0
      Iterator.from(0).map { i =>
        if (count % printPeriod == 0) {
          val secondPassed = (System.currentTimeMillis() - start) / 1000
          println(s"$count entries written after $secondPassed seconds...")
        }
        count += 1
        val row = SimpleRow(i.toString, Map("index" -> i.toLong))
        UpdateEntry(collection, row, OverwriteMode.REPLACE)
      }
    }

    val props = new Properties
    props.put("container-id", "1")

    val responses = DoInContainer(props) { container =>
      val kvOps = KeyValueOperations(container, Map.empty ++ maxInFlight.map(maxInFlightPropName -> _))
      val responseFutureList = List.fill(parallelMassUpdates)(kvOps.update(requestIterator()))
      val responsesFuture = Future.sequence(responseFutureList)

      Await.result(responsesFuture, Duration.Inf)
    }
    println(s"responses = $responses")
  }
}