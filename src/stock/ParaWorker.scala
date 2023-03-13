package stock

import parabond.cluster.{Node, Partition}
import parabond.util.Result
import parascale.actor.last.{Task, Worker}
import parascale.util._
object ParaWorker extends App{
  //a. If worker running on a single host, spawn two workers // else spawn one worker.
  val nhosts = getPropertyOrElse("nhosts", 1)

  // One-port configuration
  val port1 = getPropertyOrElse("port", 8000)
  // If there is 1 host, then ports include 9000 by default // Otherwise, if there are two hosts in this configuration,
  // use just one port which must be specified by VM options
  val ports =  if (nhosts == 1) List(port1, 9000) else List(port1)

  for (port <- ports) {
    // Start a new worker.
    new ParaWorker(port)
  }
}

class ParaWorker(port: Int) extends Worker(port) {
  override def act(): Unit = {

    while (true){
      receive match {
        case task: Task =>
          val partition = task.payload.asInstanceOf[Partition]
          val node = Node.getInstance(partition)
          assert(node != null, "failed to construct node")
          // collect the results
          val results = node.analyze().results
          // sum of every result t1 - t0
          val workerTime = results.map(job => job.result.t1 - job.result.t0).sum

          sleep(500)

          sender.send(Result(workerTime))
      }
    }
  }
}
