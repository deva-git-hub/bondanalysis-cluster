package pack2

import pack2.PerfectWorker.Result
import org.apache.log4j.Logger
import parascale.actor.last.{Dispatcher, Task}
import parascale.future.perfect.candidates
import parascale.util._

/**
  * Spawns a dispatcher to connect to multiple workers.
  * @author Ron.Coleman
  */
object PerfectDispatcher extends App {
  case class Partition(start: Long, end: Long, candidate: Long) extends Serializable

  val LOG = Logger.getLogger(getClass)
  LOG.info("started")


  // For initial testing on a single host, use this socket.
  // When deploying on multiple hosts, use the VM argument,
  // -Dsocket=<ip address>:9000 which points to the second
  // host.
  val socket2 = getPropertyOrElse("socket","localhost:9000")

  // Construction forks a thread which automatically runs the actor act method.
  new PerfectDispatcher(List("localhost:8000", socket2))
}

/**
  * Template dispatcher which tests readiness of the host(s)
  * @param sockets List of sockets
  * @author Ron.Coleman
  */

class PerfectDispatcher(sockets: List[String]) extends Dispatcher(sockets) {
  import PerfectDispatcher._



  /**
    * Handles actor startup after construction.
    */
  def act: Unit = {
    LOG.info("sockets to workers = "+sockets)

    var Info = "";
    Info += "PNF Using Parallel Collections\n"
    Info += "By <student-name>\n"
    Info += "<date>\n"
    Info += "Cores : 16\n"
    // get the worker addresses
    val addresses = workers.map(_.forwardAddr)
    Info += "Hosts: " + addresses.mkString(", ") + "\n"
    Info +=  "%-15s %-10s %10s %10s %10s %10s\n".format("Candidate", "Perfect", "T1(s)", "TN(s)", "R", "e")

    print(Info)

    val candidatesSubset = candidates.slice(0,11); //upto 137438691328

    candidatesSubset.foreach(
      cand => {
        val p1 = PerfectDispatcher.Partition(1, cand / 2, cand)
        val p2 = PerfectDispatcher.Partition(cand / 2 + 1, cand - 1, cand)

        // dispatch job to two workers
        workers(0) ! p1
        workers(1) ! p2

      val TN_0 = System.currentTimeMillis() // start the parallel timer
      val replies = (0 until sockets.length).map { k =>
        receive match {
          case task: Task if task.kind == Task.REPLY =>
            LOG.info("received reply from worker " + k)
            task.payload.asInstanceOf[Result]
        }
      }

        var factorsSum = 0L
        var T1 = 0.toDouble
        for {
          result <- replies
          currSum = result.sum
          currT1 = result.t1 - result.t0
        } {
          factorsSum += currSum
          T1 += currT1
        }

      var TN : Double = System.currentTimeMillis() - TN_0
      val R  = T1 / TN
        T1 = T1 / 1000L
        TN = TN / 1000L // convert milliseconds to seconds
      val N = 16 //number of cores of two hosts in cluster
      val e = R / N
      val perfect = if (factorsSum == cand) "YES" else "NO"

      println("%-15d %-10s %10.2f %10.2f %10.2f %10.2f".format(cand, perfect, T1, TN, R, e))
    }
    )

    // TODO: Replace the code below to implement PNF
    // Create the partition info and put it in two seaparate messages,
    // one for each worker, then wait below for two replies, one from
    // eah worker

  }
}

