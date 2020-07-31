package Tests_Decentralized

import Managers.ClientManager
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
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, Authorize, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Bob  extends AnyFunSuite with BeforeAndAfterAll  {
  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemBob")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Bob") {
    // get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // creating akka actor
    val contract = cm.createActor(testSystem, "Bob")

    // private and public key
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))

    // fetch the partecipant db from the initial state
    val bob_p = initialState.partdb.fetch(b_pub.toString()).get

    // Initialize Alice with the state information.
    contract ! Init(b_priv, stateJson)

    // Start network interface.
    contract ! Listen("test_application_b.conf", bob_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout: Timeout = Timeout(2000 milliseconds)

    if(false) {
      contract ! Authorize("T1_alice")
    }

    var published = false
    var tries = 0
    
    // Bob should keep trying to publish the transaction until he receives Oracle's signature
    while (!published && tries < 5) {
      // Ask for signature
      contract ! AskForSigs("T1_bob")
      // Try to assemble the transaction and publish it
      var future = contract ? TryAssemble("T1_bob", autoPublish = true)
      var res = cm.getResult(future)
      published = cm.isPublished(res)

      contract ! AskForSigs("T1_C_bob")
      future = contract ? TryAssemble("T1_C_bob", autoPublish = true)
      res = cm.getResult(future)
      published = cm.isPublished(res)

      contract ! AskForSigs("T1_C_split_bob")
      contract ? TryAssemble("T1_C_split_bob", autoPublish = true)
      res = cm.getResult(future)
      published = cm.isPublished(res)

      tries+=1
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }

}
