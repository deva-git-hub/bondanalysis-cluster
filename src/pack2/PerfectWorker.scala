package pack2



import org.apache.log4j.Logger
import parascale.actor.last.{Task, Worker}
import parascale.util._

import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

/**
 * Spawns workers on the localhost.
 */
object PerfectWorker extends App {
  case class Result(sum: Long, t0: Long, t1: Long) extends Serializable
  val LOG = Logger.getLogger(getClass)

  LOG.info("started")

  // Number of hosts in this configuration
  val nhosts = getPropertyOrElse("nhosts", 1)

  // One-port configuration
  val port1 = getPropertyOrElse("port", 8000)

  // If there is just one host, then the ports will include 9000 by default
  // Otherwise, if there are two hosts in this configuration, use just one
  // port which must be specified by VM options
  val ports = if(nhosts == 1) List(port1, 9000) else List(port1)

  // Spawn the worker(s).
  // Note: for initial testing with a single host, "ports" contains two ports.
  // When deploying on two hosts, "ports" will contain one port per host.
  for(port <- ports) {
    // Construction forks a thread which automatically runs the actor act method.
    new PerfectWorker(port)
  }
}

/**
 * Template worker for finding a perfect number.
 * @param port Localhost port this worker listens to
 */
class PerfectWorker(port: Int) extends Worker(port) {
  import PerfectWorker._

  /**
   * Handles actor startup after construction.
   */


  override def act(): Unit = {
    val name = getClass.getSimpleName
    LOG.info("started " + name + " (id=" + id + ")")

    // Wait for inbound messages as tasks.
    while (true) {
      receive match {
        // TODO: Replace the code below to implement PNF.
        // It gets the partition range info from the task payload then
        // spawns futures (or uses parallel collections) to analyze the
        // partition in parallel. Finally, when done, it replies
        // with the partial sum and the total estimated serial time for
        // this worker.
        case task: Task =>
          val currPartitionTimerStart = System.currentTimeMillis(); // start the timer
          LOG.info("got task = " + task.payload + " sending reply")

          val partition = task.payload.asInstanceOf[pack2.PerfectDispatcher.Partition]

          val chunksSize = 1000000
          // divide current partition into chunks
          val chunks = partition.start to partition.end by chunksSize map {
            currStart => {
              val currEnd = (currStart + chunksSize - 1) min partition.end
              currStart to currEnd
            }
          }


          val partials = chunks.par.map { chunk =>
            val t0 = System.currentTimeMillis() // start the inner clock

            val candidateFactors = for {
              num <- chunk
              rem = partition.candidate % num
            } yield {
              if (rem == 0) num else 0
            }

            val partialSum = candidateFactors.sum

            val t1 = System.currentTimeMillis() // end the inner clock
            (partialSum, t1 - t0)
          }

          val workerSum =  partials.map(_._1).sum // total sum of factors on current worker.
          val partialSerialTime = partials.map(_._2).sum // total sum of serial time on current worker.

          sleep(250)
          sender ! Result(workerSum, currPartitionTimerStart,  currPartitionTimerStart + partialSerialTime)
      }
    }
  }
}
