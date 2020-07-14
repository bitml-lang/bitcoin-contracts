package Tests_DecentralizedEscrow

import akka.actor.{ActorRef, ActorSystem, Address, CoordinatedShutdown, Props}
import fr.acinq.bitcoin.Crypto.PublicKey
import fr.acinq.bitcoin._
import scodec.bits.ByteVector
import xyz.bitml.api.ChunkPrivacy.ChunkPrivacy
import xyz.bitml.api.ChunkType.ChunkType
import xyz.bitml.api.persistence._
import xyz.bitml.api._
import akka.util.Timeout
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import xyz.bitml.api.messaging.AssembledTx

import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable, Future}
import scala.collection.mutable

case class ClientManager() {
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
