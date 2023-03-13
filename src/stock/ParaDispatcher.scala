package stock

import parabond.cluster.{Partition, check, checkReset, nanoSecondsToSeconds}
import parabond.util.{MongoHelper, Result}
import parascale.actor.last.Dispatcher
import parascale.util._

object ParaDispatcher extends  App{
  // For initial testing on a single host, use this socket.
  // When deploying on multiple hosts, use the VM argument,
  // -Dsocket=<ip address>:9000 which points to the second
  val socket2 = getPropertyOrElse("socket", "localhost:9000")
  // This spawns a list of relay workers at the sockets
  new ParaDispatcher(List("localhost:8000", socket2))
}



class ParaDispatcher(sockets: List[String]) extends Dispatcher(sockets) {

 var reportAcc = ""
 reportAcc += s"Parabond Analysis\nBy <student-name>\n<date>\nFineGrainNode\n"
 reportAcc += s"Workers: ${this.sockets.length}\n"
 val workerAddresses = sockets.map(socket => socket.split(":")(0))
 reportAcc += f"Hosts: localhost (dispatcher), ${workerAddresses(0)}%10s (worker), ${workerAddresses(1)}%10s (worker), "
 val mongoHost = MongoHelper.getHost
 reportAcc += f"${mongoHost}%10s (mongo)\n"
 reportAcc += s"Cores: ${Runtime.getRuntime.availableProcessors()}\n"
 reportAcc += "%10s %10s %10s %10s %10s %10s\n".format("N", "missed", "T1", "TN", "R", "e")

 def printReport(rung : Int, missed  : Int, T1_S : Double, TN_S : Double, R : Double, e : Double ) =  {
  reportAcc +=  "%10d %10d %10.2f %10.2f %10.2f %10.2f\n".format(rung, missed, T1_S, TN_S, R, e)
  println(reportAcc)
 }


 override def act : Unit = {
   val ladder = List(1000,
      2000,
      4000,
      8000,
      16000,
      32000,
      64000,
      100000)

    ladder.foreach(rung =>{
      val portIds = checkReset(rung)
     val p1 = Partition(rung / 2, 1)
     val p2 = Partition(rung / 2, rung / 2)

      // dispatch the tasks to two workers
      workers(0) ! p1
      workers(1) ! p2

      val TN_0 = System.nanoTime()
     /*val replies = (0 until workers.length).map { k =>
        receive match {
          case task: Task if task.kind == Task.REPLY =>
            task.payload.asInstanceOf[Result]
        }
      }*/

      val repliesRaw = for ( _ <- 0 until workers.length) yield receive
      val replies = repliesRaw.map(_.payload.asInstanceOf[Result])
     // val T1 = replies.map(reply=> (reply.t1 - reply.t0)).sum
      val T1 = replies.foldLeft(0L) { (acc, reply) => acc + (reply.t1 - reply.t0) }
      val T1_S = nanoSecondsToSeconds(T1).seconds
      val TN_S = nanoSecondsToSeconds(System.nanoTime() - TN_0).seconds
      val missed = check(portIds).length
      val R : Double = (T1_S / TN_S)
      val N = 8
      val e = R / N

     printReport(rung, missed, T1_S, TN_S, R, e)


    })

  }

}