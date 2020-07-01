package Tests_Decentralized

import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, TxEntry}
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Bob  extends AnyFunSuite with BeforeAndAfterAll  {
  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem2")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Bob") {
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)
    val bob = testSystem.actorOf(Props(classOf[Client]), name="Bob")

    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))

    val bob_p = initialState.partdb.fetch("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7").get

    // Initialize Alice with the state information.
    bob ! Init(b_priv, stateJson)

    // Start network interface.
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout: Timeout = Timeout(2000 milliseconds)

    //bob tries to assemble T1 and is going to ask the signature
    val rpc = new BitcoinJSONRPCClient()
    var notReceived = true
    while (notReceived) {
      bob ! AskForSigs("T1")
      Thread.sleep(500)
      try {
        val future3 = bob ? TryAssemble("T1", autoPublish = true)
        val res2 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
        val tx = rpc.getRawTransaction(res2)
        println("Publish Success! "+tx)
        notReceived = false
      } catch {
        case x : Exception => {
          println("Publish Failed... Retrying")
          notReceived = true
        }
      }
      Thread.sleep(2000)
    }




    // final partecipant shutdown
    bob ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

}
