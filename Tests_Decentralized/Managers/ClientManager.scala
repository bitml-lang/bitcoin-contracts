package Managers

import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown, Props}
import akka.util.Timeout
import akka.util
import scala.concurrent.duration._
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import xyz.bitml.api.Client
import xyz.bitml.api.messaging.AssembledTx

import scala.concurrent.{Await, Future}

case class ClientManager() {
  def isPublished(res: String): Boolean = {
    try {
      rpc.getTransaction(res)
      true
    } catch {
      case x : Exception => {
        false
      }
    }
  }

  val rpc = new BitcoinJSONRPCClient()

  def getResult(future : Future[Any], timeoutMillis : Int = 2000) : String = {
    val timeout = Timeout(timeoutMillis milliseconds)
    Await.result(future, timeout.duration).asInstanceOf[AssembledTx].serializedTx
  }

  def createActor(system : ActorSystem, name : String) : ActorRef = {
    system.actorOf(Props(classOf[Client]), name = name)
  }

  def shutSystem(system: ActorSystem) : Unit = {
    CoordinatedShutdown(system).run(CoordinatedShutdown.unknownReason)
  }

}
